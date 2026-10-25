package com.learning.english.dto;

import lombok.Builder;

import java.util.List;

@Builder
public record WordListResponse(
        long total,
        List<WordDto> words
) {}
