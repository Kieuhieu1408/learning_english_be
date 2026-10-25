package com.learning.english.service;

import com.learning.english.constant.WordStatus;
import com.learning.english.dto.ProgressDashboardResponse;
import com.learning.english.model.DailyStats;
import com.learning.english.model.UserAppConfig;
import com.learning.english.model.UserSyncMetadata;
import com.learning.english.repository.*;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.TimezoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ProgressService {

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private UserWordRepository userWordRepository;

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

    @Transactional(readOnly = true)
    public ProgressDashboardResponse getDashboard() {
        UUID userId = getCurrentUserId();

        UserAppConfig appConfig = userAppConfigRepository.findByUserId(userId).orElse(null);
        ZoneId zoneId = TimezoneUtil.parseZoneId(appConfig != null ? appConfig.getTimezone() : TimezoneUtil.DEFAULT_TIMEZONE);
        int dailyTarget = (appConfig != null && appConfig.getDailyTarget() != null) ? appConfig.getDailyTarget() : 20;

        LocalDate today = TimezoneUtil.today(zoneId);
        LocalDate startDate35 = today.minusDays(34);
        LocalDate startDate30 = today.minusDays(29);

        List<DailyStats> statsList = dailyStatsRepository.findStatsBetweenDates(userId, startDate35, today);
        Map<LocalDate, DailyStats> statsByDate = statsList.stream()
                .collect(Collectors.toMap(DailyStats::getStudyDate, Function.identity(), (a, b) -> a));

        // 1. Build 35-day Activity Calendar (today - 34 days .. today)
        List<ProgressDashboardResponse.ActivityDayDto> activityCalendar = new ArrayList<>(35);
        int totalCorrect30d = 0;
        int totalIncorrect30d = 0;

        for (int i = 0; i < 35; i++) {
            LocalDate date = startDate35.plusDays(i);
            DailyStats dayStat = statsByDate.get(date);
            int reviewed = (dayStat != null && dayStat.getWordsReviewed() != null) ? dayStat.getWordsReviewed() : 0;
            int added = (dayStat != null && dayStat.getWordsAdded() != null) ? dayStat.getWordsAdded() : 0;
            boolean filled = reviewed > 0 || added > 0;
            boolean isToday = date.equals(today);

            activityCalendar.add(ProgressDashboardResponse.ActivityDayDto.builder()
                    .date(date.toString())
                    .count(reviewed)
                    .wordsAdded(added)
                    .filled(filled)
                    .isToday(isToday)
                    .build());

            if (!date.isBefore(startDate30) && dayStat != null) {
                totalCorrect30d += (dayStat.getCorrectCount() != null ? dayStat.getCorrectCount() : 0);
                totalIncorrect30d += (dayStat.getIncorrectCount() != null ? dayStat.getIncorrectCount() : 0);
            }
        }

        int totalAttempts30d = totalCorrect30d + totalIncorrect30d;
        int accuracyPercent = totalAttempts30d > 0
                ? (int) Math.round((totalCorrect30d * 100.0) / totalAttempts30d)
                : 0;

        DailyStats todayStats = statsByDate.get(today);
        int reviewedToday = (todayStats != null && todayStats.getWordsReviewed() != null) ? todayStats.getWordsReviewed() : 0;
        int wordsAddedToday = (todayStats != null && todayStats.getWordsAdded() != null) ? todayStats.getWordsAdded() : 0;

        // 2. Distribution counts
        long newCount = userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.NEW);
        long learningCount = userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.LEARNING);
        long rememberedCount = userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.TEMP_REMEMBERED);
        long masteredCount = userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(userId, WordStatus.MASTERED);
        long totalWords = newCount + learningCount + rememberedCount + masteredCount;

        // 3. Streak days
        UserSyncMetadata metadata = metadataRepository.findByUserId(userId).orElse(null);
        int streakDays = (metadata != null && metadata.getCurrentStreakDays() != null) ? metadata.getCurrentStreakDays() : 0;
        int longestStreakDays = (metadata != null && metadata.getLongestStreakDays() != null) ? metadata.getLongestStreakDays() : 0;

        // Check if streak is still active as of yesterday/today in user's timezone
        if (metadata != null && metadata.getLastActiveDate() != null) {
            if (metadata.getLastActiveDate().isBefore(today.minusDays(1))) {
                streakDays = 0;
            }
        }

        return ProgressDashboardResponse.builder()
                .accuracyPercent(accuracyPercent)
                .reviewedToday(reviewedToday)
                .dailyTarget(dailyTarget)
                .wordsAddedToday(wordsAddedToday)
                .totalWords(totalWords)
                .streakDays(streakDays)
                .longestStreakDays(longestStreakDays)
                .distribution(ProgressDashboardResponse.DistributionDto.builder()
                        .newWords(newCount)
                        .learning(learningCount)
                        .remembered(rememberedCount)
                        .mastered(masteredCount)
                        .build())
                .activityCalendar(activityCalendar)
                .build();
    }
}
