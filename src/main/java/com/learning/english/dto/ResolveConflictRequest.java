package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ResolveConflictRequest(
        @NotNull(message = "noteId is required")
        UUID noteId,
        @NotBlank(message = "choice is required (KEEP_LOCAL or APPLY_CLOUD)")
        String choice,
        String resolvedTitle,
        String resolvedContent
) {}
