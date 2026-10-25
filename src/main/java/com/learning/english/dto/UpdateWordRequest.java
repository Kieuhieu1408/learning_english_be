package com.learning.english.dto;

import lombok.Builder;

@Builder
public record UpdateWordRequest(
        String text,
        String primaryMeaning,
        String ipa
) {}
