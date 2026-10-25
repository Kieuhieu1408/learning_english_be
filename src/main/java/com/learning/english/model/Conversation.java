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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "conversations")
@Getter
@Setter
@NoArgsConstructor
public class Conversation implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "note_id")
    private UUID noteId;

    @Column(nullable = false, length = 500)
    private String title;

    @Column(nullable = false)
    private Boolean pinned = false;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "conversation", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ChatMessage> messages = new ArrayList<>();

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
    public Conversation(UUID id, User user, UUID noteId, String title, Boolean pinned, Instant deletedAt,
                        Instant createdAt, Instant updatedAt, List<ChatMessage> messages) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.user = user;
        this.noteId = noteId;
        this.title = title;
        this.pinned = pinned != null ? pinned : false;
        this.deletedAt = deletedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        if (messages != null) {
            this.messages = messages;
        }
    }
}
