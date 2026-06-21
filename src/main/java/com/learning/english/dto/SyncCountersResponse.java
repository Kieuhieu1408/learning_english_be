package com.learning.english.dto;

import java.time.Instant;

public class SyncCountersResponse {
    private int dailyWordCount;
    private int totalWordCount;
    private Instant lastSyncAt;

    public SyncCountersResponse() {}

    public SyncCountersResponse(int dailyWordCount, int totalWordCount, Instant lastSyncAt) {
        this.dailyWordCount = dailyWordCount;
        this.totalWordCount = totalWordCount;
        this.lastSyncAt = lastSyncAt;
    }

    public int getDailyWordCount() {
        return dailyWordCount;
    }

    public void setDailyWordCount(int dailyWordCount) {
        this.dailyWordCount = dailyWordCount;
    }

    public int getTotalWordCount() {
        return totalWordCount;
    }

    public void setTotalWordCount(int totalWordCount) {
        this.totalWordCount = totalWordCount;
    }

    public Instant getLastSyncAt() {
        return lastSyncAt;
    }

    public void setLastSyncAt(Instant lastSyncAt) {
        this.lastSyncAt = lastSyncAt;
    }

    public static SyncCountersResponseBuilder builder() {
        return new SyncCountersResponseBuilder();
    }

    public static class SyncCountersResponseBuilder {
        private int dailyWordCount;
        private int totalWordCount;
        private Instant lastSyncAt;

        public SyncCountersResponseBuilder dailyWordCount(int dailyWordCount) {
            this.dailyWordCount = dailyWordCount;
            return this;
        }

        public SyncCountersResponseBuilder totalWordCount(int totalWordCount) {
            this.totalWordCount = totalWordCount;
            return this;
        }

        public SyncCountersResponseBuilder lastSyncAt(Instant lastSyncAt) {
            this.lastSyncAt = lastSyncAt;
            return this;
        }

        public SyncCountersResponse build() {
            return new SyncCountersResponse(dailyWordCount, totalWordCount, lastSyncAt);
        }
    }
}
