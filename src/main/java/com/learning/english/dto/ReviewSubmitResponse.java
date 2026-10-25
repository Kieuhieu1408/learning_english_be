package com.learning.english.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record ReviewSubmitResponse(
        List<UpdatedWordStateDto> updatedWords,
        int reviewedToday,
        int streakDays
) {
    @Builder
    public record UpdatedWordStateDto(
            UUID id,
            String status,
            int correctStreak,
            double easeFactor,
            int intervalDays,
            String nextReviewAt
    ) {}
}
