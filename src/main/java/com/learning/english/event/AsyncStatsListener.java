package com.learning.english.event;

import com.learning.english.constant.WordStatus;
import com.learning.english.model.DailyStats;
import com.learning.english.model.User;
import com.learning.english.model.UserSyncMetadata;
import com.learning.english.repository.*;
import com.learning.english.util.TimezoneUtil;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

@Component
public class AsyncStatsListener {

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private UserRepository userRepository;

    @EventListener
    @Transactional
    public void handleWordAdded(WordAddedEvent event) {
        if (event == null || event.userId() == null || event.count() <= 0) {
            return;
        }
        UUID userId = event.userId();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        ZoneId zoneId = getUserZone(userId);
        LocalDate studyDate = event.occurredAt() != null
                ? event.occurredAt().atZone(zoneId).toLocalDate()
                : TimezoneUtil.today(zoneId);

        DailyStats stats = dailyStatsRepository.findByUserIdAndStudyDate(userId, studyDate)
                .orElseGet(() -> DailyStats.builder()
                        .id(UuidV7Generator.generate())
                        .user(user)
                        .studyDate(studyDate)
                        .wordsReviewed(0)
                        .wordsAdded(0)
                        .correctCount(0)
                        .incorrectCount(0)
                        .timeSpentSeconds(0)
                        .build());

        stats.setWordsAdded((stats.getWordsAdded() != null ? stats.getWordsAdded() : 0) + event.count());
        dailyStatsRepository.save(stats);

        updateMetadataAndStreak(user, studyDate);
    }

    @EventListener
    @Transactional
    public void handleReviewSubmitted(ReviewSubmittedEvent event) {
        if (event == null || event.userId() == null || event.items() == null || event.items().isEmpty()) {
            return;
        }
        UUID userId = event.userId();
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return;
        }
        User user = userOpt.get();
        ZoneId zoneId = getUserZone(userId);

        Map<LocalDate, List<ReviewSubmittedEvent.ReviewItem>> groupedByDate = new TreeMap<>();
        for (ReviewSubmittedEvent.ReviewItem item : event.items()) {
            LocalDate date = item.reviewedAt() != null
                    ? item.reviewedAt().atZone(zoneId).toLocalDate()
                    : TimezoneUtil.today(zoneId);
            groupedByDate.computeIfAbsent(date, k -> new ArrayList<>()).add(item);
        }

        for (Map.Entry<LocalDate, List<ReviewSubmittedEvent.ReviewItem>> entry : groupedByDate.entrySet()) {
            LocalDate studyDate = entry.getKey();
            List<ReviewSubmittedEvent.ReviewItem> dayItems = entry.getValue();

            int reviewedDelta = dayItems.size();
            int correctDelta = (int) dayItems.stream().filter(ReviewSubmittedEvent.ReviewItem::isCorrect).count();
            int incorrectDelta = reviewedDelta - correctDelta;

            DailyStats stats = dailyStatsRepository.findByUserIdAndStudyDate(userId, studyDate)
                    .orElseGet(() -> DailyStats.builder()
                            .id(UuidV7Generator.generate())
                            .user(user)
                            .studyDate(studyDate)
                            .wordsReviewed(0)
                            .wordsAdded(0)
                            .correctCount(0)
                            .incorrectCount(0)
                            .timeSpentSeconds(0)
                            .build());

            stats.setWordsReviewed((stats.getWordsReviewed() != null ? stats.getWordsReviewed() : 0) + reviewedDelta);
            stats.setCorrectCount((stats.getCorrectCount() != null ? stats.getCorrectCount() : 0) + correctDelta);
            stats.setIncorrectCount((stats.getIncorrectCount() != null ? stats.getIncorrectCount() : 0) + incorrectDelta);
            dailyStatsRepository.save(stats);

            updateMetadataAndStreak(user, studyDate);
        }
    }

    private void updateMetadataAndStreak(User user, LocalDate activityDate) {
        UUID userId = user.getId();
        UserSyncMetadata metadata = metadataRepository.findByUserId(userId)
                .orElseGet(() -> UserSyncMetadata.builder()
                        .user(user)
                        .dailyWordCount(0)
                        .totalWordCount(0)
                        .build());

        LocalDate lastActive = metadata.getLastActiveDate();
        int currentStreak = metadata.getCurrentStreakDays() != null ? metadata.getCurrentStreakDays() : 0;
        int longestStreak = metadata.getLongestStreakDays() != null ? metadata.getLongestStreakDays() : 0;

        if (lastActive == null) {
            currentStreak = 1;
            lastActive = activityDate;
        } else if (activityDate.equals(lastActive)) {
            if (currentStreak == 0) {
                currentStreak = 1;
            }
        } else if (activityDate.equals(lastActive.plusDays(1))) {
            currentStreak += 1;
            lastActive = activityDate;
        } else if (activityDate.isAfter(lastActive.plusDays(1))) {
            currentStreak = 1;
            lastActive = activityDate;
        }

        longestStreak = Math.max(longestStreak, currentStreak);
        metadata.setCurrentStreakDays(currentStreak);
        metadata.setLongestStreakDays(longestStreak);
        metadata.setLastActiveDate(lastActive);

        // Sync distribution counters
        int newCount = (int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.NEW);
        int learningCount = (int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.LEARNING);
        int rememberedCount = (int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.TEMP_REMEMBERED);
        int masteredCount = (int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.MASTERED);
        int totalWords = newCount + learningCount + rememberedCount + masteredCount;

        metadata.setNewCount(newCount);
        metadata.setLearningCount(learningCount);
        metadata.setRememberedCount(rememberedCount);
        metadata.setMasteredCount(masteredCount);
        metadata.setTotalWordCount(totalWords);

        metadataRepository.save(metadata);
    }

    private ZoneId getUserZone(UUID userId) {
        return userAppConfigRepository.findByUserId(userId)
                .map(c -> TimezoneUtil.parseZoneId(c.getTimezone()))
                .orElseGet(() -> TimezoneUtil.parseZoneId(TimezoneUtil.DEFAULT_TIMEZONE));
    }
}
