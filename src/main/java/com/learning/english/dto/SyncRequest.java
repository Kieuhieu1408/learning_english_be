package com.learning.english.dto;

import java.util.List;
import java.util.UUID;

public record SyncRequest(
    List<TopicDto> topics,
    List<NoteDto> notes,
    List<WordDto> words,
    List<UUID> deletedTopicIds,
    List<UUID> deletedNoteIds,
    List<UUID> deletedWordIds
) {
    public record TopicDto(
        UUID id,
        String name,
        long createdAtMs,
        long updatedAtMs
    ) {}

    public record NoteDto(
        UUID id,
        UUID topicId,
        String title,
        String content,
        long version,
        long createdAtMs,
        long updatedAtMs
    ) {}

    public record WordDto(
        UUID id,
        UUID noteId,
        String text,
        String translation,
        String partOfSpeech,
        String ipa,
        String exampleContext,
        String exampleTranslation,
        String status,
        int correctStreak,
        double difficulty,
        int intervalDays,
        String nextReviewAt, // Format: yyyy-MM-dd
        int failedEssayStreak,
        long version,
        long createdAtMs,
        long updatedAtMs
    ) {}
}
