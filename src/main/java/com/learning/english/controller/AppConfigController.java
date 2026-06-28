package com.learning.english.controller;

import com.learning.english.dto.AppConfigDto;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.service.AppConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/app-config")
public class AppConfigController {

    @Autowired
    private AppConfigService appConfigService;

    @GetMapping
    public ResponseEntity<AppConfigDto> getAppConfig() {
        UUID userId = getCurrentUserId();
        AppConfigDto config = appConfigService.getAppConfig(userId);
        if (config == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(config);
    }

    @PutMapping
    public ResponseEntity<AppConfigDto> saveAppConfig(@RequestBody AppConfigDto dto) {
        UUID userId = getCurrentUserId();
        AppConfigDto saved = appConfigService.saveAppConfig(userId, dto);
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
