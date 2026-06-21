package com.learning.english;

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
}
