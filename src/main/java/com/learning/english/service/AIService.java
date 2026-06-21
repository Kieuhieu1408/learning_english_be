package com.learning.english.service;

import com.learning.english.client.AIClient;
import com.learning.english.client.dto.AIResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;

@Service
public class AIService {

    private final AIClient activeAIClient;

    public AIService(
            @Value("${app.ai.provider:gemini}") String aiProvider,
            @Autowired @Qualifier("openaiAdapter") AIClient openaiAdapter,
            @Autowired @Qualifier("geminiAdapter") AIClient geminiAdapter) {
        if ("openai".equalsIgnoreCase(aiProvider)) {
            this.activeAIClient = openaiAdapter;
        } else {
            this.activeAIClient = geminiAdapter;
        }
    }

    /**
     * Translates multiple words in parallel using Virtual Threads and CompletableFuture.
     * This provides optimal throughput for concurrent network I/O calls to AI engines.
     */
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

            // Wait for all to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            List<AIResult> results = new ArrayList<>();
            for (var future : futures) {
                results.add(future.join());
            }
            return results;
        }
    }

    public String classifyNoteTopic(String content, List<String> existingTopics) {
        return activeAIClient.classifyTopic(content, existingTopics);
    }

    public double evaluateEssay(String topicText, String essayText) {
        return activeAIClient.evaluateEssay(topicText, essayText);
    }
}
