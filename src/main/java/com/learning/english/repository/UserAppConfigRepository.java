package com.learning.english.repository;

import com.learning.english.model.UserAppConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserAppConfigRepository extends JpaRepository<UserAppConfig, UUID> {
    Optional<UserAppConfig> findByUserId(UUID userId);
}
