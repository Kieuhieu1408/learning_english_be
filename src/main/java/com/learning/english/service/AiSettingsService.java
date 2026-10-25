package com.learning.english.service;

import com.learning.english.dto.AiConnectionTestResponse;
import com.learning.english.dto.AiSettingsDto;
import com.learning.english.model.User;
import com.learning.english.model.UserAiSettings;
import com.learning.english.repository.UserAiSettingsRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.util.EncryptionUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class AiSettingsService {

    private static final List<String> DEFAULT_ALLOWED_BASE_URLS = List.of(
            "http://localhost:20128",
            "http://127.0.0.1:20128",
            "https://api.deepseek.com",
            "https://generativelanguage.googleapis.com",
            "https://api.openai.com"
    );

    @Autowired
    private UserAiSettingsRepository userAiSettingsRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EncryptionUtil encryptionUtil;

    @Value("${app.encryption.secret-key}")
    private String secretKey;

    @Value("${app.ai.allowed-base-urls:http://localhost:20128/v1,https://api.deepseek.com,https://generativelanguage.googleapis.com,https://api.openai.com}")
    private List<String> configuredAllowedUrls;

    @Transactional(readOnly = true)
    public AiSettingsDto getSettings(UUID userId) {
        return userAiSettingsRepository.findByUserId(userId)
                .map(this::toSafeDto)
                .orElseGet(() -> AiSettingsDto.builder()
                        .provider("9ROUTER")
                        .customBaseUrl("http://localhost:20128/v1")
                        .hasApiKey(false)
                        .translationModel("deepseek-chat")
                        .quizModel("deepseek-chat")
                        .chatModel("deepseek-chat")
                        .build());
    }

    @Transactional
    public AiSettingsDto saveSettings(UUID userId, AiSettingsDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (dto.customBaseUrl() != null && !dto.customBaseUrl().isBlank()) {
            validateBaseUrlAllowList(dto.customBaseUrl());
        }

        UserAiSettings settings = userAiSettingsRepository.findByUserId(userId)
                .orElseGet(() -> UserAiSettings.builder()
                        .user(user)
                        .build());

        if (dto.provider() != null && !dto.provider().isBlank()) {
            settings.setProvider(dto.provider().trim().toUpperCase(Locale.ROOT));
        }
        if (dto.customBaseUrl() != null && !dto.customBaseUrl().isBlank()) {
            settings.setCustomBaseUrl(dto.customBaseUrl().trim());
        } else if (settings.getCustomBaseUrl() == null || settings.getCustomBaseUrl().isBlank()) {
            settings.setCustomBaseUrl("http://localhost:20128/v1");
        }

        // Only overwrite encrypted key if a non-blank key was supplied
        if (dto.apiKey() != null && !dto.apiKey().isBlank()) {
            settings.setEncryptedApiKey(encryptionUtil.encrypt(dto.apiKey().trim(), secretKey));
        }

        if (dto.translationModel() != null && !dto.translationModel().isBlank()) {
            settings.setTranslationModel(dto.translationModel().trim());
        }
        if (dto.quizModel() != null && !dto.quizModel().isBlank()) {
            settings.setQuizModel(dto.quizModel().trim());
        }
        if (dto.chatModel() != null && !dto.chatModel().isBlank()) {
            settings.setChatModel(dto.chatModel().trim());
        }

        UserAiSettings saved = userAiSettingsRepository.save(settings);
        return toSafeDto(saved);
    }

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    @Transactional(readOnly = true)
    public AiConnectionTestResponse testConnection(UUID userId) {
        return testConnection(userId, null);
    }

    @Transactional(readOnly = true)
    public AiConnectionTestResponse testConnection(UUID userId, AiSettingsDto overrideDto) {
        AiSettingsDto current = getSettings(userId);
        String provider = overrideDto != null && overrideDto.provider() != null && !overrideDto.provider().isBlank()
                ? overrideDto.provider().trim().toUpperCase(Locale.ROOT)
                : (current.provider() != null ? current.provider() : "9ROUTER");

        String customBaseUrl = overrideDto != null && overrideDto.customBaseUrl() != null && !overrideDto.customBaseUrl().isBlank()
                ? overrideDto.customBaseUrl().trim()
                : (current.customBaseUrl() != null ? current.customBaseUrl() : "http://localhost:20128/v1");

        validateBaseUrlAllowList(customBaseUrl);

        String apiKey = overrideDto != null && overrideDto.apiKey() != null && !overrideDto.apiKey().isBlank()
                ? overrideDto.apiKey().trim()
                : getDecryptedApiKey(userId);

        String endpoint;
        boolean isGemini = provider.contains("GEMINI") || provider.contains("GOOGLE") || customBaseUrl.contains("generativelanguage.googleapis.com");
        if (isGemini) {
            endpoint = "https://generativelanguage.googleapis.com/v1beta/models" + (apiKey != null && !apiKey.isBlank() ? "?key=" + apiKey : "");
        } else {
            endpoint = customBaseUrl.endsWith("/") ? customBaseUrl + "models" : customBaseUrl + "/models";
        }

        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofMillis(5000))
                    .build();

            java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(endpoint))
                    .timeout(java.time.Duration.ofMillis(6000))
                    .GET();

            if (!isGemini) {
                if (apiKey != null && !apiKey.isBlank()) {
                    reqBuilder.header("Authorization", "Bearer " + apiKey);
                } else if ("9ROUTER".equalsIgnoreCase(provider)) {
                    reqBuilder.header("Authorization", "Bearer 9router-token");
                }
            }

            java.net.http.HttpResponse<String> response = client.send(reqBuilder.build(), java.net.http.HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                java.util.List<AiConnectionTestResponse.ModelOptionDto> fetchedModels = new java.util.ArrayList<>();
                try {
                    com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(response.body());
                    com.fasterxml.jackson.databind.JsonNode dataNode = rootNode.get("data");
                    com.fasterxml.jackson.databind.JsonNode modelsNode = rootNode.get("models");

                    if (dataNode != null && dataNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode item : dataNode) {
                            String id = item.hasNonNull("id") ? item.get("id").asText() : "";
                            String name = id;
                            if (item.hasNonNull("name") && !item.get("name").asText().isBlank() && !"null".equalsIgnoreCase(item.get("name").asText())) {
                                name = item.get("name").asText();
                            }
                            if (!id.isBlank()) {
                                fetchedModels.add(AiConnectionTestResponse.ModelOptionDto.builder()
                                        .id(id)
                                        .name(name)
                                        .build());
                            }
                        }
                    } else if (modelsNode != null && modelsNode.isArray()) {
                        for (com.fasterxml.jackson.databind.JsonNode item : modelsNode) {
                            String name = item.hasNonNull("name") ? item.get("name").asText() : "";
                            String id = name.replace("models/", "");
                            String displayName = item.hasNonNull("displayName") ? item.get("displayName").asText() : id;
                            if (!id.isBlank() && (id.contains("gemini") || id.contains("flash") || id.contains("pro"))) {
                                fetchedModels.add(AiConnectionTestResponse.ModelOptionDto.builder()
                                        .id(id)
                                        .name(displayName)
                                        .build());
                            }
                        }
                    }
                } catch (Exception ignored) {
                }

                if (fetchedModels.isEmpty()) {
                    fetchedModels = getDefaultModels();
                }

                return AiConnectionTestResponse.builder()
                        .connected(true)
                        .message("Connected successfully to " + provider + " gateway")
                        .models(fetchedModels)
                        .build();

            } else {
                String safeEndpoint = isGemini ? "https://generativelanguage.googleapis.com/v1beta/models" : customBaseUrl;
                return AiConnectionTestResponse.builder()
                        .connected(false)
                        .message("Gateway returned HTTP " + response.statusCode() + " from " + safeEndpoint)
                        .models(getDefaultModels())
                        .build();
            }
        } catch (Exception e) {
            return AiConnectionTestResponse.builder()
                    .connected(false)
                    .message("Connection failed to " + provider + " gateway. Please check your network and gateway settings.")
                    .models(getDefaultModels())
                    .build();
        }
    }

    private java.util.List<AiConnectionTestResponse.ModelOptionDto> getDefaultModels() {
        return java.util.List.of(
                AiConnectionTestResponse.ModelOptionDto.builder()
                        .id("deepseek-chat")
                        .name("DeepSeek V3")
                        .build(),
                AiConnectionTestResponse.ModelOptionDto.builder()
                        .id("gemini-2.5-flash")
                        .name("Gemini 2.5 Flash")
                        .build(),
                AiConnectionTestResponse.ModelOptionDto.builder()
                        .id("gpt-4o-mini")
                        .name("GPT-4o Mini")
                        .build()
        );
    }

    public void validateBaseUrlAllowList(String customBaseUrl) {
        if (customBaseUrl == null || customBaseUrl.isBlank()) {
            throw new IllegalArgumentException("customBaseUrl cannot be null or empty");
        }

        URI uri;
        try {
            uri = URI.create(customBaseUrl.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid customBaseUrl URI syntax: " + e.getMessage());
        }

        String scheme = uri.getScheme();
        if (scheme == null || (!scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https"))) {
            throw new IllegalArgumentException("Only HTTP and HTTPS protocols are allowed in customBaseUrl");
        }

        // Prevent User-Info credential parser confusion (e.g. https://api.openai.com@attacker.com)
        if (uri.getUserInfo() != null) {
            throw new IllegalArgumentException("User-info credentials are not permitted in customBaseUrl");
        }

        String targetHost = uri.getHost();
        if (targetHost == null || targetHost.isBlank()) {
            throw new IllegalArgumentException("Host cannot be empty in customBaseUrl");
        }
        targetHost = targetHost.toLowerCase(Locale.ROOT);
        int targetPort = uri.getPort();

        boolean allowed = isAllowedUrl(scheme.toLowerCase(Locale.ROOT), targetHost, targetPort);
        if (!allowed) {
            throw new IllegalArgumentException("customBaseUrl is not allowed by SSRF security policy: " + customBaseUrl);
        }
    }

    private boolean isAllowedUrl(String targetScheme, String targetHost, int targetPort) {
        List<String> allAllowed = new ArrayList<>(DEFAULT_ALLOWED_BASE_URLS);
        if (configuredAllowedUrls != null) {
            allAllowed.addAll(configuredAllowedUrls);
        }

        for (String allowedStr : allAllowed) {
            if (allowedStr == null || allowedStr.isBlank()) {
                continue;
            }
            try {
                URI allowedUri = URI.create(allowedStr.trim());
                String allowedScheme = allowedUri.getScheme();
                String allowedHost = allowedUri.getHost();
                int allowedPort = allowedUri.getPort();

                if (allowedHost == null) {
                    continue;
                }

                if (allowedScheme != null && !allowedScheme.equalsIgnoreCase(targetScheme)) {
                    continue;
                }

                if (!allowedHost.equalsIgnoreCase(targetHost)) {
                    continue;
                }

                // If allowedUri specified a port, target must match that port
                if (allowedPort != -1) {
                    if (targetPort != allowedPort) {
                        continue;
                    }
                } else {
                    // Default ports: target port must be -1 or standard default port for scheme
                    if (targetPort != -1) {
                        int defaultPort = "https".equalsIgnoreCase(targetScheme) ? 443 : 80;
                        if (targetPort != defaultPort) {
                            continue;
                        }
                    }
                }

                return true;
            } catch (Exception ignored) {
            }
        }
        return false;
    }

    public String getDecryptedApiKey(UUID userId) {
        return userAiSettingsRepository.findByUserId(userId)
                .map(UserAiSettings::getEncryptedApiKey)
                .filter(k -> k != null && !k.isBlank())
                .map(k -> {
                    try {
                        return encryptionUtil.decrypt(k, secretKey);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElse(null);
    }

    private AiSettingsDto toSafeDto(UserAiSettings settings) {
        String decrypted = null;
        if (settings.getEncryptedApiKey() != null && !settings.getEncryptedApiKey().isBlank()) {
            try {
                decrypted = encryptionUtil.decrypt(settings.getEncryptedApiKey(), secretKey);
            } catch (Exception ignored) {
            }
        }
        boolean hasKey = decrypted != null && !decrypted.isBlank();
        String hint = hasKey ? "••••••••••••" : null;

        return AiSettingsDto.builder()
                .provider(settings.getProvider() != null ? settings.getProvider() : "9ROUTER")
                .customBaseUrl(settings.getCustomBaseUrl() != null ? settings.getCustomBaseUrl() : "http://localhost:20128/v1")
                .hasApiKey(hasKey)
                .apiKeyHint(hint)
                .translationModel(settings.getTranslationModel() != null ? settings.getTranslationModel() : "deepseek-chat")
                .quizModel(settings.getQuizModel() != null ? settings.getQuizModel() : "deepseek-chat")
                .chatModel(settings.getChatModel() != null ? settings.getChatModel() : "deepseek-chat")
                .build();
    }
}
