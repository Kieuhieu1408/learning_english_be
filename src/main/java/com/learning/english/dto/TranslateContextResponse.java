package com.learning.english.dto;

import lombok.Builder;

@Builder
public record TranslateContextResponse(
        String text,
        String ipa,
        String partOfSpeech,
        String meaning,
        String sentence,
        String sentenceTranslation
) {}
