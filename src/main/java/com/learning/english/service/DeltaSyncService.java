package com.learning.english.service;

import com.learning.english.constant.WordStatus;
import com.learning.english.dto.*;
import com.learning.english.event.WordAddedEvent;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.TextNormalizationUtil;
import com.learning.english.util.TimezoneUtil;
import com.learning.english.util.UuidV7Generator;
import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DeltaSyncService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private WordService wordService;

    @Autowired
    private ReviewService reviewService;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private EntityManager entityManager;

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    private UUID getCurrentUserId() {
        return getCurrentUserPrincipal().getId();
    }

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserId()).orElseThrow();
    }

    @Transactional
    public DeltaSyncResponse syncDelta(DeltaSyncRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();

        // Update timezone if provided by client
        if (request != null && request.timezone() != null && !request.timezone().isBlank()) {
            UserAppConfig config = userAppConfigRepository.findByUserId(userId)
                    .orElseGet(() -> UserAppConfig.builder().user(user).build());
            config.setTimezone(TimezoneUtil.parseZoneId(request.timezone()).getId());
            userAppConfigRepository.save(config);
        }

        ZoneId zoneId = wordService.getUserZoneId(userId);
        Instant cursorInstant = parseCursor(request != null ? request.cursor() : null);
        Instant now = Instant.now();

        Map<String, String> idRemap = new LinkedHashMap<>();
        Map<UUID, UUID> uuidRemap = new HashMap<>();
        List<DeltaSyncResponse.ConflictDto> conflicts = new ArrayList<>();
        Set<UUID> touchedWordIds = new HashSet<>();

        // 1. Process soft-deletes (Tombstones from client)
        if (request != null && request.deleted() != null) {
            DeltaSyncRequest.DeletedPayload deleted = request.deleted();

            if (deleted.topicIds() != null) {
                for (UUID topicId : deleted.topicIds()) {
                    topicRepository.findByIdAndUserIdAndDeletedAtIsNull(topicId, userId).ifPresent(topic -> {
                        topic.setDeletedAt(now);
                        topic.setUpdatedAt(now);
                        topicRepository.save(topic);

                        List<Note> notesInTopic = noteRepository.findAllByTopicIdAndUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(topicId, userId);
                        if (!notesInTopic.isEmpty()) {
                            List<UUID> noteIds = notesInTopic.stream().map(Note::getId).toList();
                            wordContextRepository.detachContextsFromNotes(noteIds, userId, now);
                            for (Note n : notesInTopic) {
                                n.setDeletedAt(now);
                                n.setUpdatedAt(now);
                            }
                            noteRepository.saveAll(notesInTopic);
                        }
                    });
                }
            }

            if (deleted.noteIds() != null) {
                for (UUID noteId : deleted.noteIds()) {
                    noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId).ifPresent(note -> {
                        wordContextRepository.detachContextsFromNote(noteId, userId, now);
                        note.setDeletedAt(now);
                        note.setUpdatedAt(now);
                        noteRepository.save(note);
                    });
                }
            }

            if (deleted.wordIds() != null) {
                for (UUID wordId : deleted.wordIds()) {
                    userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(wordId, userId).ifPresent(word -> {
                        word.setDeletedAt(now);
                        word.setUpdatedAt(now);
                        if (word.getContexts() != null) {
                            for (WordContext ctx : word.getContexts()) {
                                if (ctx.getDeletedAt() == null) {
                                    ctx.setDeletedAt(now);
                                    ctx.setUpdatedAt(now);
                                }
                            }
                        }
                        userWordRepository.save(word);
                    });
                }
            }

            if (deleted.contextIds() != null) {
                for (UUID ctxId : deleted.contextIds()) {
                    wordContextRepository.findByIdAndUserIdAndDeletedAtIsNull(ctxId, userId).ifPresent(ctx -> {
                        ctx.setDeletedAt(now);
                        ctx.setUpdatedAt(now);
                        wordContextRepository.save(ctx);
                    });
                }
            }
        }

        // 2. Process Changes
        if (request != null && request.changes() != null) {
            DeltaSyncRequest.ChangesPayload changes = request.changes();

            // 2a. Topics
            if (changes.topics() != null) {
                for (DeltaSyncRequest.TopicChangeDto dto : changes.topics()) {
                    if (dto.name() == null || dto.name().isBlank()) {
                        continue;
                    }
                    UUID topicId = dto.id() != null ? dto.id() : UuidV7Generator.generate();
                    Topic topic = topicRepository.findByIdAndUserId(topicId, userId)
                            .orElseGet(() -> Topic.builder()
                                    .id(topicId)
                                    .user(user)
                                    .createdAt(now)
                                    .build());
                    topic.setName(dto.name().trim());
                    topic.setDeletedAt(null);
                    topic.setUpdatedAt(now);
                    topicRepository.save(topic);
                }
            }

            // 2b. Notes (with baseVersion conflict check)
            if (changes.notes() != null) {
                for (DeltaSyncRequest.NoteChangeDto dto : changes.notes()) {
                    UUID noteId = dto.id() != null ? dto.id() : UuidV7Generator.generate();
                    Optional<Note> existingNoteOpt = noteRepository.findByIdAndUserId(noteId, userId);

                    if (existingNoteOpt.isPresent() && existingNoteOpt.get().getDeletedAt() == null) {
                        Note existingNote = existingNoteOpt.get();
                        long serverVersion = existingNote.getVersion() != null ? existingNote.getVersion() : 1L;

                        if (dto.baseVersion() != null && dto.baseVersion() < serverVersion) {
                            boolean titleDiff = !Objects.equals(existingNote.getTitle(), dto.title());
                            boolean contentDiff = !Objects.equals(existingNote.getContent(), dto.content());
                            if (titleDiff || contentDiff) {
                                conflicts.add(DeltaSyncResponse.ConflictDto.builder()
                                        .noteId(existingNote.getId())
                                        .serverVersion(serverVersion)
                                        .cloudTitle(existingNote.getTitle())
                                        .cloudContent(existingNote.getContent())
                                        .cloudUpdatedAt(existingNote.getUpdatedAt())
                                        .build());
                                continue;
                            }
                        }

                        boolean titleChanged = dto.title() != null && !Objects.equals(existingNote.getTitle(), dto.title());
                        if (dto.title() != null) {
                            existingNote.setTitle(dto.title());
                        }
                        if (dto.content() != null) {
                            existingNote.setContent(dto.content());
                        }
                        if (dto.topicId() != null) {
                            Topic topic = topicRepository.findByIdAndUserIdAndDeletedAtIsNull(dto.topicId(), userId).orElse(null);
                            existingNote.setTopic(topic);
                        }
                        existingNote.setUpdatedAt(now);
                        noteRepository.saveAndFlush(existingNote);

                        if (titleChanged) {
                            wordContextRepository.updateNoteTitleSnapshot(existingNote.getId(), userId, existingNote.getTitle(), now);
                        }
                    } else {
                        Topic topic = dto.topicId() != null
                                ? topicRepository.findByIdAndUserIdAndDeletedAtIsNull(dto.topicId(), userId).orElse(null)
                                : null;
                        Note newNote = Note.builder()
                                .id(noteId)
                                .user(user)
                                .topic(topic)
                                .title(dto.title() != null ? dto.title() : "Untitled Note")
                                .content(dto.content())
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                        noteRepository.saveAndFlush(newNote);
                    }
                }
            }

            // 2c. Words (with Unicode normalization deduplication & idRemap)
            int newlyAddedCount = 0;
            if (changes.words() != null) {
                for (DeltaSyncRequest.WordChangeDto dto : changes.words()) {
                    if (dto.text() == null || dto.text().isBlank()) {
                        continue;
                    }
                    String norm = TextNormalizationUtil.normalizeText(dto.text());
                    if (norm.isEmpty()) {
                        continue;
                    }

                    Optional<UserWord> byNorm = userWordRepository.findByUserIdAndTextNormalizedAndDeletedAtIsNull(userId, norm);
                    if (byNorm.isPresent()) {
                        UserWord canonical = byNorm.get();
                        if (dto.id() != null && !dto.id().equals(canonical.getId())) {
                            idRemap.put(dto.id().toString(), canonical.getId().toString());
                            uuidRemap.put(dto.id(), canonical.getId());
                        }
                        if ((canonical.getPrimaryMeaning() == null || canonical.getPrimaryMeaning().isBlank()) && dto.primaryMeaning() != null) {
                            canonical.setPrimaryMeaning(dto.primaryMeaning());
                        }
                        if ((canonical.getIpa() == null || canonical.getIpa().isBlank()) && dto.ipa() != null) {
                            canonical.setIpa(dto.ipa());
                        }
                        canonical.setUpdatedAt(now);
                        userWordRepository.save(canonical);
                        touchedWordIds.add(canonical.getId());
                    } else {
                        UUID wordId = dto.id() != null ? dto.id() : UuidV7Generator.generate();
                        Optional<UserWord> byId = userWordRepository.findByIdAndUserId(wordId, userId);
                        if (byId.isPresent()) {
                            UserWord existing = byId.get();
                            existing.setText(dto.text().trim());
                            existing.setTextNormalized(norm);
                            if (dto.primaryMeaning() != null) {
                                existing.setPrimaryMeaning(dto.primaryMeaning());
                            }
                            if (dto.ipa() != null) {
                                existing.setIpa(dto.ipa());
                            }
                            existing.setDeletedAt(null);
                            existing.setUpdatedAt(now);
                            userWordRepository.save(existing);
                            touchedWordIds.add(existing.getId());
                        } else {
                            WordStatus status = WordStatus.NEW;
                            if (dto.status() != null) {
                                try {
                                    status = WordStatus.valueOf(dto.status().toUpperCase(Locale.ROOT));
                                } catch (IllegalArgumentException ignored) {
                                }
                            }
                            UserWord newWord = UserWord.builder()
                                    .id(wordId)
                                    .user(user)
                                    .text(dto.text().trim())
                                    .textNormalized(norm)
                                    .primaryMeaning(dto.primaryMeaning())
                                    .ipa(dto.ipa())
                                    .status(status)
                                    .correctStreak(dto.correctStreak() != null ? dto.correctStreak() : 0)
                                    .easeFactor(dto.easeFactor() != null ? dto.easeFactor() : 2.5)
                                    .intervalDays(dto.intervalDays() != null ? dto.intervalDays() : 0)
                                    .nextReviewAt(TimezoneUtil.parseDateOrInstant(dto.nextReviewAt(), zoneId))
                                    .failedEssayStreak(0)
                                    .createdAt(now)
                                    .updatedAt(now)
                                    .build();
                            userWordRepository.save(newWord);
                            touchedWordIds.add(newWord.getId());
                            newlyAddedCount++;
                        }
                    }
                }
            }

            if (newlyAddedCount > 0) {
                eventPublisher.publishEvent(new WordAddedEvent(userId, null, newlyAddedCount, now));
            }

            // 2d. WordContexts (remap wordId if needed)
            if (changes.wordContexts() != null) {
                for (DeltaSyncRequest.WordContextChangeDto dto : changes.wordContexts()) {
                    if (dto.wordId() == null) {
                        continue;
                    }
                    UUID targetWordId = uuidRemap.getOrDefault(dto.wordId(), dto.wordId());
                    Optional<UserWord> wordOpt = userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(targetWordId, userId);
                    if (wordOpt.isEmpty()) {
                        continue;
                    }
                    UserWord word = wordOpt.get();
                    Note note = dto.noteId() != null
                            ? noteRepository.findByIdAndUserIdAndDeletedAtIsNull(dto.noteId(), userId).orElse(null)
                            : null;
                    String snapshot = dto.noteTitleSnapshot();
                    if ((snapshot == null || snapshot.isBlank()) && note != null) {
                        snapshot = note.getTitle();
                    }

                    String normSentence = TextNormalizationUtil.normalizeText(dto.sentence());
                    UUID targetNoteId = note != null ? note.getId() : null;

                    WordContext existingCtx = null;
                    if (word.getContexts() != null) {
                        for (WordContext c : word.getContexts()) {
                            if (c.getDeletedAt() != null) continue;
                            if (dto.id() != null && dto.id().equals(c.getId())) {
                                existingCtx = c;
                                break;
                            }
                            UUID cNoteId = c.getNote() != null ? c.getNote().getId() : null;
                            String cSentNorm = TextNormalizationUtil.normalizeText(c.getSentence());
                            if (Objects.equals(targetNoteId, cNoteId) && !normSentence.isEmpty() && normSentence.equals(cSentNorm)) {
                                existingCtx = c;
                                break;
                            }
                        }
                    }

                    if (existingCtx != null) {
                        if (dto.meaning() != null) existingCtx.setMeaning(dto.meaning());
                        if (dto.sentence() != null) existingCtx.setSentence(dto.sentence());
                        if (dto.sentenceTranslation() != null) existingCtx.setSentenceTranslation(dto.sentenceTranslation());
                        if (dto.partOfSpeech() != null) existingCtx.setPartOfSpeech(dto.partOfSpeech());
                        existingCtx.setNote(note);
                        if (snapshot != null) existingCtx.setNoteTitleSnapshot(snapshot);
                        existingCtx.setUpdatedAt(now);
                        wordContextRepository.save(existingCtx);
                    } else {
                        WordContext newCtx = WordContext.builder()
                                .id(dto.id() != null ? dto.id() : UuidV7Generator.generate())
                                .user(user)
                                .userWord(word)
                                .note(note)
                                .noteTitleSnapshot(snapshot)
                                .partOfSpeech(dto.partOfSpeech())
                                .meaning(dto.meaning() != null ? dto.meaning() : (word.getPrimaryMeaning() != null ? word.getPrimaryMeaning() : ""))
                                .sentence(dto.sentence())
                                .sentenceTranslation(dto.sentenceTranslation())
                                .createdAt(now)
                                .updatedAt(now)
                                .build();
                        word.getContexts().add(newCtx);
                        wordContextRepository.save(newCtx);
                    }
                    word.setUpdatedAt(now);
                    userWordRepository.save(word);
                    touchedWordIds.add(word.getId());
                }
            }

            // 2e. Replay ReviewEvents (remap wordId if needed)
            if (changes.reviewEvents() != null && !changes.reviewEvents().isEmpty()) {
                List<ReviewSubmitRequest.ReviewEventDto> remappedEvents = changes.reviewEvents().stream()
                        .map(ev -> ReviewSubmitRequest.ReviewEventDto.builder()
                                .eventId(ev.eventId())
                                .wordId(uuidRemap.getOrDefault(ev.wordId(), ev.wordId()))
                                .quality(ev.quality())
                                .isCorrect(ev.isCorrect())
                                .mode(ev.mode())
                                .reviewedAt(ev.reviewedAt())
                                .build())
                        .toList();
                ReviewSubmitResponse reviewRes = reviewService.processReviewEvents(user, zoneId, remappedEvents);
                if (reviewRes.updatedWords() != null) {
                    reviewRes.updatedWords().forEach(uw -> touchedWordIds.add(uw.id()));
                }
            }
        }

        entityManager.flush();

        // 3. Pull Delta Changes & Tombstones since cursorInstant
        List<Topic> changedTopics;
        List<Note> changedNotes;
        List<UserWord> changedWords;
        List<WordContext> changedContexts;

        List<UUID> deletedTopicIds = List.of();
        List<UUID> deletedNoteIds = List.of();
        List<UUID> deletedWordIds = List.of();
        List<UUID> deletedContextIds = List.of();

        if (cursorInstant == null) {
            changedTopics = topicRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
            changedNotes = noteRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId);
            changedWords = userWordRepository.findAllByUserIdAndDeletedAtIsNull(userId);
            changedContexts = wordContextRepository.findAllByUserIdAndDeletedAtIsNull(userId);
        } else {
            changedTopics = topicRepository.findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(userId, cursorInstant);
            changedNotes = noteRepository.findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(userId, cursorInstant);

            Map<UUID, UserWord> wordMap = userWordRepository
                    .findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(userId, cursorInstant).stream()
                    .collect(Collectors.toMap(UserWord::getId, w -> w, (a, b) -> a, LinkedHashMap::new));
            if (!touchedWordIds.isEmpty()) {
                for (UserWord w : userWordRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(touchedWordIds, userId)) {
                    wordMap.put(w.getId(), w);
                }
            }
            changedWords = new ArrayList<>(wordMap.values());
            changedContexts = wordContextRepository.findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(userId, cursorInstant);

            deletedTopicIds = topicRepository.findAllByUserIdAndDeletedAtAfter(userId, cursorInstant).stream()
                    .map(Topic::getId).toList();
            deletedNoteIds = noteRepository.findAllByUserIdAndDeletedAtAfter(userId, cursorInstant).stream()
                    .map(Note::getId).toList();
            deletedWordIds = userWordRepository.findAllByUserIdAndDeletedAtAfter(userId, cursorInstant).stream()
                    .map(UserWord::getId).toList();
            deletedContextIds = wordContextRepository.findAllByUserIdAndDeletedAtAfter(userId, cursorInstant).stream()
                    .map(WordContext::getId).toList();
        }

        List<TopicDto> topicDtos = changedTopics.stream()
                .map(t -> TopicDto.builder()
                        .id(t.getId())
                        .name(t.getName())
                        .noteCount(noteRepository.countByTopicIdAndUserIdAndDeletedAtIsNull(t.getId(), userId))
                        .createdAt(t.getCreatedAt())
                        .updatedAt(t.getUpdatedAt())
                        .build())
                .toList();

        List<NoteDto> noteDtos = changedNotes.stream()
                .map(n -> NoteDto.builder()
                        .id(n.getId())
                        .topicId(n.getTopic() != null ? n.getTopic().getId() : null)
                        .title(n.getTitle())
                        .content(n.getContent())
                        .harvestedWordCount(wordContextRepository.countDistinctWordsByNoteId(n.getId()))
                        .version(n.getVersion() != null ? n.getVersion() : 1L)
                        .createdAt(n.getCreatedAt())
                        .updatedAt(n.getUpdatedAt())
                        .build())
                .toList();

        List<WordDto> wordDtos = changedWords.stream()
                .map(w -> wordService.toWordDto(w, zoneId))
                .toList();

        List<DeltaSyncResponse.SyncWordContextDto> contextDtos = changedContexts.stream()
                .map(c -> DeltaSyncResponse.SyncWordContextDto.builder()
                        .id(c.getId())
                        .wordId(c.getUserWord().getId())
                        .noteId(c.getNote() != null ? c.getNote().getId() : null)
                        .noteTitleSnapshot(c.getNoteTitleSnapshot())
                        .partOfSpeech(c.getPartOfSpeech())
                        .meaning(c.getMeaning())
                        .sentence(c.getSentence())
                        .sentenceTranslation(c.getSentenceTranslation())
                        .createdAt(c.getCreatedAt())
                        .updatedAt(c.getUpdatedAt())
                        .build())
                .toList();

        // 4. Update Sync Metadata & Build Counters
        Instant newCursorInstant = Instant.now();
        UserSyncMetadata metadata = metadataRepository.findByUserId(userId)
                .orElseGet(() -> UserSyncMetadata.builder().user(user).build());
        metadata.setLastSyncedAt(newCursorInstant);
        metadataRepository.save(metadata);

        Instant dueCutoff = TimezoneUtil.endOfDayInstant(TimezoneUtil.today(zoneId), zoneId);
        long dueCount = userWordRepository.countDueWords(userId, dueCutoff);
        long totalWords = userWordRepository.countByUserIdAndDeletedAtIsNull(userId);
        LocalDate today = TimezoneUtil.today(zoneId);
        int reviewedToday = dailyStatsRepository.findByUserIdAndStudyDate(userId, today)
                .map(d -> d.getWordsReviewed() != null ? d.getWordsReviewed() : 0)
                .orElse(0);
        int streakDays = metadata.getCurrentStreakDays() != null ? metadata.getCurrentStreakDays() : 0;

        return DeltaSyncResponse.builder()
                .newCursor(newCursorInstant.toString())
                .changes(DeltaSyncResponse.ChangesResult.builder()
                        .topics(topicDtos)
                        .notes(noteDtos)
                        .words(wordDtos)
                        .wordContexts(contextDtos)
                        .build())
                .tombstones(DeltaSyncResponse.TombstonesResult.builder()
                        .topicIds(deletedTopicIds)
                        .noteIds(deletedNoteIds)
                        .wordIds(deletedWordIds)
                        .contextIds(deletedContextIds)
                        .build())
                .idRemap(idRemap)
                .conflicts(conflicts)
                .counters(DeltaSyncResponse.CountersDto.builder()
                        .dueCount(dueCount)
                        .reviewedToday(reviewedToday)
                        .totalWords(totalWords)
                        .streakDays(streakDays)
                        .build())
                .build();
    }

    @Transactional
    public NoteDto resolveConflict(ResolveConflictRequest request) {
        UUID userId = getCurrentUserId();
        Note note = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(request.noteId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));

        if ("KEEP_LOCAL".equalsIgnoreCase(request.choice())) {
            Instant now = Instant.now();
            if (request.resolvedTitle() != null && !request.resolvedTitle().isBlank()) {
                note.setTitle(request.resolvedTitle());
                wordContextRepository.updateNoteTitleSnapshot(note.getId(), userId, request.resolvedTitle(), now);
            }
            if (request.resolvedContent() != null) {
                note.setContent(request.resolvedContent());
            }
            note.setUpdatedAt(now);
            note = noteRepository.saveAndFlush(note);
        }

        return NoteDto.builder()
                .id(note.getId())
                .topicId(note.getTopic() != null ? note.getTopic().getId() : null)
                .title(note.getTitle())
                .content(note.getContent())
                .harvestedWordCount(wordContextRepository.countDistinctWordsByNoteId(note.getId()))
                .version(note.getVersion() != null ? note.getVersion() : 1L)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    private Instant parseCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(cursor.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
