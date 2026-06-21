package com.learning.english;

import com.learning.english.constant.Role;
import com.learning.english.constant.WordStatus;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.ActiveProfiles;

import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class Unit1JPATests {

    @Autowired
    private com.learning.english.service.SyncService syncService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private WordRepository wordRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private EntityManager entityManager;

    private User savedUser;

    @BeforeEach
    public void setUp() {
        transactionTemplate.execute(status -> {
            wordRepository.deleteAll();
            noteRepository.deleteAll();
            topicRepository.deleteAll();
            metadataRepository.deleteAll();
            userRepository.deleteAll();

            User user = User.builder()
                    .username("testuser_" + UUID.randomUUID())
                    .password("password123")
                    .email("testuser_" + UUID.randomUUID() + "@example.com")
                    .role(Role.ROLE_USER)
                    .build();
            savedUser = userRepository.save(user);

            UserSyncMetadata metadata = UserSyncMetadata.builder()
                    .user(savedUser)
                    .dailyWordCount(0)
                    .totalWordCount(0)
                    .build();
            metadataRepository.save(metadata);

            return null;
        });
    }

    @Test
    public void testSaveNoteAndWordRelation() {
        transactionTemplate.execute(status -> {
            // 1. Create Topic
            Topic topic = Topic.builder()
                    .user(savedUser)
                    .name("Daily English")
                    .build();
            Topic savedTopic = topicRepository.save(topic);

            // 2. Create Note
            Note note = Note.builder()
                    .user(savedUser)
                    .topic(savedTopic)
                    .title("Business vocabulary")
                    .content("Here are some notes from business meetings.")
                    .build();
            Note savedNote = noteRepository.save(note);

            // 3. Create Word
            Word word = Word.builder()
                    .user(savedUser)
                    .note(savedNote)
                    .text("negotiation")
                    .translation("sự đàm phán")
                    .partOfSpeech("Noun")
                    .ipa("nɪˌɡəʊʃɪˈeɪʃn")
                    .exampleContext("The negotiation was successful.")
                    .exampleTranslation("Cuộc đàm phán đã thành công.")
                    .status(WordStatus.NEW)
                    .nextReviewAt(LocalDate.now())
                    .build();
            Word savedWord = wordRepository.save(word);

            // Verify
            assertNotNull(savedTopic.getId());
            assertNotNull(savedNote.getId());
            assertNotNull(savedWord.getId());
            assertEquals(savedNote.getId(), savedWord.getNote().getId());
            assertEquals(savedTopic.getId(), savedNote.getTopic().getId());

            return null;
        });
    }

    @Test
    public void testOptimisticLockingOnWord() {
        // Save initial Word
        UUID wordId = transactionTemplate.execute(status -> {
            Word word = Word.builder()
                    .user(savedUser)
                    .text("concurrency")
                    .status(WordStatus.NEW)
                    .nextReviewAt(LocalDate.now())
                    .build();
            return wordRepository.save(word).getId();
        });

        // Simulating Transaction A loading Word
        Word wordA = transactionTemplate.execute(status -> wordRepository.findById(wordId).orElseThrow());

        // Simulating Transaction B loading Word
        Word wordB = transactionTemplate.execute(status -> wordRepository.findById(wordId).orElseThrow());

        // Transaction A updates first
        transactionTemplate.execute(status -> {
            wordA.setCorrectStreak(1);
            wordRepository.save(wordA);
            return null;
        });

        // Transaction B tries to update the stale version
        assertThrows(ObjectOptimisticLockingFailureException.class, () -> {
            transactionTemplate.execute(status -> {
                wordB.setCorrectStreak(2);
                wordRepository.save(wordB);
                return null;
            });
        });
    }

    @Test
    public void testManuallyAssignedIdsSync() {
        UUID manualTopicId = UUID.randomUUID();
        UUID manualNoteId = UUID.randomUUID();
        UUID manualWordId = UUID.randomUUID();

        transactionTemplate.execute(status -> {
            // Save manual Topic
            Topic topic = Topic.builder()
                    .id(manualTopicId)
                    .user(savedUser)
                    .name("Manual Topic")
                    .build();
            Topic savedTopic = topicRepository.save(topic);
            assertEquals(manualTopicId, savedTopic.getId());

            // Save manual Note
            Note note = Note.builder()
                    .id(manualNoteId)
                    .user(savedUser)
                    .topic(savedTopic)
                    .title("Manual Note")
                    .content("Manual Note Content")
                    .build();
            Note savedNote = noteRepository.save(note);
            assertEquals(manualNoteId, savedNote.getId());

            // Save manual Word
            Word word = Word.builder()
                    .id(manualWordId)
                    .user(savedUser)
                    .note(savedNote)
                    .text("manual")
                    .status(WordStatus.NEW)
                    .nextReviewAt(LocalDate.now())
                    .build();
            Word savedWord = wordRepository.save(word);
            assertEquals(manualWordId, savedWord.getId());

            return null;
        });

        // Verify entities can be retrieved with the manual IDs
        Optional<Topic> retrievedTopic = topicRepository.findById(manualTopicId);
        assertTrue(retrievedTopic.isPresent());
        assertEquals("Manual Topic", retrievedTopic.get().getName());

        Optional<Note> retrievedNote = noteRepository.findById(manualNoteId);
        assertTrue(retrievedNote.isPresent());
        assertEquals("Manual Note", retrievedNote.get().getTitle());

        Optional<Word> retrievedWord = wordRepository.findById(manualWordId);
        assertTrue(retrievedWord.isPresent());
        assertEquals("manual", retrievedWord.get().getText());
    }

    @Test
    public void testSyncBatchWithManuallyAssignedIdsAndVersions() {
        UUID manualTopicId = UUID.randomUUID();
        UUID manualNoteId = UUID.randomUUID();
        UUID manualWordId = UUID.randomUUID();

        // 1. Prepare SyncRequest DTOs
        com.learning.english.dto.SyncRequest.TopicDto topicDto = new com.learning.english.dto.SyncRequest.TopicDto(
                manualTopicId, "Sync Topic", System.currentTimeMillis(), System.currentTimeMillis()
        );
        com.learning.english.dto.SyncRequest.NoteDto noteDto = new com.learning.english.dto.SyncRequest.NoteDto(
                manualNoteId, manualTopicId, "Sync Note", "Sync Note Content", 1L, System.currentTimeMillis(), System.currentTimeMillis()
        );
        com.learning.english.dto.SyncRequest.WordDto wordDto = new com.learning.english.dto.SyncRequest.WordDto(
                manualWordId, manualNoteId, "sync", "đồng bộ", "Noun", "sɪŋk", "Let's sync.", "Hãy đồng bộ.",
                "NEW", 0, 2.0, 0, LocalDate.now().toString(), 0, 1L, System.currentTimeMillis(), System.currentTimeMillis()
        );

        com.learning.english.dto.SyncRequest request = new com.learning.english.dto.SyncRequest(
                java.util.List.of(topicDto),
                java.util.List.of(noteDto),
                java.util.List.of(wordDto),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of()
        );

        // 2. Execute syncBatch within ScopedValue binding of user principal
        com.learning.english.security.UserPrincipal principal = com.learning.english.security.UserPrincipal.create(savedUser);

        ScopedValue.where(com.learning.english.security.SecurityContextUtils.CURRENT_USER, principal).run(() -> {
            com.learning.english.dto.SyncResponse response = syncService.syncBatch(request, UUID.randomUUID().toString());
            assertNotNull(response);
            assertEquals("SUCCESS", response.getStatus());
        });

        // 3. Verify retrieved records from database have correct fields and values
        Optional<Topic> retrievedTopic = topicRepository.findById(manualTopicId);
        assertTrue(retrievedTopic.isPresent());
        assertEquals("Sync Topic", retrievedTopic.get().getName());

        Optional<Note> retrievedNote = noteRepository.findById(manualNoteId);
        assertTrue(retrievedNote.isPresent());
        assertEquals("Sync Note", retrievedNote.get().getTitle());
        assertNotNull(retrievedNote.get().getVersion());

        Optional<Word> retrievedWord = wordRepository.findById(manualWordId);
        assertTrue(retrievedWord.isPresent());
        assertEquals("sync", retrievedWord.get().getText());
        assertNotNull(retrievedWord.get().getVersion());
    }
}
