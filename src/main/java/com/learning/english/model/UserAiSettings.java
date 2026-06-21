package com.learning.english.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_ai_settings")
@Getter
@Setter
@NoArgsConstructor
public class UserAiSettings extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "provider")
    private String provider;

    @Column(name = "encrypted_api_key", length = 1024)
    private String encryptedApiKey;

    @Column(name = "translation_model")
    private String translationModel;

    @Column(name = "quiz_model")
    private String quizModel;

    @Builder
    public UserAiSettings(UUID id, User user, String provider, String encryptedApiKey,
                          String translationModel, String quizModel,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.provider = provider;
        this.encryptedApiKey = encryptedApiKey;
        this.translationModel = translationModel;
        this.quizModel = quizModel;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
