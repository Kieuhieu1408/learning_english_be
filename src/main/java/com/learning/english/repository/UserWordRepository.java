package com.learning.english.repository;

import com.learning.english.constant.WordStatus;
import com.learning.english.model.UserWord;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserWordRepository extends JpaRepository<UserWord, UUID> {

    @EntityGraph(attributePaths = {"contexts"})
    Optional<UserWord> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<UserWord> findByIdAndUserId(UUID id, UUID userId);

    @EntityGraph(attributePaths = {"contexts"})
    Optional<UserWord> findByUserIdAndTextNormalizedAndDeletedAtIsNull(UUID userId, String textNormalized);

    @EntityGraph(attributePaths = {"contexts"})
    List<UserWord> findAllByUserIdAndDeletedAtIsNull(UUID userId);

    @EntityGraph(attributePaths = {"contexts"})
    @Query("SELECT DISTINCT w FROM UserWord w LEFT JOIN w.contexts c " +
           "WHERE w.user.id = :userId AND w.deletedAt IS NULL " +
           "AND (:status IS NULL OR w.status = :status) " +
           "AND (:q IS NULL OR :q = '' OR LOWER(w.text) LIKE LOWER(CONCAT('%', :q, '%')) " +
           "     OR LOWER(w.primaryMeaning) LIKE LOWER(CONCAT('%', :q, '%')))")
    List<UserWord> searchWords(
            @Param("userId") UUID userId,
            @Param("q") String q,
            @Param("status") WordStatus status
    );

    @EntityGraph(attributePaths = {"contexts"})
    @Query("SELECT w FROM UserWord w " +
           "WHERE w.user.id = :userId AND w.deletedAt IS NULL " +
           "AND (w.nextReviewAt IS NULL OR w.nextReviewAt <= :dueCutoff) " +
           "ORDER BY w.nextReviewAt ASC NULLS FIRST, w.status ASC")
    List<UserWord> findDueWords(
            @Param("userId") UUID userId,
            @Param("dueCutoff") Instant dueCutoff,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"contexts"})
    @Query("SELECT w FROM UserWord w " +
           "WHERE w.user.id = :userId AND w.deletedAt IS NULL " +
           "AND (w.nextReviewAt IS NULL OR w.nextReviewAt <= :dueCutoff) " +
           "AND w.id NOT IN :excludeIds " +
           "ORDER BY w.nextReviewAt ASC NULLS FIRST, w.status ASC")
    List<UserWord> findDueWordsExcluding(
            @Param("userId") UUID userId,
            @Param("dueCutoff") Instant dueCutoff,
            @Param("excludeIds") Collection<UUID> excludeIds,
            Pageable pageable
    );

    @Query("SELECT COUNT(w) FROM UserWord w " +
           "WHERE w.user.id = :userId AND w.deletedAt IS NULL " +
           "AND (w.nextReviewAt IS NULL OR w.nextReviewAt <= :dueCutoff)")
    long countDueWords(
            @Param("userId") UUID userId,
            @Param("dueCutoff") Instant dueCutoff
    );

    long countByUserIdAndDeletedAtIsNull(UUID userId);

    long countByUserIdAndStatusAndDeletedAtIsNull(UUID userId, WordStatus status);

    @EntityGraph(attributePaths = {"contexts"})
    List<UserWord> findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(UUID userId, Instant cursor);

    List<UserWord> findAllByUserIdAndDeletedAtAfter(UUID userId, Instant cursor);

    @EntityGraph(attributePaths = {"contexts"})
    List<UserWord> findAllByIdInAndUserIdAndDeletedAtIsNull(Collection<UUID> ids, UUID userId);
}
