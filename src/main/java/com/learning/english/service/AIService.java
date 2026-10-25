package com.learning.english.service;

import com.learning.english.client.AIClient;
import com.learning.english.client.adapter.GeminiAdapter;
import com.learning.english.client.adapter.OpenAIAdapter;
import com.learning.english.client.dto.AIResult;
import com.learning.english.dto.AiSettingsDto;
import com.learning.english.dto.TranslateContextRequest;
import com.learning.english.dto.TranslateContextResponse;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class AIService {

    private final AIClient activeAIClient;
    private final GeminiAdapter geminiAdapter;
    private final OpenAIAdapter openaiAdapter;
    private final String defaultAiProvider;

    @Autowired(required = false)
    private AiSettingsService aiSettingsService;

    public AIService(
            @Value("${app.ai.provider:gemini}") String aiProvider,
            @Autowired @Qualifier("openaiAdapter") OpenAIAdapter openaiAdapter,
            @Autowired @Qualifier("geminiAdapter") GeminiAdapter geminiAdapter) {
        this.defaultAiProvider = aiProvider != null ? aiProvider.trim().toLowerCase(Locale.ROOT) : "gemini";
        this.openaiAdapter = openaiAdapter;
        this.geminiAdapter = geminiAdapter;

        if ("openai".equalsIgnoreCase(this.defaultAiProvider) || "deepseek".equalsIgnoreCase(this.defaultAiProvider)) {
            this.activeAIClient = openaiAdapter;
        } else {
            this.activeAIClient = geminiAdapter;
        }
    }

    private UUID getOptionalUserId() {
        try {
            if (SecurityContextUtils.CURRENT_USER.isBound()) {
                return SecurityContextUtils.CURRENT_USER.get().getId();
            }
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
                return userPrincipal.getId();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public List<AIResult> translateWordsInParallel(List<String> words) throws Exception {
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<CompletableFuture<AIResult>> futures = words.stream()
                    .map(word -> CompletableFuture.supplyAsync(() -> {
                        try {
                            return activeAIClient.translateAndDefine(word);
                        } catch (Exception e) {
                            throw new RuntimeException("AI Translation failed for word: " + word, e);
                        }
                    }, executor))
                    .toList();

            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<AIResult> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.join());
            }
            return results;
        }
    }

    public TranslateContextResponse translateContext(TranslateContextRequest request) {
        String rawText = request.text() != null ? request.text().trim() : "";
        String inputSentence = request.sentence() != null && !request.sentence().isBlank()
                ? request.sentence().trim()
                : null;

        AIResult aiResult = null;
        UUID userId = getOptionalUserId();

        if (userId != null && aiSettingsService != null) {
            try {
                AiSettingsDto userSettings = aiSettingsService.getSettings(userId);
                String userApiKey = aiSettingsService.getDecryptedApiKey(userId);
                String provider = userSettings.provider() != null
                        ? userSettings.provider().trim().toUpperCase(Locale.ROOT)
                        : this.defaultAiProvider.toUpperCase(Locale.ROOT);
                String model = userSettings.translationModel();
                String customBaseUrl = userSettings.customBaseUrl();

                if (userApiKey != null && !userApiKey.isBlank()) {
                    if (provider.contains("GEMINI") || provider.contains("GOOGLE")) {
                        aiResult = geminiAdapter.translateAndDefineWithContext(rawText, inputSentence, userApiKey, model);
                    } else {
                        aiResult = openaiAdapter.translateAndDefineWithContext(rawText, inputSentence, userApiKey, model, customBaseUrl);
                    }
                } else if ("9ROUTER".equalsIgnoreCase(provider)) {
                    aiResult = openaiAdapter.translateAndDefineWithContext(rawText, inputSentence, null, model, customBaseUrl);
                }
            } catch (Exception e) {
                // If user-specific AI call failed, log and fallback to active default AI client
                System.err.println("Translation failed using user AI settings: " + e.getMessage() + ". Falling back to active default AI client.");
            }
        }

        if (aiResult == null) {
            aiResult = activeAIClient.translateAndDefineWithContext(rawText, inputSentence);
        }

        if (aiResult == null) {
            throw new IllegalStateException("AI provider returned empty translation for word: " + rawText);
        }

        return TranslateContextResponse.builder()
                .text(aiResult.text() != null && !aiResult.text().isBlank() ? aiResult.text() : rawText)
                .ipa(aiResult.ipa() != null ? aiResult.ipa() : "")
                .partOfSpeech(aiResult.partOfSpeech() != null ? aiResult.partOfSpeech().toLowerCase(Locale.ROOT) : "noun")
                .meaning(aiResult.translation() != null ? aiResult.translation() : "")
                .sentence(aiResult.exampleContext() != null && !aiResult.exampleContext().isBlank() ? aiResult.exampleContext() : (inputSentence != null ? inputSentence : ""))
                .sentenceTranslation(aiResult.exampleTranslation() != null ? aiResult.exampleTranslation() : "")
                .build();
    }

    public String classifyNoteTopic(String content, List<String> existingTopics) {
        return activeAIClient.classifyTopic(content, existingTopics);
    }

    public double evaluateEssay(String topicText, String essayText) {
        return activeAIClient.evaluateEssay(topicText, essayText);
    }
}
