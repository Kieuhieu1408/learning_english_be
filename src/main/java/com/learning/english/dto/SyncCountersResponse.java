package com.learning.english.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SyncCountersResponse {
    private int dueCount;
    private int reviewedToday;
    private int totalWords;
    private int streakDays;
    private int dailyWordCount;
    private int totalWordCount;
    private Instant lastSyncAt;
}
