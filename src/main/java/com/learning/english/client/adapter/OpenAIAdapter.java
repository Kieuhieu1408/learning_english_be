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

@Component("openaiAdapter")
public class OpenAIAdapter implements AIClient {

    private final RestClient restClient;
    private final String apiKey;

    public OpenAIAdapter(
            @Value("${app.openai.api-url:https://api.openai.com/v1}") String apiUrl,
            @Value("${app.openai.api-key:mock-key}") String apiKey) {
        this.apiKey = apiKey;
        this.restClient = RestClient.builder()
                .baseUrl(apiUrl)
                .defaultHeader("Authorization", "Bearer " + apiKey)
                .build();
    }

    @Override
    public AIResult translateAndDefine(String text) {
        if ("mock-key".equals(apiKey)) {
            return getMockResult(text);
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You are a lexicographer. For the given English word, reply in JSON format with fields: text (original word), translation (Vietnamese), partOfSpeech, ipa, exampleContext (in English), and exampleTranslation (in Vietnamese)."),
                    Map.of("role", "user", "content", text)
            ));
            requestBody.put("response_format", Map.of("type", "json_object"));

            return restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(AIResult.class); // Spring automatically maps JSON response to DTO
        } catch (Exception e) {
            return getMockResult(text); // Fallback to mock on connection errors
        }
    }

    @Override
    public String classifyTopic(String noteContent, List<String> existingTopics) {
        if ("mock-key".equals(apiKey)) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }

        try {
            String prompt = String.format("Classify the following note content into one of these topics: %s. Reply with only the topic name.", String.join(", ", existingTopics));
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "You classify text. Only return the matching topic name or suggest a new one if none matches."),
                    Map.of("role", "user", "content", prompt + "\nContent: " + noteContent)
            ));

            Map<?, ?> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<?> choices = (List<?>) response.get("choices");
            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            return message.get("content").toString().trim();
        } catch (Exception e) {
            return existingTopics.isEmpty() ? "General English" : existingTopics.get(0);
        }
    }

    @Override
    public double evaluateEssay(String topicText, String essayText) {
        if ("mock-key".equals(apiKey)) {
            return 8.0;
        }

        try {
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", "gpt-4o-mini");
            requestBody.put("messages", List.of(
                    Map.of("role", "system", "content", "Grade the following essay based on topic relevance, vocabulary, and grammar. Return ONLY a single number from 0.0 to 10.0."),
                    Map.of("role", "user", "content", "Topic: " + topicText + "\nEssay: " + essayText)
            ));

            Map<?, ?> response = restClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            List<?> choices = (List<?>) response.get("choices");
            Map<?, ?> firstChoice = (Map<?, ?>) choices.get(0);
            Map<?, ?> message = (Map<?, ?>) firstChoice.get("message");
            return Double.parseDouble(message.get("content").toString().trim());
        } catch (Exception e) {
            return 8.0;
        }
    }

    private AIResult getMockResult(String text) {
        return AIResult.builder()
                .text(text)
                .translation("Bản dịch giả lập OpenAI cho: " + text)
                .partOfSpeech("Noun")
                .ipa("/mock-openai-ipa/")
                .exampleContext("This is a mock example sentence from OpenAI.")
                .exampleTranslation("Đây là câu ví dụ giả lập từ OpenAI.")
                .build();
    }
}
