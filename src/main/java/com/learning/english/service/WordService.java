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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class WordService {

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

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

    public ZoneId getUserZoneId(UUID userId) {
        return userAppConfigRepository.findByUserId(userId)
                .map(cfg -> TimezoneUtil.parseZoneId(cfg.getTimezone()))
                .orElseGet(() -> TimezoneUtil.parseZoneId(TimezoneUtil.DEFAULT_TIMEZONE));
    }

    @Transactional(readOnly = true)
    public WordListResponse getWords(String q, String statusStr, String sortBy, int limit, int offset) {
        UUID userId = getCurrentUserId();
        ZoneId zoneId = getUserZoneId(userId);

        WordStatus status = null;
        if (statusStr != null && !statusStr.isBlank() && !"ALL".equalsIgnoreCase(statusStr.trim())) {
            try {
                status = WordStatus.valueOf(statusStr.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
            }
        }

        String searchQuery = (q != null && !q.isBlank()) ? q.trim() : null;
        List<UserWord> matchedWords = userWordRepository.searchWords(userId, searchQuery, status);

        Comparator<UserWord> comparator;
        if ("word".equalsIgnoreCase(sortBy) || "alphabetical".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    (UserWord w) -> w.getTextNormalized() != null ? w.getTextNormalized() : "",
                    String.CASE_INSENSITIVE_ORDER
            );
        } else if ("review".equalsIgnoreCase(sortBy) || "due".equalsIgnoreCase(sortBy)) {
            comparator = Comparator.comparing(
                    UserWord::getNextReviewAt,
                    Comparator.nullsFirst(Comparator.naturalOrder())
            );
        } else {
            comparator = Comparator.comparing(
                    UserWord::getCreatedAt,
                    Comparator.nullsLast(Comparator.reverseOrder())
            );
        }

        List<UserWord> sortedWords = matchedWords.stream()
                .sorted(comparator)
                .toList();

        long total = sortedWords.size();
        int safeOffset = Math.max(0, offset);
        int safeLimit = limit > 0 ? limit : 50;

        List<WordDto> pagedWords = sortedWords.stream()
                .skip(safeOffset)
                .limit(safeLimit)
                .map(w -> toWordDto(w, zoneId))
                .toList();

        return WordListResponse.builder()
                .total(total)
                .words(pagedWords)
                .build();
    }

    @Transactional
    public SaveWordContextResponse saveWordContext(SaveWordContextRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        ZoneId zoneId = getUserZoneId(userId);

        String normalized = TextNormalizationUtil.normalizeText(request.text());
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException("Normalized word text cannot be empty");
        }

        Optional<UserWord> existingOpt = userWordRepository.findByUserIdAndTextNormalizedAndDeletedAtIsNull(userId, normalized);
        if (existingOpt.isEmpty() && request.wordId() != null) {
            existingOpt = userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(request.wordId(), userId);
        }

        boolean isNewWord;
        UserWord word;
        Instant now = Instant.now();

        if (existingOpt.isPresent()) {
            word = existingOpt.get();
            isNewWord = false;
            if ((word.getPrimaryMeaning() == null || word.getPrimaryMeaning().isBlank()) && request.meaning() != null) {
                word.setPrimaryMeaning(request.meaning());
            }
            if ((word.getIpa() == null || word.getIpa().isBlank()) && request.ipa() != null) {
                word.setIpa(request.ipa());
            }
            word.setUpdatedAt(now);
        } else {
            isNewWord = true;
            Instant todayStart = TimezoneUtil.startOfDayInstant(TimezoneUtil.today(zoneId), zoneId);
            word = UserWord.builder()
                    .id(request.wordId() != null ? request.wordId() : UuidV7Generator.generate())
                    .user(user)
                    .text(request.text().trim())
                    .textNormalized(normalized)
                    .primaryMeaning(request.meaning())
                    .ipa(request.ipa())
                    .status(WordStatus.NEW)
                    .correctStreak(0)
                    .easeFactor(2.5)
                    .intervalDays(0)
                    .nextReviewAt(todayStart)
                    .failedEssayStreak(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }

        Note note = null;
        if (request.noteId() != null) {
            note = noteRepository.findByIdAndUserId(request.noteId(), userId)
                    .filter(n -> n.getDeletedAt() == null)
                    .orElse(null);
        }

        String snapshot = request.noteTitleSnapshot();
        if ((snapshot == null || snapshot.isBlank()) && note != null) {
            snapshot = note.getTitle();
        }

        String normalizedSentence = TextNormalizationUtil.normalizeText(request.sentence());
        UUID targetNoteId = note != null ? note.getId() : null;

        WordContext targetContext = null;
        if (word.getContexts() != null) {
            for (WordContext ctx : word.getContexts()) {
                if (ctx.getDeletedAt() != null) {
                    continue;
                }
                if (request.contextId() != null && request.contextId().equals(ctx.getId())) {
                    targetContext = ctx;
                    break;
                }
                UUID ctxNoteId = ctx.getNote() != null ? ctx.getNote().getId() : null;
                String ctxSentenceNorm = TextNormalizationUtil.normalizeText(ctx.getSentence());
                if (Objects.equals(targetNoteId, ctxNoteId)
                        && !normalizedSentence.isEmpty()
                        && normalizedSentence.equals(ctxSentenceNorm)) {
                    targetContext = ctx;
                    break;
                }
            }
        }

        if (targetContext != null) {
            targetContext.setMeaning(request.meaning());
            targetContext.setSentence(request.sentence());
            targetContext.setSentenceTranslation(request.sentenceTranslation());
            targetContext.setPartOfSpeech(request.partOfSpeech());
            targetContext.setNote(note);
            if (snapshot != null) {
                targetContext.setNoteTitleSnapshot(snapshot);
            }
            targetContext.setUpdatedAt(now);
        } else {
            targetContext = WordContext.builder()
                    .id(request.contextId() != null ? request.contextId() : UuidV7Generator.generate())
                    .user(user)
                    .userWord(word)
                    .note(note)
                    .noteTitleSnapshot(snapshot)
                    .partOfSpeech(request.partOfSpeech())
                    .meaning(request.meaning())
                    .sentence(request.sentence())
                    .sentenceTranslation(request.sentenceTranslation())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
            word.getContexts().add(targetContext);
        }

        UserWord savedWord = userWordRepository.save(word);

        if (isNewWord) {
            eventPublisher.publishEvent(new WordAddedEvent(userId, savedWord.getId(), 1, now));
        }

        return SaveWordContextResponse.builder()
                .word(toWordDto(savedWord, zoneId))
                .isNewWord(isNewWord)
                .context(toContextDto(targetContext))
                .build();
    }

    @Transactional
    public WordDto updateWord(UUID wordId, UpdateWordRequest request) {
        UUID userId = getCurrentUserId();
        ZoneId zoneId = getUserZoneId(userId);

        UserWord word = userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(wordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Word not found or access denied"));

        if (request.text() != null && !request.text().isBlank()) {
            String normalized = TextNormalizationUtil.normalizeText(request.text());
            if (!normalized.isEmpty()) {
                word.setText(request.text().trim());
                word.setTextNormalized(normalized);
            }
        }
        if (request.primaryMeaning() != null) {
            word.setPrimaryMeaning(request.primaryMeaning());
        }
        if (request.ipa() != null) {
            word.setIpa(request.ipa());
        }
        word.setUpdatedAt(Instant.now());

        UserWord saved = userWordRepository.save(word);
        return toWordDto(saved, zoneId);
    }

    @Transactional
    public void deleteWord(UUID wordId) {
        UUID userId = getCurrentUserId();
        UserWord word = userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(wordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Word not found or access denied"));

        Instant now = Instant.now();
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
    }

    @Transactional
    public void deleteWordContext(UUID wordId, UUID contextId) {
        UUID userId = getCurrentUserId();
        WordContext ctx = wordContextRepository.findByIdAndUserWordIdAndUserIdAndDeletedAtIsNull(contextId, wordId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Word context not found or access denied"));

        Instant now = Instant.now();
        ctx.setDeletedAt(now);
        ctx.setUpdatedAt(now);
        wordContextRepository.save(ctx);
    }

    public WordDto toWordDto(UserWord word, ZoneId zoneId) {
        List<WordContextDto> activeContexts = word.getContexts() == null ? List.of() :
                word.getContexts().stream()
                        .filter(c -> c.getDeletedAt() == null)
                        .sorted(Comparator.comparing(WordContext::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                        .map(this::toContextDto)
                        .collect(Collectors.toList());

        return WordDto.builder()
                .id(word.getId())
                .text(word.getText())
                .primaryMeaning(word.getPrimaryMeaning())
                .ipa(word.getIpa())
                .status(word.getStatus().name())
                .correctStreak(word.getCorrectStreak())
                .easeFactor(word.getEaseFactor())
                .intervalDays(word.getIntervalDays())
                .nextReviewAt(TimezoneUtil.formatDate(word.getNextReviewAt(), zoneId))
                .contextsCount(activeContexts.size())
                .contexts(activeContexts)
                .createdAt(word.getCreatedAt())
                .updatedAt(word.getUpdatedAt())
                .build();
    }

    public WordContextDto toContextDto(WordContext ctx) {
        UUID noteId = ctx.getNote() != null ? ctx.getNote().getId() : null;
        return WordContextDto.builder()
                .id(ctx.getId())
                .noteId(noteId)
                .noteTitle(ctx.getNoteTitleSnapshot())
                .sentence(ctx.getSentence())
                .sentenceTranslation(ctx.getSentenceTranslation())
                .meaning(ctx.getMeaning())
                .partOfSpeech(ctx.getPartOfSpeech())
                .createdAt(ctx.getCreatedAt())
                .build();
    }
}
