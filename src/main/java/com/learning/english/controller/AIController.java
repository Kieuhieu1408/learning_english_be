package com.learning.english.controller;

import com.learning.english.client.dto.AIResult;
import com.learning.english.service.AIService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/ai")
public class AIController {

    @Autowired
    private AIService aiService;

    @PostMapping("/translate")
    public ResponseEntity<List<AIResult>> translateWords(@RequestBody List<String> words) {
        try {
            List<AIResult> results = aiService.translateWordsInParallel(words);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}
