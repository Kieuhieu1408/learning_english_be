package com.learning.english.repository;

import com.learning.english.model.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, UUID> {
    List<Conversation> findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(UUID userId);
    Optional<Conversation> findByIdAndUserIdAndDeletedAtIsNull(UUID id, UUID userId);
    Optional<Conversation> findByIdAndUserId(UUID id, UUID userId);
}
