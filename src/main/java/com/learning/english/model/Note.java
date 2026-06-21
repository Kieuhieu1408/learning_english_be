package com.learning.english.model;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "notes")
public class Note {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "topic_id")
    private Topic topic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String content;

    @Version
    private Long version;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    public Note() {}

    public Note(UUID id, Topic topic, User user, String title, String content, Long version, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.topic = topic;
        this.user = user;
        this.title = title;
        this.content = content;
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

    public Topic getTopic() {
        return topic;
    }

    public void setTopic(Topic topic) {
        this.topic = topic;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
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
    public static NoteBuilder builder() {
        return new NoteBuilder();
    }

    public static class NoteBuilder {
        private UUID id;
        private Topic topic;
        private User user;
        private String title;
        private String content;
        private Long version;
        private Instant createdAt;
        private Instant updatedAt;

        public NoteBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public NoteBuilder topic(Topic topic) {
            this.topic = topic;
            return this;
        }

        public NoteBuilder user(User user) {
            this.user = user;
            return this;
        }

        public NoteBuilder title(String title) {
            this.title = title;
            return this;
        }

        public NoteBuilder content(String content) {
            this.content = content;
            return this;
        }

        public NoteBuilder version(Long version) {
            this.version = version;
            return this;
        }

        public NoteBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public NoteBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public Note build() {
            return new Note(id, topic, user, title, content, version, createdAt, updatedAt);
        }
    }
}
