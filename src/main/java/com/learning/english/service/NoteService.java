package com.learning.english.service;

import com.learning.english.client.AIClient;
import com.learning.english.dto.NoteRequest;
import com.learning.english.model.Note;
import com.learning.english.model.Topic;
import com.learning.english.model.User;
import com.learning.english.repository.NoteRepository;
import com.learning.english.repository.TopicRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("geminiAdapter") // Default AI Client provider
    private AIClient aiClient;

    private UserPrincipal getCurrentUserPrincipal() {
        if (SecurityContextUtils.CURRENT_USER.isBound()) {
            return SecurityContextUtils.CURRENT_USER.get();
        }
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserPrincipal) {
            return (UserPrincipal) principal;
        }
        throw new IllegalStateException("User not authenticated");
    }

    private User getCurrentUserEntity() {
        return userRepository.findById(getCurrentUserPrincipal().getId()).orElseThrow();
    }

    public List<Note> getAllNotes() {
        return noteRepository.findAllByUserId(getCurrentUserPrincipal().getId());
    }

    public List<Note> getNotesByTopic(UUID topicId) {
        return noteRepository.findAllByTopicIdAndUserId(topicId, getCurrentUserPrincipal().getId());
    }

    @Transactional
    public Note saveNote(NoteRequest request) {
        User user = getCurrentUserEntity();
        Note note;

        if (request.getId() != null) {
            note = noteRepository.findByIdAndUserId(request.getId(), user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));
            note.setTitle(request.getTitle());
            note.setContent(request.getContent());
        } else {
            note = Note.builder()
                    .user(user)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .build();
        }

        // Logic for AI classification if topicId is null
        Topic topic = null;
        if (request.getTopicId() != null) {
            topic = topicRepository.findByIdAndUserId(request.getTopicId(), user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));
        } else {
            // Trigger AI classification dynamically based on existing user topics
            List<Topic> existingTopics = topicRepository.findAllByUserId(user.getId());
            if (!existingTopics.isEmpty()) {
                List<String> topicNames = existingTopics.stream().map(Topic::getName).collect(Collectors.toList());
                String recommendedTopicName = aiClient.classifyTopic(request.getContent(), topicNames);
                
                // Find matching topic or create a new one dynamically if AI recommended a new name
                topic = existingTopics.stream()
                        .filter(t -> t.getName().equalsIgnoreCase(recommendedTopicName))
                        .findFirst()
                        .orElseGet(() -> {
                            Topic newTopic = Topic.builder().user(user).name(recommendedTopicName).build();
                            return topicRepository.save(newTopic);
                        });
            }
        }
        note.setTopic(topic);

        return noteRepository.save(note);
    }

    @Transactional
    public void deleteNote(UUID id) {
        UUID userId = getCurrentUserPrincipal().getId();
        Note note = noteRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));
        noteRepository.delete(note);
    }
}
