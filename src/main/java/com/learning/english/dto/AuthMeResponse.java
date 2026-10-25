package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.UUID;

@Builder
public record AuthMeResponse(
        UUID userId,
        String username,
        String name,
        String email,
        String role,
        ConfigDto config,
        CountersDto counters
) {
    @Builder
    public record ConfigDto(
            String timezone,
            int dailyTarget,
            String theme,
            int sidebarWidth
    ) {}

    @Builder
    public record CountersDto(
            long dueCount,
            int reviewedToday,
            long totalWords,
            int streakDays,
            Instant lastSyncAt
    ) {}
}
