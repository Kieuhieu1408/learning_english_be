package com.learning.english.service;

import com.learning.english.cache.SlidingWordWindow;
import com.learning.english.constant.Role;
import com.learning.english.dto.*;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import com.learning.english.security.CustomUserDetailsService;
import com.learning.english.security.JwtTokenProvider;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.TimezoneUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserAppConfigRepository userAppConfigRepository;

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private SlidingWordWindow slidingWordWindow;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private CustomUserDetailsService customUserDetailsService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmailIgnoreCase(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        String displayName = (request.getName() != null && !request.getName().isBlank())
                ? request.getName().trim()
                : request.getUsername();

        User user = User.builder()
                .username(request.getUsername())
                .name(displayName)
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ROLE_USER)
                .build();
        User savedUser = userRepository.save(user);

        UserSyncMetadata metadata = UserSyncMetadata.builder()
                .user(savedUser)
                .dailyWordCount(0)
                .totalWordCount(0)
                .build();
        metadataRepository.save(metadata);

        String timezone = TimezoneUtil.parseZoneId(request.getTimezone()).getId();
        UserAppConfig appConfig = UserAppConfig.builder()
                .user(savedUser)
                .timezone(timezone)
                .dailyTarget(20)
                .sidebarWidth(244)
                .themeMode("light")
                .autoSpeak(false)
                .build();
        userAppConfigRepository.save(appConfig);

        return authenticateUser(request.getUsername(), request.getPassword());
    }

    public AuthResponse login(LoginRequest request) {
        return authenticateUser(request.getUsername(), request.getPassword());
    }

    @Transactional(readOnly = true)
    public AuthMeResponse getMe() {
        UserPrincipal principal = getCurrentUserPrincipal();
        UUID userId = principal.getId();
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        UserAppConfig config = userAppConfigRepository.findByUserId(userId).orElse(null);
        String timezone = (config != null && config.getTimezone() != null)
                ? config.getTimezone()
                : TimezoneUtil.DEFAULT_TIMEZONE;
        int dailyTarget = (config != null && config.getDailyTarget() != null) ? config.getDailyTarget() : 20;
        String theme = (config != null && config.getThemeMode() != null) ? config.getThemeMode() : "light";
        int sidebarWidth = (config != null && config.getSidebarWidth() != null) ? config.getSidebarWidth() : 244;

        ZoneId zoneId = TimezoneUtil.parseZoneId(timezone);
        LocalDate today = TimezoneUtil.today(zoneId);
        Instant dueCutoff = TimezoneUtil.endOfDayInstant(today, zoneId);

        long dueCount = userWordRepository.countDueWords(userId, dueCutoff);
        long totalWords = userWordRepository.countByUserIdAndDeletedAtIsNull(userId);
        int reviewedToday = dailyStatsRepository.findByUserIdAndStudyDate(userId, today)
                .map(d -> d.getWordsReviewed() != null ? d.getWordsReviewed() : 0)
                .orElse(0);

        UserSyncMetadata metadata = metadataRepository.findByUserId(userId).orElse(null);
        int streakDays = (metadata != null && metadata.getCurrentStreakDays() != null) ? metadata.getCurrentStreakDays() : 0;
        Instant lastSyncAt = metadata != null
                ? (metadata.getLastSyncedAt() != null ? metadata.getLastSyncedAt() : metadata.getUpdatedAt())
                : null;

        // Pre-warm 45 due words cache asynchronously
        slidingWordWindow.preWarmAsync(userId);

        return AuthMeResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .name(user.getName() != null ? user.getName() : user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .config(AuthMeResponse.ConfigDto.builder()
                        .timezone(timezone)
                        .dailyTarget(dailyTarget)
                        .theme(theme)
                        .sidebarWidth(sidebarWidth)
                        .build())
                .counters(AuthMeResponse.CountersDto.builder()
                        .dueCount(dueCount)
                        .reviewedToday(reviewedToday)
                        .totalWords(totalWords)
                        .streakDays(streakDays)
                        .lastSyncAt(lastSyncAt)
                        .build())
                .build();
    }

    @Transactional
    public User createUserByAdmin(CreateUserRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .name(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole())
                .build();
        User savedUser = userRepository.save(user);

        UserSyncMetadata metadata = UserSyncMetadata.builder()
                .user(savedUser)
                .dailyWordCount(0)
                .totalWordCount(0)
                .build();
        metadataRepository.save(metadata);

        return savedUser;
    }

    private AuthResponse authenticateUser(String username, String password) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username, password)
        );

        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        User user = userRepository.findByUsernameIgnoreCase(username)
                .or(() -> userRepository.findByUsername(username))
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));

        // Trigger background pre-warming of 45 due words in Caffeine cache
        slidingWordWindow.preWarmAsync(user.getId());

        return buildAuthResponse(principal);
    }

    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            throw new BadCredentialsException("Invalid or expired refresh token");
        }
        RefreshToken stored = refreshTokenRepository.findById(tokenProvider.getTokenIdFromJWT(refreshToken))
                .orElseThrow(() -> new BadCredentialsException("Invalid or expired refresh token"));

        if (stored.isRevoked()) {
            // A rotated/revoked token was replayed: assume it leaked and kill every session of this user.
            refreshTokenRepository.revokeAllByUserId(stored.getUserId());
            throw new BadCredentialsException("Refresh token has been revoked");
        }

        // Rotation: the used token can never be exchanged again.
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        String username = tokenProvider.getUsernameFromJWT(refreshToken);
        UserPrincipal principal = (UserPrincipal) customUserDetailsService.loadUserByUsername(username);
        return buildAuthResponse(principal);
    }

    /** Revokes the given refresh token. Silently ignores unknown or invalid tokens. */
    @Transactional
    public void logout(RefreshTokenRequest request) {
        String refreshToken = request.getRefreshToken();
        if (!tokenProvider.validateRefreshToken(refreshToken)) {
            return;
        }
        refreshTokenRepository.findById(tokenProvider.getTokenIdFromJWT(refreshToken)).ifPresent(t -> {
            t.setRevoked(true);
            refreshTokenRepository.save(t);
        });
    }

    private AuthResponse buildAuthResponse(UserPrincipal principal) {
        UUID tokenId = UUID.randomUUID();
        String refreshToken = tokenProvider.generateRefreshToken(principal, tokenId);
        refreshTokenRepository.save(RefreshToken.builder()
                .id(tokenId)
                .userId(principal.getId())
                .expiresAt(Instant.now().plusMillis(tokenProvider.getRefreshExpirationMs()))
                .build());
        // Opportunistic cleanup of expired rows
        refreshTokenRepository.deleteExpired(Instant.now());

        return AuthResponse.builder()
                .accessToken(tokenProvider.generateAccessToken(principal))
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(tokenProvider.getAccessExpirationSeconds())
                .build();
    }

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new IllegalStateException("User not authenticated");
    }
}
