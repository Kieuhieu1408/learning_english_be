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
    private String provider = "9ROUTER";

    @Column(name = "custom_base_url", length = 500)
    private String customBaseUrl = "http://localhost:20128/v1";

    @Column(name = "encrypted_api_key", length = 1024)
    private String encryptedApiKey;

    @Column(name = "translation_model")
    private String translationModel = "deepseek-chat";

    @Column(name = "quiz_model")
    private String quizModel = "deepseek-chat";

    @Column(name = "chat_model")
    private String chatModel = "deepseek-chat";

    @Builder
    public UserAiSettings(UUID id, User user, String provider, String customBaseUrl,
                          String encryptedApiKey, String translationModel,
                          String quizModel, String chatModel,
                          Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.provider = provider != null ? provider : "9ROUTER";
        this.customBaseUrl = customBaseUrl != null ? customBaseUrl : "http://localhost:20128/v1";
        this.encryptedApiKey = encryptedApiKey;
        this.translationModel = translationModel != null ? translationModel : "deepseek-chat";
        this.quizModel = quizModel != null ? quizModel : "deepseek-chat";
        this.chatModel = chatModel != null ? chatModel : "deepseek-chat";
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
