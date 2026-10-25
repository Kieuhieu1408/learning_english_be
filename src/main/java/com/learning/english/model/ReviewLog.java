package com.learning.english.model;

import com.learning.english.util.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "review_logs")
@Getter
@Setter
@NoArgsConstructor
public class ReviewLog implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @Column(name = "event_id", nullable = false, unique = true)
    private UUID eventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_word_id", nullable = false)
    private UserWord userWord;

    @Column(nullable = false, length = 50)
    private String mode;

    @Column(nullable = false)
    private Integer quality;

    @Column(name = "is_correct", nullable = false)
    private Boolean isCorrect;

    @Column(name = "reviewed_at", nullable = false)
    private Instant reviewedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Override
    public boolean isNew() {
        return isNew || id == null;
    }

    @PostLoad
    @PrePersist
    void markNotNew() {
        this.isNew = false;
    }

    @Builder
    public ReviewLog(UUID id, UUID eventId, User user, UserWord userWord, String mode,
                     Integer quality, Boolean isCorrect, Instant reviewedAt, Instant createdAt) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.eventId = eventId != null ? eventId : this.id;
        this.user = user;
        this.userWord = userWord;
        this.mode = mode;
        this.quality = quality;
        this.isCorrect = isCorrect != null ? isCorrect : false;
        this.reviewedAt = reviewedAt != null ? reviewedAt : Instant.now();
        this.createdAt = createdAt;
    }
}
