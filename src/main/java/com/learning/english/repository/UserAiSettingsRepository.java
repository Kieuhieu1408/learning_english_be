package com.learning.english.repository;

import com.learning.english.model.UserAiSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAiSettingsRepository extends JpaRepository<UserAiSettings, UUID> {
    Optional<UserAiSettings> findByUserId(UUID userId);
}
