package com.learning.english.service;

import com.learning.english.dto.AppConfigDto;
import com.learning.english.dto.ThemeOptionDto;
import com.learning.english.model.User;
import com.learning.english.model.UserAppConfig;
import com.learning.english.repository.UserAppConfigRepository;
import com.learning.english.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class AppConfigService {

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private UserRepository userRepository;

    private static final List<ThemeOptionDto> AVAILABLE_THEMES = List.of(
            new ThemeOptionDto("midnight", "Midnight Navy", List.of("bg-slate-900", "bg-slate-800", "bg-slate-700"), "bg-white", "bg-slate-950"),
            new ThemeOptionDto("sky", "Sky Blue", List.of("bg-sky-200", "bg-sky-300", "bg-sky-400"), "bg-sky-50", "bg-slate-950"),
            new ThemeOptionDto("lavender", "Lavender", List.of("bg-purple-200", "bg-purple-300", "bg-purple-400"), "bg-purple-50", "bg-slate-950"),
            new ThemeOptionDto("mint", "Mint Green", List.of("bg-emerald-200", "bg-emerald-300", "bg-emerald-400"), "bg-emerald-50", "bg-slate-950"),
            new ThemeOptionDto("peach", "Peach", List.of("bg-amber-200", "bg-amber-300", "bg-amber-400"), "bg-orange-50", "bg-slate-950"),
            new ThemeOptionDto("rose", "Rose", List.of("bg-rose-200", "bg-rose-300", "bg-rose-400"), "bg-rose-50", "bg-slate-950"),
            new ThemeOptionDto("slate", "Slate Gray", List.of("bg-slate-200", "bg-slate-300", "bg-slate-400"), "bg-slate-50", "bg-slate-950")
    );

    public AppConfigDto getAppConfig(UUID userId) {
        return userAppConfigRepository.findByUserId(userId)
                .map(config -> new AppConfigDto(
                        config.getThemeColor(),
                        config.getThemeMode(),
                        AVAILABLE_THEMES
                ))
                .orElse(null);
    }

    @Transactional
    public AppConfigDto saveAppConfig(UUID userId, AppConfigDto dto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserAppConfig config = userAppConfigRepository.findByUserId(userId)
                .orElseGet(() -> UserAppConfig.builder()
                        .user(user)
                        .build());

        if (dto.themeColor() != null) {
            config.setThemeColor(dto.themeColor());
        }
        if (dto.themeMode() != null) {
            config.setThemeMode(dto.themeMode());
        }

        userAppConfigRepository.save(config);

        return new AppConfigDto(
                config.getThemeColor(),
                config.getThemeMode(),
                AVAILABLE_THEMES
        );
    }
}
