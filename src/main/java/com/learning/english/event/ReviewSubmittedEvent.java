package com.learning.english.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReviewSubmittedEvent(
        UUID userId,
        List<ReviewItem> items
) {
    public record ReviewItem(
            UUID eventId,
            UUID wordId,
            boolean isCorrect,
            int quality,
            Instant reviewedAt
    ) {}
}
