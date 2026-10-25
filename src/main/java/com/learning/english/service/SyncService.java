package com.learning.english.service;

import com.learning.english.constant.WordStatus;
import com.learning.english.dto.*;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SyncService {

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private WordRepository wordRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private WordService wordService;

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserPrincipal().getId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public SyncCountersResponse getCounters() {
        UUID userId = getCurrentUserPrincipal().getId();
        UserSyncMetadata metadata = metadataRepository.findByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("User sync metadata not found"));

        var zoneId = wordService.getUserZoneId(userId);
        var today = com.learning.english.util.TimezoneUtil.today(zoneId);
        var dueCutoff = com.learning.english.util.TimezoneUtil.endOfDayInstant(today, zoneId);

        int dueCount = (int) userWordRepository.countDueWords(userId, dueCutoff);
        int totalWords = (int) userWordRepository.countByUserIdAndDeletedAtIsNull(userId);
        int reviewedToday = dailyStatsRepository.findByUserIdAndStudyDate(userId, today)
                .map(d -> d.getWordsReviewed() != null ? d.getWordsReviewed() : 0)
                .orElse(0);
        int streakDays = metadata.getCurrentStreakDays() != null ? metadata.getCurrentStreakDays() : 0;

        return SyncCountersResponse.builder()
                .dueCount(dueCount)
                .reviewedToday(reviewedToday)
                .totalWords(totalWords > 0 ? totalWords : metadata.getTotalWordCount())
                .streakDays(streakDays)
                .dailyWordCount(metadata.getDailyWordCount())
                .totalWordCount(totalWords > 0 ? totalWords : metadata.getTotalWordCount())
                .lastSyncAt(metadata.getLastSyncedAt() != null ? metadata.getLastSyncedAt() : metadata.getUpdatedAt())
                .build();
    }

    @Transactional
    public SyncResponse syncBatch(SyncRequest request, String idempotencyKey) {
        if (idempotencyKey != null && idempotencyService.has(idempotencyKey)) {
            return (SyncResponse) idempotencyService.get(idempotencyKey);
        }

        User user = getCurrentUserEntity();
        UUID userId = user.getId();

        // 1. Process deletes
        if (request.deletedWordIds() != null && !request.deletedWordIds().isEmpty()) {
            int deleteCount = 0;
            for (UUID wordId : request.deletedWordIds()) {
                Optional<Word> wordOpt = wordRepository.findByIdAndUserId(wordId, userId);
                if (wordOpt.isPresent()) {
                    wordRepository.delete(wordOpt.get());
                    deleteCount++;
                }
            }
            if (deleteCount > 0) {
                metadataRepository.updateWordCount(userId, -deleteCount);
            }
        }

        if (request.deletedNoteIds() != null && !request.deletedNoteIds().isEmpty()) {
            for (UUID noteId : request.deletedNoteIds()) {
                noteRepository.findByIdAndUserId(noteId, userId).ifPresent(noteRepository::delete);
            }
        }

        if (request.deletedTopicIds() != null && !request.deletedTopicIds().isEmpty()) {
            for (UUID topicId : request.deletedTopicIds()) {
                topicRepository.findByIdAndUserId(topicId, userId).ifPresent(topicRepository::delete);
            }
        }

        // 2. Process Topics
        List<Topic> topicsToSave = new ArrayList<>();
        Map<UUID, Topic> topicMap = new HashMap<>();
        if (request.topics() != null) {
            for (SyncRequest.TopicDto dto : request.topics()) {
                Topic topic = topicMap.get(dto.id());
                if (topic == null) {
                    topic = topicRepository.findById(dto.id())
                            .orElseGet(() -> {
                                Topic t = new Topic();
                                t.setId(dto.id());
                                return t;
                            });
                }
                topic.setUser(user);
                topic.setName(dto.name());
                topicsToSave.add(topic);
                topicMap.put(topic.getId(), topic);
            }
            topicRepository.saveAll(topicsToSave);
        }

        // 3. Process Notes
        List<Note> notesToSave = new ArrayList<>();
        Map<UUID, Note> noteMap = new HashMap<>();
        if (request.notes() != null) {
            for (SyncRequest.NoteDto dto : request.notes()) {
                Note note = noteMap.get(dto.id());
                if (note == null) {
                    note = noteRepository.findById(dto.id())
                            .orElseGet(() -> {
                                Note n = new Note();
                                n.setId(dto.id());
                                return n;
                            });
                }
                note.setUser(user);
                note.setTitle(dto.title());
                note.setContent(dto.content());
                note.setVersion(note.isNew() ? null : dto.version());
                
                if (dto.topicId() != null) {
                    Topic topic = topicMap.get(dto.topicId());
                    if (topic == null) {
                        topic = topicRepository.findById(dto.topicId()).orElse(null);
                    }
                    note.setTopic(topic);
                } else {
                    note.setTopic(null);
                }
                notesToSave.add(note);
                noteMap.put(note.getId(), note);
            }
            noteRepository.saveAll(notesToSave);
        }

        // 4. Process Words & update counters
        List<Word> wordsToSave = new ArrayList<>();
        int newWordCount = 0;
        if (request.words() != null) {
            for (SyncRequest.WordDto dto : request.words()) {
                Optional<Word> wordOpt = wordRepository.findById(dto.id());
                Word word;
                if (wordOpt.isPresent()) {
                    word = wordOpt.get();
                    if (dto.version() < word.getVersion()) {
                        continue;
                    }
                } else {
                    word = new Word();
                    word.setId(dto.id());
                    newWordCount++;
                }

                word.setUser(user);
                word.setText(dto.text());
                word.setTranslation(dto.translation());
                word.setPartOfSpeech(dto.partOfSpeech());
                word.setIpa(dto.ipa());
                word.setExampleContext(dto.exampleContext());
                word.setExampleTranslation(dto.exampleTranslation());
                word.setStatus(WordStatus.valueOf(dto.status()));
                word.setCorrectStreak(dto.correctStreak());
                word.setDifficulty(dto.difficulty());
                word.setIntervalDays(dto.intervalDays());
                word.setNextReviewAt(LocalDate.parse(dto.nextReviewAt()));
                word.setFailedEssayStreak(dto.failedEssayStreak());
                word.setVersion(word.isNew() ? null : dto.version());

                if (dto.noteId() != null) {
                    Note note = noteMap.get(dto.noteId());
                    if (note == null) {
                        note = noteRepository.findById(dto.noteId()).orElse(null);
                    }
                    word.setNote(note);
                } else {
                    word.setNote(null);
                }

                wordsToSave.add(word);
            }
            wordRepository.saveAll(wordsToSave);

            if (newWordCount > 0) {
                metadataRepository.updateWordCount(userId, newWordCount);
                metadataRepository.updateDailyWordCount(userId, newWordCount);
            }
        }

        UserSyncMetadata finalMetadata = metadataRepository.findByUserId(userId).orElseThrow();

        // 5. Gather all fresh server data to return to client
        List<SyncRequest.TopicDto> responseTopics = topicRepository.findAllByUserId(userId).stream()
                .map(t -> new SyncRequest.TopicDto(
                    t.getId(),
                    t.getName(),
                    t.getCreatedAt().toEpochMilli(),
                    t.getUpdatedAt().toEpochMilli()
                )).collect(Collectors.toList());

        List<SyncRequest.NoteDto> responseNotes = noteRepository.findAllByUserId(userId).stream()
                .map(n -> new SyncRequest.NoteDto(
                    n.getId(),
                    n.getTopic() != null ? n.getTopic().getId() : null,
                    n.getTitle(),
                    n.getContent(),
                    n.getVersion() != null ? n.getVersion() : 0,
                    n.getCreatedAt().toEpochMilli(),
                    n.getUpdatedAt().toEpochMilli()
                )).collect(Collectors.toList());

        List<SyncRequest.WordDto> responseWords = wordRepository.findAllByUserId(userId).stream()
                .map(w -> new SyncRequest.WordDto(
                    w.getId(),
                    w.getNote() != null ? w.getNote().getId() : null,
                    w.getText(),
                    w.getTranslation(),
                    w.getPartOfSpeech(),
                    w.getIpa(),
                    w.getExampleContext(),
                    w.getExampleTranslation(),
                    w.getStatus().name(),
                    w.getCorrectStreak(),
                    w.getDifficulty(),
                    w.getIntervalDays(),
                    w.getNextReviewAt().toString(),
                    w.getFailedEssayStreak(),
                    w.getVersion() != null ? w.getVersion() : 0,
                    w.getCreatedAt().toEpochMilli(),
                    w.getUpdatedAt().toEpochMilli()
                )).collect(Collectors.toList());

        SyncResponse response = SyncResponse.builder()
                .status("SUCCESS")
                .topics(responseTopics)
                .notes(responseNotes)
                .words(responseWords)
                .counters(SyncCountersResponse.builder()
                        .dailyWordCount(finalMetadata.getDailyWordCount())
                        .totalWordCount(finalMetadata.getTotalWordCount())
                        .lastSyncAt(finalMetadata.getUpdatedAt())
                        .build())
                .build();

        if (idempotencyKey != null) {
            idempotencyService.put(idempotencyKey, response, 300);
        }

        return response;
    }
}
