package com.learning.english.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NoteDto(
        UUID id,
        UUID topicId,
        String title,
        String content,
        Long harvestedWordCount,
        Long version,
        List<SavedWordSnippetDto> savedWords,
        Instant createdAt,
        Instant updatedAt
) {
    @Builder
    public record SavedWordSnippetDto(
            UUID wordId,
            String text,
            String meaning
    ) {}
}
