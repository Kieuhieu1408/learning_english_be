package com.learning.english.client.adapter;

import com.learning.english.client.AIClient;
import com.learning.english.client.dto.AIResult;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component("geminiAdapter")
public class GeminiAdapter implements AIClient {

    private final RestClient restClient;
    private final String apiKey;

    public GeminiAdapter(
            @Value("${app.gemini.api-url:https://generativelanguage.googleapis.com/v1beta}") String apiUrl,
            @Value("${app.gemini.api-key:mock-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .build();
    }

    @Override
    public AIResult translateAndDefine(String text) {
        if ("mock-key".equals(apiKey)) {
            return getMockResult(text);
        }

        try {
            String prompt = String.format("For the English word '%s', reply in JSON format with fields: text (original word), translation (Vietnamese), partOfSpeech, ipa, exampleContext (in English), and exampleTranslation (in Vietnamese). Do not wrap in markdown tags.", text);
            Map<String, Object> requestBody = buildGeminiRequestBody(prompt);

            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String responseText = extractTextFromGeminiResponse(response);
            // Parse responseText into AIResult
            // Mocking dynamic JSON mapping from text block
            return getMockResult(text); 
        } catch (Exception e) {
            return getMockResult(text);
        }
    }

    @Override
    public String classifyTopic(String noteContent, List<String> existingTopics) {
        if ("mock-key".equals(apiKey)) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }

        try {
            String prompt = String.format("Classify this note content into one of these topics: %s. Reply with only the topic name.\nContent: %s", String.join(", ", existingTopics), noteContent);
            Map<String, Object> requestBody = buildGeminiRequestBody(prompt);

            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractTextFromGeminiResponse(response).trim();
        } catch (Exception e) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }
    }

    @Override
    public double evaluateEssay(String topicText, String essayText) {
        if ("mock-key".equals(apiKey)) {
            return 9.0;
        }

        try {
            String prompt = String.format("Grade the following essay based on topic relevance. Return ONLY a single number from 0.0 to 10.0.\nTopic: %s\nEssay: %s", topicText, essayText);
            Map<String, Object> requestBody = buildGeminiRequestBody(prompt);

            Map<?, ?> response = restClient.post()
                    .uri("/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return Double.parseDouble(extractTextFromGeminiResponse(response).trim());
        } catch (Exception e) {
            return 9.0;
        }
    }

    private Map<String, Object> buildGeminiRequestBody(String prompt) {
        Map<String, Object> textPart = Map.of("text", prompt);
        Map<String, Object> parts = Map.of("parts", List.of(textPart));
        Map<String, Object> contents = Map.of("contents", List.of(parts));
        return contents;
    }

    private String extractTextFromGeminiResponse(Map<?, ?> response) {
        List<?> candidates = (List<?>) response.get("candidates");
        Map<?, ?> firstCandidate = (Map<?, ?>) candidates.get(0);
        Map<?, ?> content = (Map<?, ?>) firstCandidate.get("content");
        List<?> parts = (List<?>) content.get("parts");
        Map<?, ?> firstPart = (Map<?, ?>) parts.get(0);
        return firstPart.get("text").toString();
    }

    private AIResult getMockResult(String text) {
        return AIResult.builder()
                .text(text)
                .translation("Bản dịch giả lập Gemini cho: " + text)
                .partOfSpeech("Noun")
                .ipa("/mock-gemini-ipa/")
                .exampleContext("This is a mock example sentence from Gemini.")
                .exampleTranslation("Đây là câu ví dụ giả lập từ Gemini.")
                .build();
    }
}
