package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class NoteRequest {

    private UUID id;

    private UUID topicId;

    private Long baseVersion;

    @NotBlank(message = "Title cannot be blank")
    private String title;

    private String content;

    public NoteRequest() {}

    public NoteRequest(UUID id, UUID topicId, String title, String content) {
        this.id = id;
        this.topicId = topicId;
        this.title = title;
        this.content = content;
    }

    public NoteRequest(UUID id, UUID topicId, Long baseVersion, String title, String content) {
        this.id = id;
        this.topicId = topicId;
        this.baseVersion = baseVersion;
        this.title = title;
        this.content = content;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public void setTopicId(UUID topicId) {
        this.topicId = topicId;
    }

    public Long getBaseVersion() {
        return baseVersion;
    }

    public void setBaseVersion(Long baseVersion) {
        this.baseVersion = baseVersion;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}
