package com.learning.english.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AiSettingsDto(
        String provider,
        String customBaseUrl,
        String apiKey,
        Boolean hasApiKey,
        String apiKeyHint,
        String translationModel,
        String quizModel,
        String chatModel
) {}
