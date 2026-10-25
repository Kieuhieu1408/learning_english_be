package com.learning.english.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record ReviewDueResponse(
        long totalDue,
        List<WordDto> words
) {}
