package com.learning.english.client;

import com.learning.english.client.dto.AIResult;
import java.util.List;

public interface AIClient {
    /**
     * Translates a vocabulary word, finds its IPA pronunciation, part of speech, 
     * and generates a contextual example sentence with its translation.
     */
    AIResult translateAndDefine(String text);

    /**
     * Translates a vocabulary word within an explicit sentence context.
     */
    AIResult translateAndDefineWithContext(String text, String sentence);

    /**
     * Classifies a note content into one of the existing topics, 
     * or suggests a new topic name if none match.
     */
    String classifyTopic(String noteContent, List<String> existingTopics);

    /**
     * Evaluates a user's essay practice context and returns a numeric score (0-10) 
     * along with grammatical feedback.
     */
    double evaluateEssay(String topicText, String essayText);
}
