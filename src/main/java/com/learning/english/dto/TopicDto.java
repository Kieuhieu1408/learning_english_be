package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record TopicDto(
        UUID id,
        String name,
        long noteCount,
        Instant createdAt,
        Instant updatedAt
) {}
