package com.learning.english.model;

import com.learning.english.constant.WordStatus;
import com.learning.english.util.UuidV7Generator;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "user_words")
@Getter
@Setter
@NoArgsConstructor
public class UserWord implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String text;

    @Column(name = "text_normalized", nullable = false)
    private String textNormalized;

    @Column(name = "primary_meaning", columnDefinition = "TEXT")
    private String primaryMeaning;

    @Column
    private String ipa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WordStatus status = WordStatus.NEW;

    @Column(name = "correct_streak", nullable = false)
    private Integer correctStreak = 0;

    @Column(name = "ease_factor", nullable = false)
    private Double easeFactor = 2.5;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 0;

    @Column(name = "next_review_at")
    private Instant nextReviewAt;

    @Column(name = "failed_essay_streak", nullable = false)
    private Integer failedEssayStreak = 0;

    @Version
    private Long version;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "userWord", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WordContext> contexts = new ArrayList<>();

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
    public UserWord(UUID id, User user, String text, String textNormalized, String primaryMeaning,
                    String ipa, WordStatus status, Integer correctStreak, Double easeFactor,
                    Integer intervalDays, Instant nextReviewAt, Integer failedEssayStreak,
                    Long version, Instant deletedAt, Instant createdAt, Instant updatedAt,
                    List<WordContext> contexts) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.user = user;
        this.text = text;
        this.textNormalized = textNormalized;
        this.primaryMeaning = primaryMeaning;
        this.ipa = ipa;
        this.status = status != null ? status : WordStatus.NEW;
        this.correctStreak = correctStreak != null ? correctStreak : 0;
        this.easeFactor = easeFactor != null ? easeFactor : 2.5;
        this.intervalDays = intervalDays != null ? intervalDays : 0;
        this.nextReviewAt = nextReviewAt;
        this.failedEssayStreak = failedEssayStreak != null ? failedEssayStreak : 0;
        this.version = version;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        if (contexts != null) {
            this.contexts = contexts;
        }
    }
}
