package com.learning.english.controller;

import com.learning.english.dto.AiConnectionTestResponse;
import com.learning.english.dto.AiSettingsDto;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.service.AiSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ai-settings")
public class AiSettingsController {

    @Autowired
    private AiSettingsService aiSettingsService;

    @GetMapping
    public ResponseEntity<AiSettingsDto> getSettings() {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(aiSettingsService.getSettings(userId));
    }

    @PutMapping
    public ResponseEntity<?> saveSettings(@RequestBody AiSettingsDto dto) {
        UUID userId = getCurrentUserId();
        try {
            AiSettingsDto saved = aiSettingsService.saveSettings(userId, dto);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "BAD_REQUEST",
                    "message", ex.getMessage()
            ));
        }
    }

    @PostMapping("/test")
    public ResponseEntity<AiConnectionTestResponse> testConnection(@RequestBody(required = false) AiSettingsDto dto) {
        UUID userId = getCurrentUserId();
        return ResponseEntity.ok(aiSettingsService.testConnection(userId, dto));
    }

    @GetMapping("/models")
    public ResponseEntity<List<AiConnectionTestResponse.ModelOptionDto>> getModels(
            @RequestParam(required = false) String provider,
            @RequestParam(required = false) String baseUrl) {
        UUID userId = getCurrentUserId();
        AiSettingsDto override = AiSettingsDto.builder()
                .provider(provider != null ? provider : "9ROUTER")
                .customBaseUrl(baseUrl)
                .build();
        AiConnectionTestResponse testRes = aiSettingsService.testConnection(userId, override);
        return ResponseEntity.ok(testRes.models() != null ? testRes.models() : List.of());
    }

    private UUID getCurrentUserId() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get().getId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal.getId();
        }
        throw new IllegalStateException("User not authenticated");
    }
}
