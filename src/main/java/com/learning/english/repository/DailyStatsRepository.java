package com.learning.english.repository;

import com.learning.english.model.DailyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyStatsRepository extends JpaRepository<DailyStats, UUID> {

    Optional<DailyStats> findByUserIdAndStudyDate(UUID userId, LocalDate studyDate);

    @Query("SELECT d FROM DailyStats d WHERE d.user.id = :userId " +
           "AND d.studyDate >= :startDate AND d.studyDate <= :endDate " +
           "ORDER BY d.studyDate ASC")
    List<DailyStats> findStatsBetweenDates(
            @Param("userId") UUID userId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate
    );

    List<DailyStats> findAllByUserId(UUID userId);
}
