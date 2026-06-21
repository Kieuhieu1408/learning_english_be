package com.learning.english.repository;

import com.learning.english.model.UserSyncMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserSyncMetadataRepository extends JpaRepository<UserSyncMetadata, UUID> {
    Optional<UserSyncMetadata> findByUserId(UUID userId);

    @Modifying
    @Query("UPDATE UserSyncMetadata m SET m.totalWordCount = m.totalWordCount + :delta WHERE m.user.id = :userId")
    void updateWordCount(@Param("userId") UUID userId, @Param("delta") int delta);

    @Modifying
    @Query("UPDATE UserSyncMetadata m SET m.dailyWordCount = m.dailyWordCount + :delta WHERE m.user.id = :userId")
    void updateDailyWordCount(@Param("userId") UUID userId, @Param("delta") int delta);
}
