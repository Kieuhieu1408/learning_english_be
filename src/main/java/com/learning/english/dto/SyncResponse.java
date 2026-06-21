package com.learning.english.dto;

import java.util.List;

public class SyncResponse {
    private String status; // SUCCESS, CONFLICT
    private List<SyncRequest.TopicDto> topics;
    private List<SyncRequest.NoteDto> notes;
    private List<SyncRequest.WordDto> words;
    private SyncCountersResponse counters;

    public SyncResponse() {}

    public SyncResponse(String status, List<SyncRequest.TopicDto> topics, List<SyncRequest.NoteDto> notes,
                        List<SyncRequest.WordDto> words, SyncCountersResponse counters) {
        this.status = status;
        this.topics = topics;
        this.notes = notes;
        this.words = words;
        this.counters = counters;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public List<SyncRequest.TopicDto> getTopics() {
        return topics;
    }

    public void setTopics(List<SyncRequest.TopicDto> topics) {
        this.topics = topics;
    }

    public List<SyncRequest.NoteDto> getNotes() {
        return notes;
    }

    public void setNotes(List<SyncRequest.NoteDto> notes) {
        this.notes = notes;
    }

    public List<SyncRequest.WordDto> getWords() {
        return words;
    }

    public void setWords(List<SyncRequest.WordDto> words) {
        this.words = words;
    }

    public SyncCountersResponse getCounters() {
        return counters;
    }

    public void setCounters(SyncCountersResponse counters) {
        this.counters = counters;
    }

    public static SyncResponseBuilder builder() {
        return new SyncResponseBuilder();
    }

    public static class SyncResponseBuilder {
        private String status;
        private List<SyncRequest.TopicDto> topics;
        private List<SyncRequest.NoteDto> notes;
        private List<SyncRequest.WordDto> words;
        private SyncCountersResponse counters;

        public SyncResponseBuilder status(String status) {
            this.status = status;
            return this;
        }

        public SyncResponseBuilder topics(List<SyncRequest.TopicDto> topics) {
            this.topics = topics;
            return this;
        }

        public SyncResponseBuilder notes(List<SyncRequest.NoteDto> notes) {
            this.notes = notes;
            return this;
        }

        public SyncResponseBuilder words(List<SyncRequest.WordDto> words) {
            this.words = words;
            return this;
        }

        public SyncResponseBuilder counters(SyncCountersResponse counters) {
            this.counters = counters;
            return this;
        }

        public SyncResponse build() {
            return new SyncResponse(status, topics, notes, words, counters);
        }
    }
}
