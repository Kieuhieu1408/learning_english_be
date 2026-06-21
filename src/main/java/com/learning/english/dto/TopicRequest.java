package com.learning.english.dto;

import jakarta.validation.constraints.NotBlank;

public class TopicRequest {
    @NotBlank(message = "Topic name cannot be blank")
    private String name;

    public TopicRequest() {}

    public TopicRequest(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
