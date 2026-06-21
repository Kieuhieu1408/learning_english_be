package com.learning.english.repository;

import com.learning.english.model.Note;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NoteRepository extends JpaRepository<Note, UUID> {
    List<Note> findAllByUserId(UUID userId);
    List<Note> findAllByTopicIdAndUserId(UUID topicId, UUID userId);
    Optional<Note> findByIdAndUserId(UUID id, UUID userId);
}
