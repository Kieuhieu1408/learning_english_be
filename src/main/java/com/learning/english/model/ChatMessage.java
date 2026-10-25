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
@Table(name = "chat_messages")
@Getter
@Setter
@NoArgsConstructor
public class ChatMessage implements Persistable<UUID> {

    @Id
    private UUID id;

    @Transient
    private boolean isNew = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private Conversation conversation;

    @Column(nullable = false, length = 50)
    private String role; // user, assistant, system

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(length = 100)
    private String model;

    @Column(name = "tokens_used")
    private Integer tokensUsed = 0;

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
    public ChatMessage(UUID id, Conversation conversation, String role, String content,
                       String model, Integer tokensUsed, Instant createdAt) {
        this.id = id != null ? id : UuidV7Generator.generate();
        this.conversation = conversation;
        this.role = role;
        this.content = content;
        this.model = model;
        this.tokensUsed = tokensUsed != null ? tokensUsed : 0;
        this.createdAt = createdAt;
    }
}
