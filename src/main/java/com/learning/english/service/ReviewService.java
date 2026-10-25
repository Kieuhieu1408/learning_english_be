package com.learning.english.service;

import com.learning.english.dto.*;
import com.learning.english.event.ReviewSubmittedEvent;
import com.learning.english.model.ReviewLog;
import com.learning.english.model.User;
import com.learning.english.model.UserSyncMetadata;
import com.learning.english.model.UserWord;
import com.learning.english.repository.*;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.TextNormalizationUtil;
import com.learning.english.util.TimezoneUtil;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReviewService {

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private ReviewLogRepository reviewLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private WordService wordService;

    @Autowired
    private SrsCalculator srsCalculator;

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

    @Transactional(readOnly = true)
    public ReviewDueResponse getDueWords(int limit, List<UUID> excludeIds) {
        UUID userId = getCurrentUserId();
        ZoneId zoneId = wordService.getUserZoneId(userId);
        Instant dueCutoff = TimezoneUtil.endOfDayInstant(TimezoneUtil.today(zoneId), zoneId);

        long totalDue = userWordRepository.countDueWords(userId, dueCutoff);
        int safeLimit = limit > 0 ? limit : 15;

        List<UserWord> dueWords;
        if (excludeIds == null || excludeIds.isEmpty()) {
            dueWords = userWordRepository.findDueWords(userId, dueCutoff, PageRequest.of(0, safeLimit));
        } else {
            dueWords = userWordRepository.findDueWordsExcluding(userId, dueCutoff, excludeIds, PageRequest.of(0, safeLimit));
        }

        List<WordDto> wordDtos = dueWords.stream()
                .map(w -> wordService.toWordDto(w, zoneId))
                .toList();

        return ReviewDueResponse.builder()
                .totalDue(totalDue)
                .words(wordDtos)
                .build();
    }

    @Transactional
    public ReviewSubmitResponse submitReviews(ReviewSubmitRequest request) {
        User user = getCurrentUserEntity();
        ZoneId zoneId = wordService.getUserZoneId(user.getId());
        return processReviewEvents(user, zoneId, request != null ? request.events() : List.of());
    }

    @Transactional
    public ReviewSubmitResponse processReviewEvents(
            User user,
            ZoneId zoneId,
            List<ReviewSubmitRequest.ReviewEventDto> events
    ) {
        UUID userId = user.getId();
        LocalDate today = TimezoneUtil.today(zoneId);

        if (events == null || events.isEmpty()) {
            int reviewedToday = dailyStatsRepository.findByUserIdAndStudyDate(userId, today)
                    .map(d -> d.getWordsReviewed() != null ? d.getWordsReviewed() : 0)
                    .orElse(0);
            int streakDays = metadataRepository.findByUserId(userId)
                    .map(m -> m.getCurrentStreakDays() != null ? m.getCurrentStreakDays() : 0)
                    .orElse(0);
            return ReviewSubmitResponse.builder()
                    .updatedWords(List.of())
                    .reviewedToday(reviewedToday)
                    .streakDays(streakDays)
                    .build();
        }

        List<UUID> incomingEventIds = events.stream()
                .map(ReviewSubmitRequest.ReviewEventDto::eventId)
                .filter(Objects::nonNull)
                .toList();

        Set<UUID> existingEventIds = incomingEventIds.isEmpty()
                ? new HashSet<>()
                : new HashSet<>(reviewLogRepository.findExistingEventIds(incomingEventIds));

        Set<UUID> wordIds = events.stream()
                .map(ReviewSubmitRequest.ReviewEventDto::wordId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<UUID, UserWord> wordMap = wordIds.isEmpty()
                ? new HashMap<>()
                : userWordRepository.findAllByIdInAndUserIdAndDeletedAtIsNull(wordIds, userId).stream()
                        .collect(Collectors.toMap(UserWord::getId, Function.identity()));

        List<ReviewSubmitRequest.ReviewEventDto> sortedEvents = events.stream()
                .sorted(Comparator.comparing(
                        ReviewSubmitRequest.ReviewEventDto::reviewedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())
                ))
                .toList();

        Map<UUID, ReviewSubmitResponse.UpdatedWordStateDto> updatedWordsMap = new LinkedHashMap<>();
        List<ReviewLog> logsToSave = new ArrayList<>();
        List<UserWord> wordsToSave = new ArrayList<>();
        Set<UUID> modifiedWordIds = new HashSet<>();
        List<ReviewSubmittedEvent.ReviewItem> newReviewItems = new ArrayList<>();

        for (ReviewSubmitRequest.ReviewEventDto event : sortedEvents) {
            if (event.wordId() == null) {
                continue;
            }
            UserWord word = wordMap.get(event.wordId());
            if (word == null) {
                continue;
            }

            UUID eventId = event.eventId() != null ? event.eventId() : UuidV7Generator.generate();

            if (existingEventIds.contains(eventId)) {
                // Idempotent duplicate: return current canonical word state without re-applying
                updatedWordsMap.put(word.getId(), toUpdatedWordStateDto(word, zoneId));
                continue;
            }

            existingEventIds.add(eventId);

            int quality = event.quality() != null ? event.quality() : (Boolean.TRUE.equals(event.isCorrect()) ? 4 : 1);
            boolean isCorrect = event.isCorrect() != null ? event.isCorrect() : (quality >= 3);
            String normalizedMode = normalizeMode(event.mode());
            Instant reviewedAt = event.reviewedAt() != null ? event.reviewedAt() : Instant.now();

            SrsCalculator.SrsResult srsResult = srsCalculator.applyToWord(word, quality, isCorrect, reviewedAt, zoneId);
            if (modifiedWordIds.add(word.getId())) {
                wordsToSave.add(word);
            }

            ReviewLog reviewLog = ReviewLog.builder()
                    .id(UuidV7Generator.generate())
                    .eventId(eventId)
                    .user(user)
                    .userWord(word)
                    .mode(normalizedMode)
                    .quality(quality)
                    .isCorrect(isCorrect)
                    .reviewedAt(reviewedAt)
                    .build();
            logsToSave.add(reviewLog);

            newReviewItems.add(new ReviewSubmittedEvent.ReviewItem(
                    eventId,
                    word.getId(),
                    isCorrect,
                    quality,
                    reviewedAt
            ));

            updatedWordsMap.put(word.getId(), ReviewSubmitResponse.UpdatedWordStateDto.builder()
                    .id(word.getId())
                    .status(srsResult.status().name())
                    .correctStreak(srsResult.correctStreak())
                    .easeFactor(srsResult.easeFactor())
                    .intervalDays(srsResult.intervalDays())
                    .nextReviewAt(srsResult.nextReviewDateStr())
                    .build());
        }

        if (!wordsToSave.isEmpty()) {
            userWordRepository.saveAll(wordsToSave);
        }
        if (!logsToSave.isEmpty()) {
            reviewLogRepository.saveAll(logsToSave);
        }
        if (!newReviewItems.isEmpty()) {
            eventPublisher.publishEvent(new ReviewSubmittedEvent(userId, newReviewItems));
        }

        int reviewedToday = dailyStatsRepository.findByUserIdAndStudyDate(userId, today)
                .map(d -> d.getWordsReviewed() != null ? d.getWordsReviewed() : 0)
                .orElse(0);
        int streakDays = metadataRepository.findByUserId(userId)
                .map(UserSyncMetadata::getCurrentStreakDays)
                .filter(Objects::nonNull)
                .orElse(0);

        return ReviewSubmitResponse.builder()
                .updatedWords(new ArrayList<>(updatedWordsMap.values()))
                .reviewedToday(reviewedToday)
                .streakDays(streakDays)
                .build();
    }

    @Transactional(readOnly = true)
    public CheckAnswerResponse checkAnswer(CheckAnswerRequest request) {
        UUID userId = getCurrentUserId();
        UserWord word = userWordRepository.findByIdAndUserIdAndDeletedAtIsNull(request.wordId(), userId)
                .orElseThrow(() -> new IllegalArgumentException("Word not found or access denied"));

        String targetText = word.getText();
        String targetNorm = TextNormalizationUtil.normalizeText(targetText);
        String typedNorm = TextNormalizationUtil.normalizeText(request.typedAnswer());

        if (typedNorm.isEmpty()) {
            return CheckAnswerResponse.builder()
                    .outcome("INCORRECT")
                    .isAccepted(false)
                    .quality(1)
                    .feedback("No answer was provided. The target word is ‘" + targetText + "’.")
                    .build();
        }

        if (typedNorm.equals(targetNorm)) {
            return CheckAnswerResponse.builder()
                    .outcome("EXACT_MATCH")
                    .isAccepted(true)
                    .quality(5)
                    .feedback("Exact match!")
                    .build();
        }

        int distance = levenshteinDistance(typedNorm, targetNorm);
        if (distance == 1 && targetNorm.length() >= 3) {
            return CheckAnswerResponse.builder()
                    .outcome("TYPO_ACCEPTED")
                    .isAccepted(true)
                    .quality(4)
                    .feedback("Minor typo accepted! The exact spelling is ‘" + targetText + "’.")
                    .build();
        }

        // Check if it is a known contextual synonym or fits context via AI / heuristic
        if (isContextualSynonym(targetNorm, typedNorm, word.getPrimaryMeaning(), request.sentence())) {
            return CheckAnswerResponse.builder()
                    .outcome("SYNONYM_SUGGESTION")
                    .isAccepted(true)
                    .quality(3)
                    .feedback("‘" + request.typedAnswer().trim() + "’ fits grammatically in this context. However, the original card to practice is ‘" + targetText + "’.")
                    .build();
        }

        return CheckAnswerResponse.builder()
                .outcome("INCORRECT")
                .isAccepted(false)
                .quality(1)
                .feedback("‘" + request.typedAnswer().trim() + "’ does not match ‘" + targetText + "’ (" + (word.getPrimaryMeaning() != null ? word.getPrimaryMeaning() : "") + ").")
                .build();
    }

    private boolean isContextualSynonym(String targetNorm, String typedNorm, String primaryMeaning, String sentence) {
        Map<String, Set<String>> synonymGroups = Map.of(
                "linger", Set.of("stay", "remain", "wait", "loiter", "tarry", "dawdle"),
                "reluctant", Set.of("unwilling", "hesitant", "disinclined", "loath"),
                "ephemeral", Set.of("transient", "fleeting", "short-lived", "momentary", "brief"),
                "ubiquitous", Set.of("omnipresent", "pervasive", "everywhere", "universal"),
                "meticulous", Set.of("careful", "thorough", "scrupulous", "precise", "painstaking"),
                "pragmatic", Set.of("practical", "realistic", "sensible"),
                "resilient", Set.of("tough", "strong", "hardy", "adaptable"),
                "candid", Set.of("frank", "honest", "sincere", "direct", "outspoken"),
                "ambiguous", Set.of("vague", "unclear", "equivocal", "cryptic"),
                "diligent", Set.of("hardworking", "industrious", "assiduous", "conscientious")
        );

        Set<String> directSynonyms = synonymGroups.get(targetNorm);
        if (directSynonyms != null && directSynonyms.contains(typedNorm)) {
            return true;
        }
        for (Map.Entry<String, Set<String>> entry : synonymGroups.entrySet()) {
            if (entry.getValue().contains(targetNorm) && (entry.getKey().equals(typedNorm) || entry.getValue().contains(typedNorm))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "FLASHCARD";
        }
        String upper = mode.trim().toUpperCase(Locale.ROOT);
        if (upper.contains("FILL") || upper.contains("BLANK") || upper.contains("WRIT")) {
            return "FILL_BLANK";
        }
        return "FLASHCARD";
    }

    private ReviewSubmitResponse.UpdatedWordStateDto toUpdatedWordStateDto(UserWord word, ZoneId zoneId) {
        return ReviewSubmitResponse.UpdatedWordStateDto.builder()
                .id(word.getId())
                .status(word.getStatus().name())
                .correctStreak(word.getCorrectStreak() != null ? word.getCorrectStreak() : 0)
                .easeFactor(word.getEaseFactor() != null ? word.getEaseFactor() : SrsCalculator.DEFAULT_EASE_FACTOR)
                .intervalDays(word.getIntervalDays() != null ? word.getIntervalDays() : 0)
                .nextReviewAt(TimezoneUtil.formatDate(word.getNextReviewAt(), zoneId))
                .build();
    }

    public static int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];
        for (int i = 0; i <= a.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= b.length(); j++) {
            dp[0][j] = j;
        }
        for (int i = 1; i <= a.length(); i++) {
            for (int j = 1; j <= b.length(); j++) {
                int cost = (a.charAt(i - 1) == b.charAt(j - 1)) ? 0 : 1;
                dp[i][j] = Math.min(
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                        dp[i - 1][j - 1] + cost
                );
            }
        }
        return dp[a.length()][b.length()];
    }
}
