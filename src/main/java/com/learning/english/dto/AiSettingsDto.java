package com.learning.english.dto;

public record AiSettingsDto(
    String provider,
    String apiKey,
    String translationModel,
    String quizModel
) {}
