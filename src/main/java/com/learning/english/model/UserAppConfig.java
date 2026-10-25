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

    @Column(nullable = false, length = 100)
    private String timezone = "Asia/Ho_Chi_Minh";

    @Column(name = "daily_target", nullable = false)
    private Integer dailyTarget = 20;

    @Column(name = "sidebar_width", nullable = false)
    private Integer sidebarWidth = 244;

    @Column(name = "auto_speak", nullable = false)
    private Boolean autoSpeak = false;

    @Version
    private Long version;

    @Builder
    public UserAppConfig(UUID id, User user, String themeColor, String themeMode,
                         String timezone, Integer dailyTarget, Integer sidebarWidth,
                         Boolean autoSpeak, Long version,
                         Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.user = user;
        this.themeColor = themeColor;
        this.themeMode = themeMode;
        this.timezone = timezone != null ? timezone : "Asia/Ho_Chi_Minh";
        this.dailyTarget = dailyTarget != null ? dailyTarget : 20;
        this.sidebarWidth = sidebarWidth != null ? sidebarWidth : 244;
        this.autoSpeak = autoSpeak != null ? autoSpeak : false;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }
}
