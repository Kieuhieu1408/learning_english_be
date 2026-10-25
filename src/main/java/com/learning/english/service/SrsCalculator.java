package com.learning.english.service;

import com.learning.english.constant.WordStatus;
import com.learning.english.model.UserWord;
import com.learning.english.util.TimezoneUtil;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Spaced Repetition System (SRS) Calculator following the v2.1.0 specification:
 * - Fixed initial intervals: 1 -> 3 -> 7 -> 14 days, then round(interval * easeFactor)
 * - EaseFactor: default 2.5, min 1.3; forgot (quality < 3) -> -0.2; quality == 3 -> -0.15; quality >= 4 -> unchanged
 * - Status thresholds:
 *   - NEW: never reviewed
 *   - LEARNING: reviewed >= 1 time, correctStreak 0..1
 *   - TEMP_REMEMBERED: correctStreak 2..4
 *   - MASTERED: correctStreak >= 5 AND intervalDays >= 14
 */
@Component
public class SrsCalculator {

    public static final double DEFAULT_EASE_FACTOR = 2.5;
    public static final double MIN_EASE_FACTOR = 1.3;

    public record SrsResult(
            WordStatus status,
            int correctStreak,
            double easeFactor,
            int intervalDays,
            Instant nextReviewAt,
            String nextReviewDateStr
    ) {}

    public SrsResult calculate(UserWord word, int quality, Boolean isCorrectFlag, Instant reviewedAt, ZoneId zoneId) {
        ZoneId z = zoneId != null ? zoneId : ZoneId.of(TimezoneUtil.DEFAULT_TIMEZONE);
        LocalDate reviewDate = reviewedAt != null
                ? reviewedAt.atZone(z).toLocalDate()
                : TimezoneUtil.today(z);
        LocalDate today = TimezoneUtil.today(z);
        LocalDate baseDate = reviewDate.isBefore(today) ? reviewDate : today;

        int currentStreak = word.getCorrectStreak() != null ? word.getCorrectStreak() : 0;
        double currentEase = word.getEaseFactor() != null ? word.getEaseFactor() : DEFAULT_EASE_FACTOR;
        int currentInterval = word.getIntervalDays() != null ? word.getIntervalDays() : 0;

        boolean remembered = (isCorrectFlag != null) ? isCorrectFlag : (quality >= 3);
        int effectiveQuality = quality > 0 ? quality : (remembered ? 4 : 1);
        if (!remembered && effectiveQuality >= 3) {
            effectiveQuality = 1;
        }

        int newStreak;
        double newEase;
        int newInterval;
        WordStatus newStatus;

        if (effectiveQuality < 3) {
            // Forgot
            newStreak = 0;
            newInterval = 1;
            newEase = Math.max(MIN_EASE_FACTOR, Math.round((currentEase - 0.2) * 100.0) / 100.0);
            newStatus = WordStatus.LEARNING;
        } else {
            // Remembered
            newStreak = currentStreak + 1;
            if (effectiveQuality == 3) {
                newEase = Math.max(MIN_EASE_FACTOR, Math.round((currentEase - 0.15) * 100.0) / 100.0);
            } else {
                newEase = Math.max(MIN_EASE_FACTOR, currentEase);
            }

            newInterval = switch (newStreak) {
                case 1 -> 1;
                case 2 -> 3;
                case 3 -> 7;
                case 4 -> 14;
                default -> {
                    int baseInterval = Math.max(14, currentInterval);
                    yield Math.max(14, (int) Math.round(baseInterval * newEase));
                }
            };

            if (newStreak <= 1) {
                newStatus = WordStatus.LEARNING;
            } else if (newStreak <= 4) {
                newStatus = WordStatus.TEMP_REMEMBERED;
            } else if (newInterval >= 14) {
                newStatus = WordStatus.MASTERED;
            } else {
                newStatus = WordStatus.TEMP_REMEMBERED;
            }
        }

        LocalDate nextDate = baseDate.plusDays(newInterval);
        Instant nextReviewAt = TimezoneUtil.startOfDayInstant(nextDate, z);

        return new SrsResult(
                newStatus,
                newStreak,
                newEase,
                newInterval,
                nextReviewAt,
                nextDate.toString()
        );
    }

    public SrsResult applyToWord(UserWord word, int quality, Boolean isCorrectFlag, Instant reviewedAt, ZoneId zoneId) {
        SrsResult result = calculate(word, quality, isCorrectFlag, reviewedAt, zoneId);
        word.setStatus(result.status());
        word.setCorrectStreak(result.correctStreak());
        word.setEaseFactor(result.easeFactor());
        word.setIntervalDays(result.intervalDays());
        word.setNextReviewAt(result.nextReviewAt());
        word.setUpdatedAt(Instant.now());
        return result;
    }
}
