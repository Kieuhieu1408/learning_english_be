package com.learning.english.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_app_config")
@Getter
@Setter
@NoArgsConstructor
public class UserAppConfig extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "theme_color")
    private String themeColor;

    @Column(name = "theme_mode")
    private String themeMode;

    @Builder
    public UserAppConfig(UUID id, User user, String themeColor, String themeMode,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.themeColor = themeColor;
        this.themeMode = themeMode;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
