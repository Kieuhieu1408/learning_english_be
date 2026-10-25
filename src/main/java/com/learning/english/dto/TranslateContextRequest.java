package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.UUID;

@Builder
public record TranslateContextRequest(
        @NotBlank(message = "text is required")
        String text,
        String sentence,
        UUID noteId,
        String noteTitle
) {}
