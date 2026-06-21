package com.learning.english.model;

import com.learning.english.constant.WordStatus;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.springframework.data.domain.Persistable;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "words")
public class Word implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @Column(nullable = false)
    private String text;

    @Column(columnDefinition = "TEXT")
    private String translation;

    @Column(name = "part_of_speech")
    private String partOfSpeech;

    private String ipa;

    @Column(name = "example_context", columnDefinition = "TEXT")
    private String exampleContext;

    @Column(name = "example_translation", columnDefinition = "TEXT")
    private String exampleTranslation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private WordStatus status;

    @Column(name = "correct_streak", nullable = false)
    private Integer correctStreak = 0;

    @Column(nullable = false)
    private Double difficulty = 2.0;

    @Column(name = "interval_days", nullable = false)
    private Integer intervalDays = 0;

    @Column(name = "next_review_at", nullable = false)
    private LocalDate nextReviewAt;

    @Column(name = "failed_essay_streak", nullable = false)
    private Integer failedEssayStreak = 0;

    @Version
    private Long version;

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

    public Word() {}

    public Word(UUID id, User user, Note note, String text, String translation, String partOfSpeech, String ipa,
                String exampleContext, String exampleTranslation, WordStatus status, Integer correctStreak,
                Double difficulty, Integer intervalDays, LocalDate nextReviewAt, Integer failedEssayStreak,
                Long version, Instant createdAt, Instant updatedAt) {
        this.id = id != null ? id : UUID.randomUUID();
        this.user = user;
        this.note = note;
        this.text = text;
        this.translation = translation;
        this.partOfSpeech = partOfSpeech;
        this.ipa = ipa;
        this.exampleContext = exampleContext;
        this.exampleTranslation = exampleTranslation;
        this.status = status;
        this.correctStreak = correctStreak != null ? correctStreak : 0;
        this.difficulty = difficulty != null ? difficulty : 2.0;
        this.intervalDays = intervalDays != null ? intervalDays : 0;
        this.nextReviewAt = nextReviewAt;
        this.failedEssayStreak = failedEssayStreak != null ? failedEssayStreak : 0;
        this.version = version;
        this.createdAt = createdAt;
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

    public Note getNote() {
        return note;
    }

    public void setNote(Note note) {
        this.note = note;
    }

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getTranslation() {
        return translation;
    }

    public void setTranslation(String translation) {
        this.translation = translation;
    }

    public String getPartOfSpeech() {
        return partOfSpeech;
    }

    public void setPartOfSpeech(String partOfSpeech) {
        this.partOfSpeech = partOfSpeech;
    }

    public String getIpa() {
        return ipa;
    }

    public void setIpa(String ipa) {
        this.ipa = ipa;
    }

    public String getExampleContext() {
        return exampleContext;
    }

    public void setExampleContext(String exampleContext) {
        this.exampleContext = exampleContext;
    }

    public String getExampleTranslation() {
        return exampleTranslation;
    }

    public void setExampleTranslation(String exampleTranslation) {
        this.exampleTranslation = exampleTranslation;
    }

    public WordStatus getStatus() {
        return status;
    }

    public void setStatus(WordStatus status) {
        this.status = status;
    }

    public Integer getCorrectStreak() {
        return correctStreak;
    }

    public void setCorrectStreak(Integer correctStreak) {
        this.correctStreak = correctStreak;
    }

    public Double getDifficulty() {
        return difficulty;
    }

    public void setDifficulty(Double difficulty) {
        this.difficulty = difficulty;
    }

    public Integer getIntervalDays() {
        return intervalDays;
    }

    public void setIntervalDays(Integer intervalDays) {
        this.intervalDays = intervalDays;
    }

    public LocalDate getNextReviewAt() {
        return nextReviewAt;
    }

    public void setNextReviewAt(LocalDate nextReviewAt) {
        this.nextReviewAt = nextReviewAt;
    }

    public Integer getFailedEssayStreak() {
        return failedEssayStreak;
    }

    public void setFailedEssayStreak(Integer failedEssayStreak) {
        this.failedEssayStreak = failedEssayStreak;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    // Manual Builder
    public static WordBuilder builder() {
        return new WordBuilder();
    }

    public static class WordBuilder {
        private UUID id;
        private User user;
        private Note note;
        private String text;
        private String translation;
        private String partOfSpeech;
        private String ipa;
        private String exampleContext;
        private String exampleTranslation;
        private WordStatus status;
        private Integer correctStreak = 0;
        private Double difficulty = 2.0;
        private Integer intervalDays = 0;
        private LocalDate nextReviewAt;
        private Integer failedEssayStreak = 0;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;

        public WordBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public WordBuilder user(User user) {
            this.user = user;
            return this;
        }

        public WordBuilder note(Note note) {
            this.note = note;
            return this;
        }

        public WordBuilder text(String text) {
            this.text = text;
            return this;
        }

        public WordBuilder translation(String translation) {
            this.translation = translation;
            return this;
        }

        public WordBuilder partOfSpeech(String partOfSpeech) {
            this.partOfSpeech = partOfSpeech;
            return this;
        }

        public WordBuilder ipa(String ipa) {
            this.ipa = ipa;
            return this;
        }

        public WordBuilder exampleContext(String exampleContext) {
            this.exampleContext = exampleContext;
            return this;
        }

        public WordBuilder exampleTranslation(String exampleTranslation) {
            this.exampleTranslation = exampleTranslation;
            return this;
        }

        public WordBuilder status(WordStatus status) {
            this.status = status;
            return this;
        }

        public WordBuilder correctStreak(Integer correctStreak) {
            this.correctStreak = correctStreak;
            return this;
        }

        public WordBuilder difficulty(Double difficulty) {
            this.difficulty = difficulty;
            return this;
        }

        public WordBuilder intervalDays(Integer intervalDays) {
            this.intervalDays = intervalDays;
            return this;
        }

        public WordBuilder nextReviewAt(LocalDate nextReviewAt) {
            this.nextReviewAt = nextReviewAt;
            return this;
        }

        public WordBuilder failedEssayStreak(Integer failedEssayStreak) {
            this.failedEssayStreak = failedEssayStreak;
            return this;
        }

        public WordBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public WordBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public WordBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Word build() {
            return new Word(id, user, note, text, translation, partOfSpeech, ipa, exampleContext,
                    exampleTranslation, status, correctStreak, difficulty, intervalDays, nextReviewAt,
                    failedEssayStreak, version, createdAt, updatedAt);
        }
    }
}
