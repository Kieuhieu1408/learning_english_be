package com.learning.english.dto;

import lombok.Builder;

@Builder
public record CheckAnswerResponse(
        String outcome,
        boolean isAccepted,
        int quality,
        String feedback
) {}
