package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SaveWordContextRequest(
        UUID wordId,
        UUID contextId,
        @NotBlank(message = "Word text cannot be blank")
        String text,
        @NotBlank(message = "Meaning cannot be blank")
        String meaning,
        String sentence,
        String sentenceTranslation,
        String partOfSpeech,
        String ipa,
        UUID noteId,
        String noteTitleSnapshot
) {}
