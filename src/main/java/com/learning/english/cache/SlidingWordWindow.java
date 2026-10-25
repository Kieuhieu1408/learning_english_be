package com.learning.english.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.learning.english.dto.WordDto;
import com.learning.english.model.UserWord;
import com.learning.english.repository.UserWordRepository;
import com.learning.english.service.WordService;
import com.learning.english.util.TimezoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * 3-Slot Sliding Word Window Cache (max 45 due words = 3 batches of 15 words per user).
 * Pre-warms in background via Virtual Threads upon user authentication / bootstrap.
 */
@Component
public class SlidingWordWindow {

    public static final int WINDOW_CAPACITY = 45;

    private final Cache<UUID, List<WordDto>> dueWindowCache = Caffeine.newBuilder()
            .maximumSize(5_000)
            .expireAfterWrite(Duration.ofMinutes(15))
            .build();

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private WordService wordService;

    @Async
    @Transactional(readOnly = true)
    public void preWarmAsync(UUID userId) {
        warmCacheSync(userId);
    }

    @Transactional(readOnly = true)
    public List<WordDto> warmCacheSync(UUID userId) {
        if (userId == null) {
            return List.of();
        }
        ZoneId zoneId = wordService.getUserZoneId(userId);
        Instant dueCutoff = TimezoneUtil.endOfDayInstant(TimezoneUtil.today(zoneId), zoneId);

        List<UserWord> topDue = userWordRepository.findDueWords(
                userId,
                dueCutoff,
                PageRequest.of(0, WINDOW_CAPACITY)
        );

        List<WordDto> dtos = topDue.stream()
                .limit(WINDOW_CAPACITY)
                .map(w -> wordService.toWordDto(w, zoneId))
                .toList();

        dueWindowCache.put(userId, Collections.unmodifiableList(dtos));
        return dtos;
    }

    public List<WordDto> getWindow(UUID userId) {
        if (userId == null) {
            return null;
        }
        return dueWindowCache.getIfPresent(userId);
    }

    public void evict(UUID userId) {
        if (userId != null) {
            dueWindowCache.invalidate(userId);
        }
    }
}
