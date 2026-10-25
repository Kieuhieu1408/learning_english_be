package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
public record ReviewSubmitRequest(
        List<ReviewEventDto> events
) {
    @Builder
    public record ReviewEventDto(
            UUID eventId,
            UUID wordId,
            Integer quality,
            Boolean isCorrect,
            String mode,
            Instant reviewedAt
    ) {}
}
