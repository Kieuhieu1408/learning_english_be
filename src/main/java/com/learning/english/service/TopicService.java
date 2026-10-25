package com.learning.english.service;

import com.learning.english.dto.TopicDto;
import com.learning.english.dto.TopicRequest;
import com.learning.english.model.Note;
import com.learning.english.model.Topic;
import com.learning.english.model.User;
import com.learning.english.repository.NoteRepository;
import com.learning.english.repository.TopicRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.repository.WordContextRepository;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class TopicService {

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    @Autowired
    private UserRepository userRepository;

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
            return userPrincipal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    private UUID getCurrentUserId() {
        return getCurrentUserPrincipal().getId();
    }

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserId()).orElseThrow();
    }

    @Transactional(readOnly = true)
    public List<TopicDto> getAllTopics() {
        UUID userId = getCurrentUserId();
        return topicRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId).stream()
                .map(t -> toTopicDto(t, userId))
                .toList();
    }

    @Transactional
    public TopicDto createOrUpsertTopic(TopicRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        Instant now = Instant.now();

        if (request.getId() != null) {
            Optional<Topic> existingOpt = topicRepository.findByIdAndUserId(request.getId(), userId);
            if (existingOpt.isPresent()) {
                Topic existing = existingOpt.get();
                existing.setName(request.getName().trim());
                existing.setDeletedAt(null);
                existing.setUpdatedAt(now);
                Topic saved = topicRepository.save(existing);
                return toTopicDto(saved, userId);
            }
        }

        Topic topic = Topic.builder()
                .id(request.getId() != null ? request.getId() : UuidV7Generator.generate())
                .user(user)
                .name(request.getName().trim())
                .createdAt(now)
                .updatedAt(now)
                .build();
        Topic saved = topicRepository.save(topic);
        return toTopicDto(saved, userId);
    }

    @Transactional
    public TopicDto updateTopic(UUID id, TopicRequest request) {
        UUID userId = getCurrentUserId();
        Topic topic = topicRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));

        topic.setName(request.getName().trim());
        topic.setUpdatedAt(Instant.now());
        Topic saved = topicRepository.save(topic);
        return toTopicDto(saved, userId);
    }

    @Transactional
    public void deleteTopic(UUID id) {
        UUID userId = getCurrentUserId();
        Topic topic = topicRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));

        Instant now = Instant.now();
        topic.setDeletedAt(now);
        topic.setUpdatedAt(now);
        topicRepository.save(topic);

        // Cascade soft-delete all Notes inside this Topic & detach WordContexts while preserving noteTitleSnapshot
        List<Note> notesInTopic = noteRepository.findAllByTopicIdAndUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(id, userId);
        if (!notesInTopic.isEmpty()) {
            List<UUID> noteIds = notesInTopic.stream().map(Note::getId).toList();
            wordContextRepository.detachContextsFromNotes(noteIds, userId, now);
            for (Note note : notesInTopic) {
                note.setDeletedAt(now);
                note.setUpdatedAt(now);
            }
            noteRepository.saveAll(notesInTopic);
        }
    }

    public TopicDto toTopicDto(Topic topic, UUID userId) {
        long noteCount = noteRepository.countByTopicIdAndUserIdAndDeletedAtIsNull(topic.getId(), userId);
        return TopicDto.builder()
                .id(topic.getId())
                .name(topic.getName())
                .noteCount(noteCount)
                .createdAt(topic.getCreatedAt())
                .updatedAt(topic.getUpdatedAt())
                .build();
    }
}
