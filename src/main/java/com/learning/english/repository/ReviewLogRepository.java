package com.learning.english.repository;

import com.learning.english.model.ReviewLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Repository
public interface ReviewLogRepository extends JpaRepository<ReviewLog, UUID> {

    boolean existsByEventId(UUID eventId);

    @Query("SELECT r.eventId FROM ReviewLog r WHERE r.eventId IN :eventIds")
    Set<UUID> findExistingEventIds(@Param("eventIds") Collection<UUID> eventIds);

    List<ReviewLog> findAllByUserIdOrderByReviewedAtDesc(UUID userId);

    @Query("SELECT COUNT(r) FROM ReviewLog r WHERE r.user.id = :userId AND r.reviewedAt >= :start AND r.reviewedAt <= :end")
    long countByUserIdAndReviewedAtBetween(
            @Param("userId") UUID userId,
            @Param("start") Instant start,
            @Param("end") Instant end
    );
}
