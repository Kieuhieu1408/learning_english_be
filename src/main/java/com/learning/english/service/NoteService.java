package com.learning.english.service;

import com.learning.english.client.AIClient;
import com.learning.english.dto.DeltaSyncResponse;
import com.learning.english.dto.NoteDto;
import com.learning.english.dto.NoteRequest;
import com.learning.english.model.Note;
import com.learning.english.model.Topic;
import com.learning.english.model.User;
import com.learning.english.model.WordContext;
import com.learning.english.repository.NoteRepository;
import com.learning.english.repository.TopicRepository;
import com.learning.english.repository.UserRepository;
import com.learning.english.repository.WordContextRepository;
import com.learning.english.security.SecurityContextUtils;
import com.learning.english.security.UserPrincipal;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class NoteService {

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    @Qualifier("geminiAdapter")
    private AIClient aiClient;

    public static class NoteConflictException extends RuntimeException {
        private final DeltaSyncResponse.ConflictDto conflict;

        public NoteConflictException(DeltaSyncResponse.ConflictDto conflict) {
            super("Note has been modified on another device.");
            this.conflict = conflict;
        }

        public DeltaSyncResponse.ConflictDto getConflict() {
            return conflict;
        }
    }

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
    public List<NoteDto> getAllNotes() {
        UUID userId = getCurrentUserId();
        return noteRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(userId).stream()
                .map(this::toNoteListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NoteDto> getNotesByTopic(UUID topicId) {
        UUID userId = getCurrentUserId();
        return noteRepository.findAllByTopicIdAndUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(topicId, userId).stream()
                .map(this::toNoteListDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public NoteDto getNoteDetail(UUID noteId) {
        UUID userId = getCurrentUserId();
        Note note = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));

        List<WordContext> contexts = wordContextRepository.findAllByNoteIdAndUserIdWithWord(noteId, userId);
        Map<UUID, NoteDto.SavedWordSnippetDto> uniqueSavedWords = new LinkedHashMap<>();
        for (WordContext ctx : contexts) {
            UUID wordId = ctx.getUserWord().getId();
            uniqueSavedWords.putIfAbsent(wordId, NoteDto.SavedWordSnippetDto.builder()
                    .wordId(wordId)
                    .text(ctx.getUserWord().getText())
                    .meaning(ctx.getMeaning() != null ? ctx.getMeaning() : ctx.getUserWord().getPrimaryMeaning())
                    .build());
        }

        return NoteDto.builder()
                .id(note.getId())
                .topicId(note.getTopic() != null ? note.getTopic().getId() : null)
                .title(note.getTitle())
                .content(note.getContent())
                .version(note.getVersion() != null ? note.getVersion() : 1L)
                .savedWords(new ArrayList<>(uniqueSavedWords.values()))
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }

    @Transactional
    public NoteDto createOrUpsertNote(NoteRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        Instant now = Instant.now();

        Note note = null;
        boolean titleChanged = false;
        if (request.getId() != null) {
            Optional<Note> existingOpt = noteRepository.findByIdAndUserId(request.getId(), userId);
            if (existingOpt.isPresent()) {
                note = existingOpt.get();
                titleChanged = !Objects.equals(note.getTitle(), request.getTitle());
                note.setTitle(request.getTitle());
                note.setContent(request.getContent());
                note.setDeletedAt(null);
                note.setUpdatedAt(now);
            }
        }

        if (note == null) {
            note = Note.builder()
                    .id(request.getId() != null ? request.getId() : UuidV7Generator.generate())
                    .user(user)
                    .title(request.getTitle())
                    .content(request.getContent())
                    .createdAt(now)
                    .updatedAt(now)
                    .build();
        }

        Topic topic = resolveTopic(user, request.getTopicId(), request.getContent());
        note.setTopic(topic);

        Note saved = noteRepository.saveAndFlush(note);
        if (titleChanged) {
            wordContextRepository.updateNoteTitleSnapshot(saved.getId(), userId, saved.getTitle(), now);
        }
        return toNoteListDto(saved);
    }

    @Transactional
    public NoteDto updateNote(UUID noteId, NoteRequest request) {
        User user = getCurrentUserEntity();
        UUID userId = user.getId();
        Note note = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(noteId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));

        long serverVersion = note.getVersion() != null ? note.getVersion() : 1L;
        if (request.getBaseVersion() != null && request.getBaseVersion() < serverVersion) {
            boolean titleDiff = !Objects.equals(note.getTitle(), request.getTitle());
            boolean contentDiff = !Objects.equals(note.getContent(), request.getContent());
            if (titleDiff || contentDiff) {
                throw new NoteConflictException(DeltaSyncResponse.ConflictDto.builder()
                        .noteId(note.getId())
                        .serverVersion(serverVersion)
                        .cloudTitle(note.getTitle())
                        .cloudContent(note.getContent())
                        .cloudUpdatedAt(note.getUpdatedAt())
                        .build());
            }
        }

        Instant now = Instant.now();
        boolean titleChanged = !Objects.equals(note.getTitle(), request.getTitle());
        note.setTitle(request.getTitle());
        note.setContent(request.getContent());
        if (request.getTopicId() != null) {
            Topic topic = topicRepository.findByIdAndUserIdAndDeletedAtIsNull(request.getTopicId(), userId)
                    .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));
            note.setTopic(topic);
        }
        note.setUpdatedAt(now);

        Note saved = noteRepository.saveAndFlush(note);
        if (titleChanged) {
            wordContextRepository.updateNoteTitleSnapshot(saved.getId(), userId, saved.getTitle(), now);
        }
        return toNoteListDto(saved);
    }

    @Transactional
    public void deleteNote(UUID id) {
        UUID userId = getCurrentUserId();
        Note note = noteRepository.findByIdAndUserIdAndDeletedAtIsNull(id, userId)
                .orElseThrow(() -> new IllegalArgumentException("Note not found or access denied"));

        Instant now = Instant.now();
        // Detach word_contexts while preserving note_title_snapshot
        wordContextRepository.detachContextsFromNote(id, userId, now);

        note.setDeletedAt(now);
        note.setUpdatedAt(now);
        noteRepository.save(note);
    }

    private Topic resolveTopic(User user, UUID topicId, String content) {
        UUID userId = user.getId();
        if (topicId != null) {
            return topicRepository.findByIdAndUserIdAndDeletedAtIsNull(topicId, userId)
                    .orElseThrow(() -> new IllegalArgumentException("Topic not found or access denied"));
        }
        List<Topic> existingTopics = topicRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(userId);
        if (!existingTopics.isEmpty()) {
            List<String> topicNames = existingTopics.stream().map(Topic::getName).collect(Collectors.toList());
            String recommendedTopicName = aiClient.classifyTopic(content, topicNames);
            return existingTopics.stream()
                    .filter(t -> t.getName().equalsIgnoreCase(recommendedTopicName))
                    .findFirst()
                    .orElseGet(() -> {
                        Topic newTopic = Topic.builder().user(user).name(recommendedTopicName).build();
                        return topicRepository.save(newTopic);
                    });
        }
        return null;
    }

    public NoteDto toNoteListDto(Note note) {
        long harvestedCount = wordContextRepository.countDistinctWordsByNoteId(note.getId());
        return NoteDto.builder()
                .id(note.getId())
                .topicId(note.getTopic() != null ? note.getTopic().getId() : null)
                .title(note.getTitle())
                .content(note.getContent())
                .harvestedWordCount(harvestedCount)
                .version(note.getVersion() != null ? note.getVersion() : 1L)
                .createdAt(note.getCreatedAt())
                .updatedAt(note.getUpdatedAt())
                .build();
    }
}
