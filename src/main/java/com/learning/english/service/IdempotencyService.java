package com.learning.english.service;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableScheduling
public class IdempotencyService {

    private static class CacheEntry {
        Object response;
        Instant expireAt;

        CacheEntry(Object response, Instant expireAt) {
            this.response = response;
            this.expireAt = expireAt;
        }
    }

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public boolean has(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return false;
        if (Instant.now().isAfter(entry.expireAt)) {
            cache.remove(key);
            return false;
        }
        return true;
    }

    public Object get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry == null) return null;
        if (Instant.now().isAfter(entry.expireAt)) {
            cache.remove(key);
            return null;
        }
        return entry.response;
    }

    public void put(String key, Object response, long ttlSeconds) {
        cache.put(key, new CacheEntry(response, Instant.now().plusSeconds(ttlSeconds)));
    }

    @Scheduled(fixedRate = 60000) // Clean up expired entries every 1 minute
    public void cleanExpiredEntries() {
        Instant now = Instant.now();
        cache.forEach((key, entry) -> {
            if (now.isAfter(entry.expireAt)) {
                cache.remove(key);
            }
        });
    }
}
