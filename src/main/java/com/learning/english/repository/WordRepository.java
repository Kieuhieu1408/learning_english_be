package com.learning.english.repository;

import com.learning.english.model.Word;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WordRepository extends JpaRepository<Word, UUID> {
    List<Word> findAllByUserId(UUID userId);

    List<Word> findAllByNoteIdAndUserId(UUID noteId, UUID userId);

    Optional<Word> findByIdAndUserId(UUID id, UUID userId);

    @Query("SELECT w FROM Word w WHERE w.user.id = :userId AND w.nextReviewAt <= :date")
    List<Word> findWordsToReview(@Param("userId") UUID userId, @Param("date") LocalDate date);

    @Query("SELECT COUNT(w) FROM Word w WHERE w.user.id = :userId AND w.nextReviewAt <= :date")
    long countWordsToReview(@Param("userId") UUID userId, @Param("date") LocalDate date);
}
