package com.learning.english.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record CheckAnswerRequest(
        @NotNull(message = "wordId is required")
        UUID wordId,
        String sentence,
        String typedAnswer
) {}
