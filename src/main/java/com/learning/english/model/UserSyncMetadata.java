package com.learning.english.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sync_metadata")
@Getter
@Setter
@NoArgsConstructor
public class UserSyncMetadata extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "daily_word_count", nullable = false)
    private Integer dailyWordCount = 0;

    @Column(name = "total_word_count", nullable = false)
    private Integer totalWordCount = 0;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "client_version", length = 50)
    private String clientVersion;

    @Column(name = "new_count", nullable = false)
    private Integer newCount = 0;

    @Column(name = "learning_count", nullable = false)
    private Integer learningCount = 0;

    @Column(name = "remembered_count", nullable = false)
    private Integer rememberedCount = 0;

    @Column(name = "mastered_count", nullable = false)
    private Integer masteredCount = 0;

    @Column(name = "current_streak_days", nullable = false)
    private Integer currentStreakDays = 0;

    @Column(name = "longest_streak_days", nullable = false)
    private Integer longestStreakDays = 0;

    @Column(name = "last_active_date")
    private java.time.LocalDate lastActiveDate;

    @Version
    private Long version;

    @Builder
    public UserSyncMetadata(UUID id, User user, Integer dailyWordCount, Integer totalWordCount,
                            Instant lastSyncedAt, String clientVersion,
                            Integer newCount, Integer learningCount, Integer rememberedCount, Integer masteredCount,
                            Integer currentStreakDays, Integer longestStreakDays, java.time.LocalDate lastActiveDate,
                            Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.dailyWordCount = dailyWordCount != null ? dailyWordCount : 0;
        this.totalWordCount = totalWordCount != null ? totalWordCount : 0;
        this.lastSyncedAt = lastSyncedAt;
        this.clientVersion = clientVersion;
        this.newCount = newCount != null ? newCount : 0;
        this.learningCount = learningCount != null ? learningCount : 0;
        this.rememberedCount = rememberedCount != null ? rememberedCount : 0;
        this.masteredCount = masteredCount != null ? masteredCount : 0;
        this.currentStreakDays = currentStreakDays != null ? currentStreakDays : 0;
        this.longestStreakDays = longestStreakDays != null ? longestStreakDays : 0;
        this.lastActiveDate = lastActiveDate;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
