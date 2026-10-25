package com.learning.english.client.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.english.client.AIClient;
import com.learning.english.client.dto.AIResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("openaiAdapter")
public class OpenAIAdapter implements AIClient {

    private final String defaultApiUrl;
    private final String defaultApiKey;
    private final RestClient defaultRestClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAIAdapter(
            @Value("${app.openai.api-url:http://localhost:20128/v1}") String apiUrl,
            @Value("${app.openai.api-key:}") String apiKey) {
        this.defaultApiUrl = apiUrl != null ? apiUrl.trim() : "http://localhost:20128/v1";
        this.defaultApiKey = apiKey != null ? apiKey.trim() : "";
        
        RestClient.Builder builder = RestClient.builder().baseUrl(this.defaultApiUrl);
        if (!this.defaultApiKey.isBlank()) {
            builder.defaultHeader("Authorization", "Bearer " + this.defaultApiKey);
        } else {
            builder.defaultHeader("Authorization", "Bearer 9router-token");
        }
        this.defaultRestClient = builder.build();
    }

    @Override
    public AIResult translateAndDefine(String text) {
        return translateAndDefineWithContext(text, null);
    }

    @Override
    public AIResult translateAndDefineWithContext(String text, String sentence) {
        return translateAndDefineWithContext(text, sentence, null, null, null);
    }

    public AIResult translateAndDefineWithContext(String text, String sentence, String overrideApiKey, String overrideModel, String overrideBaseUrl) {
        String key = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey.trim() : this.defaultApiKey;
        String model = (overrideModel != null && !overrideModel.isBlank()) ? overrideModel.trim() : "deepseek-chat";
        String baseUrl = (overrideBaseUrl != null && !overrideBaseUrl.isBlank()) ? overrideBaseUrl.trim() : this.defaultApiUrl;

        RestClient client;
        if ((overrideBaseUrl != null && !overrideBaseUrl.isBlank()) || (overrideApiKey != null && !overrideApiKey.isBlank())) {
            String cleanBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            if (!cleanBase.endsWith("/v1") && !cleanBase.contains("/v1/")) {
                cleanBase += "/v1";
            }
            RestClient.Builder builder = RestClient.builder().baseUrl(cleanBase);
            if (key != null && !key.isBlank()) {
                builder.defaultHeader("Authorization", "Bearer " + key);
            } else {
                builder.defaultHeader("Authorization", "Bearer 9router-token");
            }
            client = builder.build();
        } else {
            client = this.defaultRestClient;
        }

        try {
            String sentenceContext = (sentence != null && !sentence.isBlank())
                    ? String.format(" Context sentence: \"%s\".", sentence.trim())
                    : "";

            String userPrompt = String.format(
                    "Word/phrase: '%s'.%s Translate to Vietnamese and provide lexical details in JSON format.",
                    text, sentenceContext
            );

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("stream", false);
            requestBody.put("temperature", 0.3);
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content",
                            "You are an English-to-Vietnamese lexicographer. Reply ONLY with a valid JSON object with fields: " +
                            "text (the English word), translation (Vietnamese meaning in this context), partOfSpeech (Noun/Verb/Adjective/...), " +
                            "ipa (standard IPA pronunciation), exampleContext (the context sentence or an example English sentence), " +
                            "and exampleTranslation (Vietnamese translation of the example sentence). Do not wrap in markdown tags or backticks."),
                    Map.of("role", "user", "content", userPrompt)
            ));
            requestBody.put("response_format", Map.of("type", "json_object"));

            String jsonPayload = objectMapper.writeValueAsString(requestBody);

            Map<?, ?> response = client.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(jsonPayload)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty response received from OpenAI/DeepSeek API");
            }

            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("No choices returned in AI chat completion");
            }

            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            if (message == null || message.get("content") == null) {
                throw new IllegalStateException("No message content returned in AI chat choice");
            }

            String content = message.get("content").toString().trim();
            String cleanJson = cleanMarkdown(content);
            JsonNode root = objectMapper.readTree(cleanJson);

            String parsedText = root.hasNonNull("text") ? root.get("text").asText() : text;
            String parsedTranslation = root.hasNonNull("translation")
                    ? root.get("translation").asText()
                    : (root.hasNonNull("meaning") ? root.get("meaning").asText() : "");
            String parsedPos = root.hasNonNull("partOfSpeech") ? root.get("partOfSpeech").asText() : "Noun";
            String parsedIpa = root.hasNonNull("ipa") ? root.get("ipa").asText() : "";
            String parsedExampleContext = root.hasNonNull("exampleContext")
                    ? root.get("exampleContext").asText()
                    : (sentence != null ? sentence : "");
            String parsedExampleTranslation = root.hasNonNull("exampleTranslation")
                    ? root.get("exampleTranslation").asText()
                    : "";

            return AIResult.builder()
                    .text(parsedText)
                    .translation(parsedTranslation)
                    .partOfSpeech(parsedPos)
                    .ipa(parsedIpa)
                    .exampleContext(parsedExampleContext)
                    .exampleTranslation(parsedExampleTranslation)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("OpenAI/DeepSeek translation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String classifyTopic(String noteContent, List<String> existingTopics) {
        try {
            String prompt = String.format("Classify the following note content into one of these topics: %s. Reply with only the topic name.",
                    String.join(", ", existingTopics));
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You classify text. Only return the matching topic name or suggest a new one if none matches."),
                    Map.of("role", "user", "content", prompt + "\nContent: " + noteContent)
            ));

            Map<?, ?> response = defaultRestClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
            }

            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
            }

            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            return message != null && message.get("content") != null
                    ? message.get("content").toString().trim()
                    : (existingTopics.isEmpty() ? "General English" : existingTopics.get(0));
        } catch (Exception e) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }
    }

    @Override
    public double evaluateEssay(String topicText, String essayText) {
        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "deepseek-chat");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Grade the following essay based on topic relevance, vocabulary, and grammar. Return ONLY a single number from 0.0 to 10.0."),
                    Map.of("role", "user", "content", "Topic: " + topicText + "\nEssay: " + essayText)
            ));

            Map<?, ?> response = defaultRestClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty response received from essay evaluation");
            }

            List<?> choices = (List<?>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new IllegalStateException("No choices returned from essay evaluation");
            }

            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            return Double.parseDouble(message.get("content").toString().trim());
        } catch (Exception e) {
            throw new RuntimeException("OpenAI essay evaluation failed: " + e.getMessage(), e);
        }
    }

    private String cleanMarkdown(String text) {
        if (text == null) return "{}";
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceAll("^```(?:json)?\\s*", "").replaceAll("\\s*```$", "").trim();
        }
        return trimmed;
    }
}
