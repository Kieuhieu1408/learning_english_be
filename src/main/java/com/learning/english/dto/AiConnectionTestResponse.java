package com.learning.english.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record AiConnectionTestResponse(
        boolean connected,
        String message,
        List<ModelOptionDto> models
) {
    @Builder
    public record ModelOptionDto(
            String id,
            String name
    ) {}
}
