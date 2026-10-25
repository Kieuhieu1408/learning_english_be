package com.learning.english.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.List;

@Builder
public record ProgressDashboardResponse(
        int accuracyPercent,
        int reviewedToday,
        int dailyTarget,
        int wordsAddedToday,
        long totalWords,
        int streakDays,
        int longestStreakDays,
        DistributionDto distribution,
        List<ActivityDayDto> activityCalendar
) {
    @Builder
    public record DistributionDto(
            @JsonProperty("new")
            long newWords,
            long learning,
            long remembered,
            long mastered
    ) {}

    @Builder
    public record ActivityDayDto(
            String date,
            int count,
            int wordsAdded,
            boolean filled,
            boolean isToday
    ) {}
}
