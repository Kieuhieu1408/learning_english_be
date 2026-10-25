package com.learning.english.service;

import com.learning.english.dto.AiSettingsDto;
import com.learning.english.dto.ChatDtos.*;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ChatService {

    @Autowired
    private ConversationRepository conversationRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AiSettingsService aiSettingsService;

    @org.springframework.beans.factory.annotation.Value("${app.gemini.api-key:}")
    private String defaultGeminiKey;

    @org.springframework.beans.factory.annotation.Value("${app.openai.api-key:}")
    private String defaultOpenAiKey;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    private UUID getCurrentUserId() {
        return getCurrentUserPrincipal().getId();
    }

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<ConversationDto> getConversations() {
        UUID userId = getCurrentUserId();
        return conversationRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream()
                .map(this::toConversationDto)
                .toList();
    }

    @Transactional
    public ConversationDto createOrUpsertConversation(CreateConversationRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        Instant now = Instant.now();

        if (request.id() != null) {
            Optional<Conversation> existingOpt = conversationRepository.findByIdAndUserId(request.id(), userId);
            if (existingOpt.isPresent()) {
                Conversation existing = existingOpt.get();
                existing.setTitle(request.title().trim());
                if (request.noteId() != null) {
                    existing.setNoteId(request.noteId());
                }
                existing.setDeletedAt(null);
                existing.setUpdatedAt(now);
                return toConversationDto(conversationRepository.save(existing));
            }
        }

        Conversation conversation = Conversation.builder()
                .id(request.id() != null ? request.id() : UuidV7Generator.generate())
                .user(user)
                .noteId(request.noteId())
                .title(request.title().trim())
                .pinned(false)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return toConversationDto(conversationRepository.save(conversation));
    }

    @Transactional(readOnly = true)
    public List<ChatMessageDto> getMessages(UUID conversationId) {
        UUID userId = getCurrentUserId();
        Conversation conv = conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));

        return chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conv.getId()).stream()
                .map(this::toMessageDto)
                .toList();
    }

    @Transactional
    public ConversationDto renameConversation(UUID conversationId, UpdateConversationRequest request) {
        UUID userId = getCurrentUserId();
        Conversation conv = conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));

        conv.setTitle(request.title().trim());
        conv.setUpdatedAt(Instant.now());
        return toConversationDto(conversationRepository.save(conv));
    }

    @Transactional
    public void deleteConversation(UUID conversationId) {
        UUID userId = getCurrentUserId();
        Conversation conv = conversationRepository.findByIdAndUserIdAndDeletedAtIsNull(conversationId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found or access denied"));

        Instant now = Instant.now();
        conv.setDeletedAt(now);
        conv.setUpdatedAt(now);
        conversationRepository.save(conv);
    }

    @Transactional
    public SseEmitter sendMessageAndStream(ChatSendRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        Instant now = Instant.now();

        UUID convId = request.conversationId() != null ? request.conversationId() : UuidV7Generator.generate();
        Conversation conversation = conversationRepository.findByIdAndUserId(convId, userId)
                .orElseGet(() -> {
                    String autoTitle = request.message().length() > 60
                            ? request.message().substring(0, 60) + "..."
                            : request.message();
                    Conversation c = Conversation.builder()
                            .id(convId)
                            .user(user)
                            .noteId(request.noteId())
                            .title(autoTitle)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return conversationRepository.save(c);
                });

        if (conversation.getDeletedAt() != null) {
            conversation.setDeletedAt(null);
        }
        if (request.noteId() != null && conversation.getNoteId() == null) {
            conversation.setNoteId(request.noteId());
        }
        conversation.setUpdatedAt(now);
        conversationRepository.save(conversation);

        // 1. Save User Message
        ChatMessage userMessage = ChatMessage.builder()
                .id(UuidV7Generator.generate())
                .conversation(conversation)
                .role("user")
                .content(request.message().trim())
                .createdAt(now)
                .build();
        chatMessageRepository.save(userMessage);

        // 2. Load context from Note and Words for AI reply generation
        UUID effectiveNoteId = request.noteId() != null ? request.noteId() : conversation.getNoteId();
        Note contextNote = effectiveNoteId != null
                ? noteRepository.findByIdAndUserIdAndDeletedAtIsNull(effectiveNoteId, userId).orElse(null)
                : null;

        List<UserWord> contextWords = (request.wordIds() != null && !request.wordIds().isEmpty())
                ? userWordRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(request.wordIds(), userId)
                : List.of();

        List<ChatMessage> history = chatMessageRepository.findAllByConversationIdOrderByCreatedAtAsc(conversation.getId());

        AiSettingsDto aiSettings = aiSettingsService.getSettings(userId);
        String provider = aiSettings.provider() != null ? aiSettings.provider().trim().toUpperCase(Locale.ROOT) : "9ROUTER";
        String chatModel = aiSettings.chatModel() != null && !aiSettings.chatModel().isBlank()
                ? aiSettings.chatModel().trim()
                : (provider.contains("GEMINI") ? "gemini-1.5-flash" : "deepseek-chat");
        String customBaseUrl = aiSettings.customBaseUrl() != null && !aiSettings.customBaseUrl().isBlank()
                ? aiSettings.customBaseUrl().trim()
                : "http://localhost:20128/v1";
        String apiKey = aiSettingsService.getDecryptedApiKey(userId);

        if (apiKey == null || apiKey.isBlank()) {
            if (provider.contains("GEMINI") || provider.contains("GOOGLE")) {
                apiKey = defaultGeminiKey;
            } else if (provider.contains("OPENAI") || provider.contains("DEEPSEEK")) {
                apiKey = defaultOpenAiKey;
            }
        }

        UUID assistantMessageId = UuidV7Generator.generate();

        // 3. Persist Initial Assistant Message
        ChatMessage assistantMessage = ChatMessage.builder()
                .id(assistantMessageId)
                .conversation(conversation)
                .role("assistant")
                .content("")
                .model(chatModel)
                .tokensUsed(0)
                .createdAt(Instant.now())
                .build();
        chatMessageRepository.save(assistantMessage);

        // 4. Stream SSE events
        SseEmitter emitter = new SseEmitter(120_000L);
        try {
            Map<String, String> metaPayload = Map.of(
                    "conversationId", conversation.getId().toString(),
                    "messageId", assistantMessageId.toString()
            );
            emitter.send(SseEmitter.event()
                    .name("meta")
                    .data(metaPayload, MediaType.APPLICATION_JSON));
        } catch (IOException e) {
            emitter.completeWithError(e);
            return emitter;
        }

        final String effectiveApiKey = apiKey;
        final String effectiveModel = chatModel;
        final String effectiveProvider = provider;
        final String effectiveBaseUrl = customBaseUrl;

        Thread.startVirtualThread(() -> {
            boolean streamedSuccess = false;
            StringBuilder fullReplyBuilder = new StringBuilder();
            String errorMessage = null;

            if ((effectiveApiKey == null || effectiveApiKey.isBlank()) && !"9ROUTER".equalsIgnoreCase(effectiveProvider)) {
                errorMessage = "Chưa cấu hình API key cho " + effectiveProvider + ". Vui lòng vào Cài đặt AI để nhập API key thật.";
            } else {
                try {
                    String chatEndpoint;
                    if (effectiveProvider.contains("GEMINI") || effectiveProvider.contains("GOOGLE") || effectiveBaseUrl.contains("generativelanguage.googleapis.com")) {
                        chatEndpoint = "https://generativelanguage.googleapis.com/v1beta/openai/chat/completions";
                    } else if (effectiveProvider.contains("DEEPSEEK") && (effectiveBaseUrl.contains("localhost") || effectiveBaseUrl.contains("127.0.0.1"))) {
                        chatEndpoint = "https://api.deepseek.com/chat/completions";
                    } else if (effectiveProvider.contains("OPENAI") && (effectiveBaseUrl.contains("localhost") || effectiveBaseUrl.contains("127.0.0.1"))) {
                        chatEndpoint = "https://api.openai.com/v1/chat/completions";
                    } else {
                        chatEndpoint = effectiveBaseUrl.endsWith("/")
                                ? effectiveBaseUrl + "chat/completions"
                                : effectiveBaseUrl + "/chat/completions";
                    }

                    List<Map<String, String>> messagesList = new ArrayList<>();
                    StringBuilder systemPrompt = new StringBuilder("You are an English language learning assistant tutor.");
                    if (contextNote != null) {
                        systemPrompt.append(" Context article: \"").append(contextNote.getTitle()).append("\".");
                    }
                    if (!contextWords.isEmpty()) {
                        String wordsContext = contextWords.stream()
                                .map(w -> w.getText() + (w.getPrimaryMeaning() != null ? " (" + w.getPrimaryMeaning() + ")" : ""))
                                .collect(Collectors.joining(", "));
                        systemPrompt.append(" Focus vocabulary: [").append(wordsContext).append("].");
                    }
                    messagesList.add(Map.of("role", "system", "content", systemPrompt.toString()));

                    int historyStart = Math.max(0, history.size() - 8);
                    for (int i = historyStart; i < history.size(); i++) {
                        ChatMessage hm = history.get(i);
                        if (hm.getContent() != null && !hm.getContent().isBlank()) {
                            messagesList.add(Map.of("role", hm.getRole(), "content", hm.getContent()));
                        }
                    }
                    messagesList.add(Map.of("role", "user", "content", request.message().trim()));

                    Map<String, Object> requestPayload = Map.of(
                            "model", effectiveModel,
                            "stream", true,
                            "messages", messagesList
                    );
                    String requestJson = objectMapper.writeValueAsString(requestPayload);

                    java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                            .connectTimeout(java.time.Duration.ofMillis(5000))
                            .build();

                    java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(chatEndpoint))
                            .timeout(java.time.Duration.ofSeconds(60))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(requestJson));

                    if (effectiveApiKey != null && !effectiveApiKey.isBlank()) {
                        reqBuilder.header("Authorization", "Bearer " + effectiveApiKey);
                    } else {
                        reqBuilder.header("Authorization", "Bearer 9router-token");
                    }

                    java.net.http.HttpResponse<java.util.stream.Stream<String>> response = httpClient.send(
                            reqBuilder.build(),
                            java.net.http.HttpResponse.BodyHandlers.ofLines()
                    );

                    if (response.statusCode() >= 200 && response.statusCode() < 300) {
                        try (java.util.stream.Stream<String> lines = response.body()) {
                            lines.forEach(line -> {
                                String trimmed = line.trim();
                                if (trimmed.startsWith("data:")) {
                                    String payload = trimmed.substring(5).trim();
                                    if ("[DONE]".equals(payload)) {
                                        return;
                                    }
                                    try {
                                        com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(payload);
                                        com.fasterxml.jackson.databind.JsonNode choices = rootNode.get("choices");
                                        if (choices != null && choices.isArray() && !choices.isEmpty()) {
                                            com.fasterxml.jackson.databind.JsonNode delta = choices.get(0).get("delta");
                                            if (delta != null && delta.has("content")) {
                                                String content = delta.get("content").asText();
                                                if (content != null && !content.isEmpty()) {
                                                    fullReplyBuilder.append(content);
                                                    emitter.send(SseEmitter.event()
                                                            .name("chunk")
                                                            .data(Map.of("text", content), MediaType.APPLICATION_JSON));
                                                }
                                            }
                                        }
                                    } catch (Exception ignored) {
                                    }
                                }
                            });
                        }
                        if (!fullReplyBuilder.isEmpty()) {
                            streamedSuccess = true;
                        }
                    } else {
                        errorMessage = "AI Provider Error (HTTP " + response.statusCode() + "). Vui lòng kiểm tra API key và hạn ngạch tài khoản.";
                    }
                } catch (Exception e) {
                    String detail = e.getMessage();
                    if (detail == null || detail.isBlank() || e instanceof java.net.ConnectException) {
                        if (effectiveBaseUrl.contains("localhost") || effectiveBaseUrl.contains("127.0.0.1")) {
                            errorMessage = "Không thể kết nối tới cổng 9Router cục bộ (" + effectiveBaseUrl + "). Vui lòng mở AI Settings (góc dưới bên trái) để chọn nhà cung cấp AI (Gemini / DeepSeek / OpenAI) và nhập API key.";
                        } else {
                            errorMessage = "Không thể kết nối tới máy chủ AI (" + effectiveProvider + "). Vui lòng kiểm tra API key và kết nối mạng.";
                        }
                    } else {
                        errorMessage = "Lỗi kết nối AI: " + detail + ". Vui lòng kiểm tra API key và kết nối mạng.";
                    }
                }
            }

            if (!streamedSuccess && errorMessage != null) {
                fullReplyBuilder.setLength(0);
                fullReplyBuilder.append(errorMessage);
                try {
                    emitter.send(SseEmitter.event()
                            .name("chunk")
                            .data(Map.of("text", errorMessage), MediaType.APPLICATION_JSON));
                } catch (Exception ignored) {
                }
            }

            String finalAssistantContent = fullReplyBuilder.toString();
            try {
                emitter.send(SseEmitter.event()
                        .name("done")
                        .data("[DONE]", MediaType.TEXT_PLAIN));
                emitter.complete();
            } catch (Exception e) {
                emitter.completeWithError(e);
            }

            try {
                chatMessageRepository.findById(assistantMessageId).ifPresent(msg -> {
                    msg.setContent(finalAssistantContent);
                    msg.setTokensUsed(Math.max(1, finalAssistantContent.length() / 4));
                    chatMessageRepository.save(msg);
                });
            } catch (Exception ignored) {
            }
        });

        return emitter;
    }


    private ConversationDto toConversationDto(Conversation c) {
        return ConversationDto.builder()
                .id(c.getId())
                .title(c.getTitle())
                .noteId(c.getNoteId())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }

    private ChatMessageDto toMessageDto(ChatMessage m) {
        return ChatMessageDto.builder()
                .id(m.getId())
                .role(m.getRole())
                .content(m.getContent())
                .createdAt(m.getCreatedAt())
                .build();
    }
}
