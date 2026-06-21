package com.learning.english.service;

import com.learning.english.dto.*;
import com.learning.english.model.*;
import com.learning.english.repository.UserRepository;
import com.learning.english.repository.UserSyncMetadataRepository;
import com.learning.english.security.JwtTokenProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already exists");
        }

        User user = User.builder()
                .username(request.getUsername())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(Role.ROLE_USER)
                .build();
        User savedUser = userRepository.save(user);

        // Initialize user sync metadata with static counters
        UserSyncMetadata metadata = UserSyncMetadata.builder()
                .user(savedUser)
                .dailyWordCount(0)
                .totalWordCount(0)
                .build();
        metadataRepository.save(metadata);

        // Automatically authenticate user after registration
        return authenticateUser(request.getUsername(), request.getPassword());
    }

    public AuthResponse login(LoginRequest request) {
        return authenticateUser(request.getUsername(), request.getPassword());
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
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .role(request.getRole())
                .build();
        User savedUser = userRepository.save(user);

        // Initialize user sync metadata with static counters
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

        String jwt = tokenProvider.generateToken(authentication);
        User user = userRepository.findByUsername(username).orElseThrow();

        return AuthResponse.builder()
                .token(jwt)
                .userId(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .role(user.getRole().name())
                .build();
    }
}
