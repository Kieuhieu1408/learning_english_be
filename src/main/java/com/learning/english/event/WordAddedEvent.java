package com.learning.english.event;

import java.time.Instant;
import java.util.UUID;

public record WordAddedEvent(
        UUID userId,
        UUID wordId,
        int count,
        Instant occurredAt
) {}
