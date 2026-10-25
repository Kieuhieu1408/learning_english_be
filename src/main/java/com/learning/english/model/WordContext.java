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
import java.util.UUID;

@Entity
@Table(name = "word_contexts")
@Getter
@Setter
@NoArgsConstructor
public class WordContext implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_word_id", nullable = false)
    private UserWord userWord;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_id")
    private Note note;

    @Column(name = "note_title_snapshot", length = 500)
    private String noteTitleSnapshot;

    @Column(name = "part_of_speech", length = 50)
    private String partOfSpeech;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String meaning;

    @Column(columnDefinition = "TEXT")
    private String sentence;

    @Column(name = "sentence_translation", columnDefinition = "TEXT")
    private String sentenceTranslation;

    @Column(name = "deleted_at")
    private Instant deletedAt;

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
    public WordContext(UUID id, User user, UserWord userWord, Note note, String noteTitleSnapshot,
                       String partOfSpeech, String meaning, String sentence,
                       String sentenceTranslation, Instant deletedAt, Instant createdAt, Instant updatedAt) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.user = user;
        this.userWord = userWord;
        this.note = note;
        this.noteTitleSnapshot = noteTitleSnapshot;
        this.partOfSpeech = partOfSpeech;
        this.meaning = meaning;
        this.sentence = sentence;
        this.sentenceTranslation = sentenceTranslation;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
