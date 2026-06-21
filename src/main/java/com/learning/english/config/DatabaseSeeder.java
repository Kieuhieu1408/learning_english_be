package com.learning.english.config;

import com.learning.english.constant.AppConstants;
import com.learning.english.model.Role;
import com.learning.english.model.User;
import com.learning.english.model.UserSyncMetadata;
import com.learning.english.repository.UserRepository;
import com.learning.english.repository.UserSyncMetadataRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) throws Exception {
        if (!userRepository.existsByUsername(AppConstants.ADMIN_USERNAME)) {
            User admin = User.builder()
                    .username(AppConstants.ADMIN_USERNAME)
                    .password(passwordEncoder.encode(AppConstants.ADMIN_PASSWORD))
                    .email(AppConstants.ADMIN_EMAIL)
                    .role(Role.ROLE_ADMIN)
                    .build();
            User savedAdmin = userRepository.save(admin);

            UserSyncMetadata metadata = UserSyncMetadata.builder()
                    .user(savedAdmin)
                    .dailyWordCount(0)
                    .totalWordCount(0)
                    .build();
            metadataRepository.save(metadata);
            System.out.println("Admin user initialized successfully.");
        }
    }
}
