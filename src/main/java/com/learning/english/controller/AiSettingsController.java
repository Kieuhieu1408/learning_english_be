package com.learning.english.controller;

import com.learning.english.dto.AiSettingsDto;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.service.AiSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/ai-settings")
public class AiSettingsController {

    @Autowired
    private AiSettingsService aiSettingsService;

    @GetMapping
    public ResponseEntity<AiSettingsDto> getSettings() {
        UUID userId = getCurrentUserId();
        AiSettingsDto settings = aiSettingsService.getSettings(userId);
        if (settings == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(settings);
    }

    @PutMapping
    public ResponseEntity<AiSettingsDto> saveSettings(@RequestBody AiSettingsDto dto) {
        UUID userId = getCurrentUserId();
        AiSettingsDto saved = aiSettingsService.saveSettings(userId, dto);
        return ResponseEntity.ok(saved);
    }

    private UUID getCurrentUserId() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get().getId();
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        throw new IllegalStateException("User not authenticated");
    }
}
