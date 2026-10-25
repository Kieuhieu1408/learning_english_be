package com.learning.english.model;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "daily_stats", uniqueConstraints = {
        @UniqueConstraint(name = "uq_daily_stats_user_date", columnNames = {"user_id", "study_date"})
})
@Getter
@Setter
@NoArgsConstructor
public class DailyStats implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "study_date", nullable = false)
    private LocalDate studyDate;

    @Column(name = "words_reviewed", nullable = false)
    private Integer wordsReviewed = 0;

    @Column(name = "words_added", nullable = false)
    private Integer wordsAdded = 0;

    @Column(name = "correct_count", nullable = false)
    private Integer correctCount = 0;

    @Column(name = "incorrect_count", nullable = false)
    private Integer incorrectCount = 0;

    @Column(name = "time_spent_seconds", nullable = false)
    private Integer timeSpentSeconds = 0;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

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
    public DailyStats(UUID id, User user, LocalDate studyDate, Integer wordsReviewed,
                      Integer wordsAdded, Integer correctCount, Integer incorrectCount,
                      Integer timeSpentSeconds, Instant createdAt, Instant updatedAt) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.user = user;
        this.studyDate = studyDate;
        this.wordsReviewed = wordsReviewed != null ? wordsReviewed : 0;
        this.wordsAdded = wordsAdded != null ? wordsAdded : 0;
        this.correctCount = correctCount != null ? correctCount : 0;
        this.incorrectCount = incorrectCount != null ? incorrectCount : 0;
        this.timeSpentSeconds = timeSpentSeconds != null ? timeSpentSeconds : 0;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
