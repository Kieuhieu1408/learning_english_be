package com.learning.english.repository;

import com.learning.english.model.WordContext;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface WordContextRepository extends JpaRepository<WordContext, UUID> {

    Optional<WordContext> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);

    Optional<WordContext> findByIdAndUserWordIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userWordId, UUID userId);

    List<WordContext> findAllByUserWordIdAndDeletedAtIsNull(UUID userWordId);

    @Query("SELECT wc FROM WordContext wc JOIN FETCH wc.userWord w " +
           "WHERE wc.note.id = :noteId AND wc.user.id = :userId AND wc.deletedAt IS NULL AND w.deletedAt IS NULL")
    List<WordContext> findAllByNoteIdAndUserIdWithWord(@Param("noteId") UUID noteId, @Param("userId") UUID userId);

    @Query("SELECT COUNT(DISTINCT wc.userWord.id) FROM WordContext wc " +
           "WHERE wc.note.id = :noteId AND wc.deletedAt IS NULL AND wc.userWord.deletedAt IS NULL")
    long countDistinctWordsByNoteId(@Param("noteId") UUID noteId);

    @Modifying
    @Query("UPDATE WordContext wc SET wc.note = null, wc.updatedAt = :now " +
           "WHERE wc.note.id = :noteId AND wc.user.id = :userId")
    void detachContextsFromNote(@Param("noteId") UUID noteId, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE WordContext wc SET wc.note = null, wc.updatedAt = :now " +
           "WHERE wc.note.id IN :noteIds AND wc.user.id = :userId")
    void detachContextsFromNotes(@Param("noteIds") Collection<UUID> noteIds, @Param("userId") UUID userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE WordContext wc SET wc.noteTitleSnapshot = :title, wc.updatedAt = :now " +
           "WHERE wc.note.id = :noteId AND wc.user.id = :userId AND wc.deletedAt IS NULL")
    void updateNoteTitleSnapshot(@Param("noteId") UUID noteId, @Param("userId") UUID userId, @Param("title") String title, @Param("now") Instant now);

    List<WordContext> findAllByUserIdAndUpdatedAtAfterAndDeletedAtIsNull(UUID userId, Instant cursor);

    List<WordContext> findAllByUserIdAndDeletedAtAfter(UUID userId, Instant cursor);

    List<WordContext> findAllByUserIdAndDeletedAtIsNull(UUID userId);
}
