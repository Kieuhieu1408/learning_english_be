package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record WordContextDto(
        UUID id,
        UUID noteId,
        String noteTitle,
        String sentence,
        String sentenceTranslation,
        String meaning,
        String partOfSpeech,
        Instant createdAt
) {}
