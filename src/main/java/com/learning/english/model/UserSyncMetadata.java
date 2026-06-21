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

    @Version
    private Long version;

    @Builder
    public UserSyncMetadata(UUID id, User user, Integer dailyWordCount, Integer totalWordCount, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.dailyWordCount = dailyWordCount != null ? dailyWordCount : 0;
        this.totalWordCount = totalWordCount != null ? totalWordCount : 0;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
