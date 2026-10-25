package com.learning.english.controller;

import com.learning.english.dto.*;
import com.learning.english.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@RestController
@RequestMapping("/api/review")
public class ReviewController {

    @Autowired
    private ReviewService reviewService;

    @GetMapping("/due")
    public ResponseEntity<ReviewDueResponse> getDueWords(
            @RequestParam(required = false, defaultValue = "15") int limit,
            @RequestParam(required = false) String excludeIds
    ) {
        List<UUID> parsedExcludeIds = List.of();
        if (excludeIds != null && !excludeIds.isBlank()) {
            parsedExcludeIds = Arrays.stream(excludeIds.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> {
                        try {
                            return UUID.fromString(s);
                        } catch (IllegalArgumentException e) {
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .toList();
        }
        return ResponseEntity.ok(reviewService.getDueWords(limit, parsedExcludeIds));
    }

    @PostMapping("/submit")
    public ResponseEntity<ReviewSubmitResponse> submitReviews(@RequestBody ReviewSubmitRequest request) {
        return ResponseEntity.ok(reviewService.submitReviews(request));
    }

    @PostMapping("/check-answer")
    public ResponseEntity<CheckAnswerResponse> checkAnswer(@Valid @RequestBody CheckAnswerRequest request) {
        return ResponseEntity.ok(reviewService.checkAnswer(request));
    }
}
