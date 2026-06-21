package com.learning.english.model;

import jakarta.persistence.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_sync_metadata")
public class UserSyncMetadata {

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

    @Version
    private Long version;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public UserSyncMetadata() {}

    public UserSyncMetadata(UUID id, User user, Integer dailyWordCount, Integer totalWordCount, Long version, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.dailyWordCount = dailyWordCount != null ? dailyWordCount : 0;
        this.totalWordCount = totalWordCount != null ? totalWordCount : 0;
        this.version = version;
        this.updatedAt = updatedAt;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Integer getDailyWordCount() {
        return dailyWordCount;
    }

    public void setDailyWordCount(Integer dailyWordCount) {
        this.dailyWordCount = dailyWordCount;
    }

    public Integer getTotalWordCount() {
        return totalWordCount;
    }

    public void setTotalWordCount(Integer totalWordCount) {
        this.totalWordCount = totalWordCount;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Manual Builder
    public static UserSyncMetadataBuilder builder() {
        return new UserSyncMetadataBuilder();
    }

    public static class UserSyncMetadataBuilder {
        private UUID id;
        private User user;
        private Integer dailyWordCount = 0;
        private Integer totalWordCount = 0;
        private Long version;
        private Instant updatedAt;

        public UserSyncMetadataBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public UserSyncMetadataBuilder user(User user) {
            this.user = user;
            return this;
        }

        public UserSyncMetadataBuilder dailyWordCount(Integer dailyWordCount) {
            this.dailyWordCount = dailyWordCount;
            return this;
        }

        public UserSyncMetadataBuilder totalWordCount(Integer totalWordCount) {
            this.totalWordCount = totalWordCount;
            return this;
        }

        public UserSyncMetadataBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public UserSyncMetadataBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public UserSyncMetadata build() {
            return new UserSyncMetadata(id, user, dailyWordCount, totalWordCount, version, updatedAt);
        }
    }
}
