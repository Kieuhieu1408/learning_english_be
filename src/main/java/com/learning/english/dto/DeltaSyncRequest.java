package com.learning.english.dto;

import lombok.Builder;

import java.util.List;
import java.util.UUID;

@Builder
public record DeltaSyncRequest(
        String cursor,
        String timezone,
        ChangesPayload changes,
        DeletedPayload deleted
) {
    @Builder
    public record ChangesPayload(
            List<TopicChangeDto> topics,
            List<NoteChangeDto> notes,
            List<WordChangeDto> words,
            List<WordContextChangeDto> wordContexts,
            List<ReviewSubmitRequest.ReviewEventDto> reviewEvents
    ) {}

    @Builder
    public record DeletedPayload(
            List<UUID> topicIds,
            List<UUID> noteIds,
            List<UUID> wordIds,
            List<UUID> contextIds
    ) {}

    @Builder
    public record TopicChangeDto(
            UUID id,
            String name
    ) {}

    @Builder
    public record NoteChangeDto(
            UUID id,
            UUID topicId,
            Long baseVersion,
            String title,
            String content
    ) {}

    @Builder
    public record WordChangeDto(
            UUID id,
            String text,
            String primaryMeaning,
            String ipa,
            String status,
            Integer correctStreak,
            Double easeFactor,
            Integer intervalDays,
            String nextReviewAt
    ) {}

    @Builder
    public record WordContextChangeDto(
            UUID id,
            UUID wordId,
            UUID noteId,
            String noteTitleSnapshot,
            String partOfSpeech,
            String meaning,
            String sentence,
            String sentenceTranslation
    ) {}
}
