package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record WordDto(
        UUID id,
        String text,
        String primaryMeaning,
        String ipa,
        String status,
        Integer correctStreak,
        Double easeFactor,
        Integer intervalDays,
        String nextReviewAt,
        Integer contextsCount,
        List<WordContextDto> contexts,
        Instant createdAt,
        Instant updatedAt
) {}
