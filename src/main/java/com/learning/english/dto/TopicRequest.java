package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public class TopicRequest {
    private UUID id;

    @NotBlank(message = "Topic name cannot be blank")
    private String name;

    public TopicRequest() {}

    public TopicRequest(String name) {
        this.name = name;
    }

    public TopicRequest(UUID id, String name) {
        this.id = id;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
