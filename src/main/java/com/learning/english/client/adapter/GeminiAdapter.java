package com.learning.english.client.adapter;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.english.client.AIClient;
import com.learning.english.client.dto.AIResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Component("geminiAdapter")
public class GeminiAdapter implements AIClient {

    private final RestClient restClient;
    private final String defaultApiKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GeminiAdapter(
            @Value("${app.gemini.api-url:https://generativelanguage.googleapis.com/v1beta}") String apiUrl,
            @Value("${app.gemini.api-key:}") String apiKey) {
        this.defaultApiKey = apiKey != null ? apiKey.trim() : "";
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    @Override
    public AIResult translateAndDefine(String text) {
        return translateAndDefineWithContext(text, null);
    }

    @Override
    public AIResult translateAndDefineWithContext(String text, String sentence) {
        return translateAndDefineWithContext(text, sentence, null, null);
    }

    public AIResult translateAndDefineWithContext(String text, String sentence, String overrideApiKey, String overrideModel) {
        String key = (overrideApiKey != null && !overrideApiKey.isBlank()) ? overrideApiKey.trim() : this.defaultApiKey;
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured. Please enter your Gemini API key in AI Settings.");
        }

        String model = (overrideModel != null && !overrideModel.isBlank()) ? overrideModel.trim() : "gemini-1.5-flash";

        try {
            String sentenceContext = (sentence != null && !sentence.isBlank())
                    ? String.format(" Context sentence: \"%s\".", sentence.trim())
                    : "";

            String prompt = String.format(
                    "You are a professional English-Vietnamese lexicographer. Translate the English word/phrase '%s'.%s " +
                    "Return ONLY a valid JSON object with exactly these fields: " +
                    "text (the English word), translation (Vietnamese meaning in this context), partOfSpeech (Noun/Verb/Adjective/...), " +
                    "ipa (standard IPA pronunciation), exampleContext (the context sentence or an example English sentence), " +
                    "and exampleTranslation (Vietnamese translation of the example sentence). Do not wrap in markdown tags or backticks.",
                    text, sentenceContext
            );

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))),
                    "generationConfig", Map.of("responseMimeType", "application/json")
            );

            Map<?, ?> response = restClient.post()
                    .uri("/models/" + model + ":generateContent?key=" + key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty response received from Gemini API");
            }

            String responseText = extractTextFromGeminiResponse(response);
            String cleanJson = cleanMarkdown(responseText);
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
            throw new RuntimeException("Gemini translation failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String classifyTopic(String noteContent, List<String> existingTopics) {
        if (defaultApiKey.isBlank()) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }

        try {
            String prompt = String.format("Classify this note content into one of these topics: %s. Reply with only the topic name.\nContent: %s",
                    String.join(", ", existingTopics), noteContent);
            Map<String, Object> requestBody = buildGeminiRequestBody(prompt);

            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-1.5-flash:generateContent?key=" + defaultApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
            }

            return extractTextFromGeminiResponse(response).trim();
        } catch (Exception e) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }
    }

    @Override
    public double evaluateEssay(String topicText, String essayText) {
        if (defaultApiKey.isBlank()) {
            throw new IllegalStateException("Gemini API key is not configured for essay evaluation.");
        }

        try {
            String prompt = String.format("Grade the following essay based on topic relevance. Return ONLY a single number from 0.0 to 10.0.\nTopic: %s\nEssay: %s",
                    topicText, essayText);
            Map<String, Object> requestBody = buildGeminiRequestBody(prompt);

            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-1.5-flash:generateContent?key=" + defaultApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                throw new IllegalStateException("Empty response received from Gemini API");
            }

            return Double.parseDouble(extractTextFromGeminiResponse(response).trim());
        } catch (Exception e) {
            throw new RuntimeException("Gemini essay evaluation failed: " + e.getMessage(), e);
        }
    }

    private Map<String, Object> buildGeminiRequestBody(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(textPart));
        return Map.of("contents", List.of(parts));
    }

    private String extractTextFromGeminiResponse(Map<?, ?> response) {
        List<?> candidates = (List<?>) response.get("candidates");
        if (candidates == null || candidates.isEmpty()) {
            throw new IllegalStateException("No candidates in Gemini response");
        }
        Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
        if (content == null) {
            throw new IllegalStateException("No content in Gemini candidate");
        }
        List<?> parts = (List<?>) content.get("parts");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalStateException("No parts in Gemini content");
        }
        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
        return firstPart.get("text").toString();
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
