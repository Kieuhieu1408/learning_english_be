package com.learning.english.dto;

import lombok.Builder;

@Builder
public record SaveWordContextResponse(
        WordDto word,
        boolean isNewWord,
        WordContextDto context
) {}
