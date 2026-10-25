package com.learning.english.dto;

import lombok.Builder;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Builder
public record DeltaSyncResponse(
        String newCursor,
        ChangesResult changes,
        TombstonesResult tombstones,
        Map<String, String> idRemap,
        List<ConflictDto> conflicts,
        CountersDto counters
) {
    @Builder
    public record ChangesResult(
            List<TopicDto> topics,
            List<NoteDto> notes,
            List<WordDto> words,
            List<SyncWordContextDto> wordContexts
    ) {}

    @Builder
    public record SyncWordContextDto(
            UUID id,
            UUID wordId,
            UUID noteId,
            String noteTitleSnapshot,
            String partOfSpeech,
            String meaning,
            String sentence,
            String sentenceTranslation,
            Instant createdAt,
            Instant updatedAt
    ) {}

    @Builder
    public record TombstonesResult(
            List<UUID> topicIds,
            List<UUID> noteIds,
            List<UUID> wordIds,
            List<UUID> contextIds
    ) {}

    @Builder
    public record ConflictDto(
            UUID noteId,
            long serverVersion,
            String cloudTitle,
            String cloudContent,
            Instant cloudUpdatedAt
    ) {}

    @Builder
    public record CountersDto(
            long dueCount,
            int reviewedToday,
            long totalWords,
            int streakDays
    ) {}
}
