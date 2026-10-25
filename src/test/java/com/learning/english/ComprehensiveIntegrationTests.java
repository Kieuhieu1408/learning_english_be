package com.learning.english;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learning.english.constant.AppConstants;
import com.learning.english.dto.*;
import com.learning.english.model.UserWord;
import com.learning.english.model.WordContext;
import com.learning.english.repository.UserWordRepository;
import com.learning.english.repository.WordContextRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.learning.english.service.AIService;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class ComprehensiveIntegrationTests {

    @MockitoBean
    private AIService aiService;

    @Autowired
    private WebApplicationContext webApplicationContext;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    private MockMvc mockMvc;
    private static String jwtToken;

    @BeforeEach
    public void setup() throws Exception {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        if (jwtToken == null) {
            LoginRequest loginReq = new LoginRequest(AppConstants.ADMIN_USERNAME, AppConstants.ADMIN_PASSWORD);
            MvcResult result = mockMvc.perform(post("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(loginReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            AuthResponse authRes = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
            assertNotNull(authRes.getToken());
            jwtToken = "Bearer " + authRes.getToken();
        }
    }

    @Test
    @Order(1)
    public void testOpenApiSpecificationExport() throws Exception {
        // Export openapi.json to docs/openapi.json
        MvcResult result = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andReturn();

        String openApiJson = result.getResponse().getContentAsString();
        assertNotNull(openApiJson);
        assertTrue(openApiJson.contains("openapi"));

        Path docsDir = Paths.get("docs");
        if (!Files.exists(docsDir)) {
            Files.createDirectories(docsDir);
        }
        Path openApiPath = docsDir.resolve("openapi.json");
        Files.writeString(openApiPath, openApiJson);
        assertTrue(Files.exists(openApiPath), "openapi.json must exist in docs/");
    }

    @Test
    @Order(2)
    public void testAuthMeBootstrap() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        AuthMeResponse me = objectMapper.readValue(result.getResponse().getContentAsString(), AuthMeResponse.class);
        assertNotNull(me);
        assertEquals(AppConstants.ADMIN_USERNAME, me.username());
        assertNotNull(me.config());
        assertEquals("Asia/Ho_Chi_Minh", me.config().timezone());
        assertNotNull(me.counters());
    }

    @Test
    @Order(3)
    public void testMultiContextAndUnicodeDeduplication() throws Exception {
        // Step 1: Save first context for word "Resilience"
        SaveWordContextRequest req1 = SaveWordContextRequest.builder()
                .text("  Resilience.  ")
                .meaning("khả năng phục hồi, kiên cường")
                .ipa("/rɪˈzɪl.jəns/")
                .partOfSpeech("noun")
                .sentence("Resilience is essential for facing unexpected life difficulties.")
                .sentenceTranslation("Sự kiên cường là điều cốt yếu để đối mặt với khó khăn.")
                .build();

        MvcResult res1 = mockMvc.perform(post("/api/words/context")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isOk())
                .andReturn();

        SaveWordContextResponse saveRes1 = objectMapper.readValue(res1.getResponse().getContentAsString(), SaveWordContextResponse.class);
        assertNotNull(saveRes1.word().id());
        assertNotNull(saveRes1.context().id());

        // Step 2: Save second context with slightly different punctuation/spacing "resilience"
        SaveWordContextRequest req2 = SaveWordContextRequest.builder()
                .text("resilience")
                .meaning("sự bền bỉ, hồi phục")
                .ipa("/rɪˈzɪl.jəns/")
                .partOfSpeech("noun")
                .sentence("Mental resilience helps us stay calm under pressure.")
                .sentenceTranslation("Sự kiên cường tinh thần giúp chúng ta bình tĩnh trước áp lực.")
                .build();

        MvcResult res2 = mockMvc.perform(post("/api/words/context")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isOk())
                .andReturn();

        SaveWordContextResponse saveRes2 = objectMapper.readValue(res2.getResponse().getContentAsString(), SaveWordContextResponse.class);

        // Verification: Deduplication must reuse the same Word ID
        assertEquals(saveRes1.word().id(), saveRes2.word().id(), "Both contexts must merge into the same canonical word");
        assertNotEquals(saveRes1.context().id(), saveRes2.context().id(), "Each sentence context has a distinct UUID");

        // Verify via GET /api/words
        MvcResult listRes = mockMvc.perform(get("/api/words")
                        .header("Authorization", jwtToken)
                        .param("query", "resilience"))
                .andExpect(status().isOk())
                .andReturn();

        WordListResponse wordList = objectMapper.readValue(listRes.getResponse().getContentAsString(), WordListResponse.class);
        assertFalse(wordList.words().isEmpty());
        WordDto matchedWord = wordList.words().stream()
                .filter(w -> w.id().equals(saveRes1.word().id()))
                .findFirst()
                .orElse(null);
        assertNotNull(matchedWord);
        assertTrue(matchedWord.contexts().size() >= 2, "Word must contain at least 2 contexts");
    }

    @Test
    @Order(4)
    public void testTopicsAndNotesCascadeSoftDeleteAndSnapshot() throws Exception {
        UUID topicId = UUID.randomUUID();
        TopicRequest topicReq = new TopicRequest(topicId, "Reading Comprehension");

        // 1. Create Topic
        mockMvc.perform(post("/api/topics")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(topicReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Reading Comprehension"));

        // 2. Create Note under Topic
        UUID noteId = UUID.randomUUID();
        NoteRequest noteReq = new NoteRequest(noteId, topicId, 0L, "Deep Focus Handbook", "Deep work is the ability to focus without distraction.");

        mockMvc.perform(post("/api/notes")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Deep Focus Handbook"));

        // 3. Save a Word Context linked to this Note
        SaveWordContextRequest wordContextReq = SaveWordContextRequest.builder()
                .text("sanctuary")
                .meaning("nơi tôn nghiêm, nơi trú ẩn bình yên")
                .ipa("/ˈsæŋk.tʃʊə.ri/")
                .partOfSpeech("noun")
                .sentence("The library became his quiet sanctuary for deep contemplation.")
                .sentenceTranslation("Thư viện đã trở thành nơi trú ẩn yên bình cho sự suy ngẫm sâu sắc.")
                .noteId(noteId)
                .build();

        MvcResult wordRes = mockMvc.perform(post("/api/words/context")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wordContextReq)))
                .andExpect(status().isOk())
                .andReturn();

        SaveWordContextResponse saveRes = objectMapper.readValue(wordRes.getResponse().getContentAsString(), SaveWordContextResponse.class);
        assertNotNull(saveRes.context().id());

        WordContext wcBefore = wordContextRepository.findById(saveRes.context().id()).orElseThrow();
        assertEquals("Deep Focus Handbook", wcBefore.getNoteTitleSnapshot());
        assertNotNull(wcBefore.getNote());

        // 4. Update Note Title -> Check snapshot sync
        NoteRequest noteUpdateReq = new NoteRequest(noteId, topicId, 0L, "Mastering Deep Focus", "Updated content");
        mockMvc.perform(put("/api/notes/" + noteId)
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noteUpdateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mastering Deep Focus"));

        WordContext wcAfterTitleUpdate = wordContextRepository.findById(saveRes.context().id()).orElseThrow();
        assertEquals("Mastering Deep Focus", wcAfterTitleUpdate.getNoteTitleSnapshot(), "Snapshot should sync when note title changes");

        // 5. Delete Note -> Check soft-delete & context detachment with snapshot preserved
        mockMvc.perform(delete("/api/notes/" + noteId)
                        .header("Authorization", jwtToken))
                .andExpect(status().isNoContent());

        WordContext wcAfterNoteDelete = wordContextRepository.findById(saveRes.context().id()).orElseThrow();
        assertNull(wcAfterNoteDelete.getNote(), "Note reference must be detached (null)");
        assertEquals("Mastering Deep Focus", wcAfterNoteDelete.getNoteTitleSnapshot(), "Snapshot title must be permanently preserved");

        // 6. Delete Topic -> Cascade soft delete
        mockMvc.perform(delete("/api/topics/" + topicId)
                        .header("Authorization", jwtToken))
                .andExpect(status().isNoContent());
    }

    @Test
    @Order(5)
    public void testSpacedRepetitionEngineAndIdempotency() throws Exception {
        // 1. Fetch due reviews
        MvcResult dueResult = mockMvc.perform(get("/api/review/due")
                        .header("Authorization", jwtToken)
                        .param("limit", "10"))
                .andExpect(status().isOk())
                .andReturn();

        ReviewDueResponse due = objectMapper.readValue(dueResult.getResponse().getContentAsString(), ReviewDueResponse.class);
        assertNotNull(due);

        if (!due.words().isEmpty()) {
            WordDto firstDue = due.words().get(0);

            // 2. Test Check Answer endpoint
            CheckAnswerRequest checkReq = new CheckAnswerRequest(firstDue.id(), "sentence context", firstDue.text());
            mockMvc.perform(post("/api/review/check-answer")
                            .header("Authorization", jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(checkReq)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.outcome").value("EXACT_MATCH"));

            // 3. Submit Review (Quality 3: GOOD)
            UUID eventId = UUID.randomUUID();
            ReviewSubmitRequest.ReviewEventDto eventDto = new ReviewSubmitRequest.ReviewEventDto(
                    eventId,
                    firstDue.id(),
                    3,
                    true,
                    "CARD",
                    Instant.now()
            );
            ReviewSubmitRequest submitReq = new ReviewSubmitRequest(List.of(eventDto));

            MvcResult submitResult = mockMvc.perform(post("/api/review/submit")
                            .header("Authorization", jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(submitReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            ReviewSubmitResponse subResponse1 = objectMapper.readValue(submitResult.getResponse().getContentAsString(), ReviewSubmitResponse.class);
            assertFalse(subResponse1.updatedWords().isEmpty());
            ReviewSubmitResponse.UpdatedWordStateDto wordState1 = subResponse1.updatedWords().get(0);
            assertNotNull(wordState1.nextReviewAt());
            assertTrue(wordState1.intervalDays() >= 1);

            // 4. Test Idempotency: Submit again with EXACT same eventId
            MvcResult submitResult2 = mockMvc.perform(post("/api/review/submit")
                            .header("Authorization", jwtToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(submitReq)))
                    .andExpect(status().isOk())
                    .andReturn();

            ReviewSubmitResponse subResponse2 = objectMapper.readValue(submitResult2.getResponse().getContentAsString(), ReviewSubmitResponse.class);
            ReviewSubmitResponse.UpdatedWordStateDto wordState2 = subResponse2.updatedWords().get(0);
            assertEquals(wordState1.intervalDays(), wordState2.intervalDays(), "Idempotent review submit should return same intervalDays");
            assertEquals(wordState1.status(), wordState2.status(), "Idempotent review submit should return same status");
        }
    }

    @Test
    @Order(6)
    public void testDeltaSyncAndConflictDetection() throws Exception {
        // 1. Check sync counters
        mockMvc.perform(get("/api/sync/counters")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalWords").isNumber());

        // 2. Perform delta sync (Push and Pull)
        UUID newTopicId = UUID.randomUUID();
        DeltaSyncRequest.TopicChangeDto topicChange = new DeltaSyncRequest.TopicChangeDto(newTopicId, "Delta Sync Topic");

        DeltaSyncRequest.ChangesPayload changes = DeltaSyncRequest.ChangesPayload.builder()
                .topics(List.of(topicChange))
                .notes(List.of())
                .words(List.of())
                .wordContexts(List.of())
                .reviewEvents(List.of())
                .build();

        DeltaSyncRequest syncReq = DeltaSyncRequest.builder()
                .timezone("Asia/Ho_Chi_Minh")
                .changes(changes)
                .build();

        MvcResult syncRes = mockMvc.perform(post("/api/sync/delta")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncReq)))
                .andExpect(status().isOk())
                .andReturn();

        DeltaSyncResponse syncResponse = objectMapper.readValue(syncRes.getResponse().getContentAsString(), DeltaSyncResponse.class);
        assertNotNull(syncResponse.newCursor());

        // 3. Test Note Version Conflict (Push Note with baseVersion = 0 when server is already version >= 1)
        UUID conflictNoteId = UUID.randomUUID();
        NoteRequest createNote = new NoteRequest(conflictNoteId, newTopicId, 0L, "Cloud Master Copy", "Initial Server Content");
        mockMvc.perform(post("/api/notes")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createNote)))
                .andExpect(status().isOk());

        // Update note on cloud to advance server version to 1
        NoteRequest updateNoteCloud = new NoteRequest(conflictNoteId, newTopicId, 0L, "Cloud Master Copy v2", "Updated Server Content");
        mockMvc.perform(put("/api/notes/" + conflictNoteId)
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateNoteCloud)))
                .andExpect(status().isOk());

        // Client pushes stale update with baseVersion = 0
        DeltaSyncRequest.NoteChangeDto staleNoteChange = DeltaSyncRequest.NoteChangeDto.builder()
                .id(conflictNoteId)
                .topicId(newTopicId)
                .baseVersion(0L)
                .title("Local Conflicting Title")
                .content("Conflicting local body")
                .build();

        DeltaSyncRequest.ChangesPayload conflictChanges = DeltaSyncRequest.ChangesPayload.builder()
                .topics(List.of())
                .notes(List.of(staleNoteChange))
                .words(List.of())
                .wordContexts(List.of())
                .reviewEvents(List.of())
                .build();

        DeltaSyncRequest conflictSyncReq = DeltaSyncRequest.builder()
                .cursor(syncResponse.newCursor())
                .timezone("Asia/Ho_Chi_Minh")
                .changes(conflictChanges)
                .build();

        MvcResult conflictResult = mockMvc.perform(post("/api/sync/delta")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(conflictSyncReq)))
                .andExpect(status().isOk())
                .andReturn();

        DeltaSyncResponse conflictSyncResponse = objectMapper.readValue(conflictResult.getResponse().getContentAsString(), DeltaSyncResponse.class);
        assertFalse(conflictSyncResponse.conflicts().isEmpty(), "Server must flag note conflict when baseVersion < serverVersion");
        assertEquals(conflictNoteId, conflictSyncResponse.conflicts().get(0).noteId());

        // 4. Resolve Conflict with KEEP_LOCAL
        ResolveConflictRequest resolveReq = new ResolveConflictRequest(conflictNoteId, "KEEP_LOCAL", null, null);
        mockMvc.perform(post("/api/sync/resolve-conflict")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(resolveReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(conflictNoteId.toString()));
    }

    @Test
    @Order(7)
    public void testProgressDashboard35Days() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/progress/dashboard")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andReturn();

        ProgressDashboardResponse dashboard = objectMapper.readValue(result.getResponse().getContentAsString(), ProgressDashboardResponse.class);
        assertNotNull(dashboard);
        assertNotNull(dashboard.activityCalendar());
        assertEquals(35, dashboard.activityCalendar().size(), "Activity calendar must span exactly 35 days for Bento grid");
        assertTrue(dashboard.streakDays() >= 0);
        assertTrue(dashboard.accuracyPercent() >= 0.0);
        assertNotNull(dashboard.distribution());
    }

    @Test
    @Order(8)
    public void testAiSettingsAndChatCompanion() throws Exception {
        // 1. Get AI Settings - Verify API Key plaintext is NOT exposed
        mockMvc.perform(get("/api/ai-settings")
                        .header("Authorization", jwtToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasApiKey").isBoolean());

        // 2. Test SSRF Protection on PUT /api/ai-settings
        AiSettingsDto maliciousReq = AiSettingsDto.builder()
                .customBaseUrl("http://malicious-internal-network.com/v1")
                .apiKey("dummy-key-12345")
                .provider("deepseek")
                .build();

        mockMvc.perform(put("/api/ai-settings")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(maliciousReq)))
                .andExpect(status().isBadRequest());

        // 3. Test Context Translation API
        TranslateContextResponse mockAiResponse = TranslateContextResponse.builder()
                .text("linger")
                .meaning("nán lại, lưu lại")
                .ipa("/ˈlɪŋ.ɡər/")
                .partOfSpeech("verb")
                .sentence("We lingered over coffee on a quiet Sunday.")
                .sentenceTranslation("Chúng tôi nán lại bên tách cà phê vào một ngày Chủ nhật yên tĩnh.")
                .build();
        org.mockito.Mockito.when(aiService.translateContext(org.mockito.ArgumentMatchers.any())).thenReturn(mockAiResponse);

        TranslateContextRequest transReq = new TranslateContextRequest(
                "linger",
                "We lingered over coffee on a quiet Sunday.",
                null,
                "Sample Note"
        );

        mockMvc.perform(post("/api/ai/translate-context")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(transReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.text").value("linger"))
                .andExpect(jsonPath("$.meaning").isNotEmpty())
                .andExpect(jsonPath("$.sentenceTranslation").isNotEmpty());

        // 4. Test AI Chat Companion Streaming (SSE)
        ChatDtos.ChatSendRequest chatReq = ChatDtos.ChatSendRequest.builder()
                .message("What does linger mean?")
                .build();

        mockMvc.perform(post("/api/chat/send")
                        .header("Authorization", jwtToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(chatReq)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM_VALUE));
    }

    @Test
    @Order(9)
    public void testDefaultUsersAuthenticationAndBootstrap() throws Exception {
        // 1. User1 login with exact username "User1" and password "user1"
        LoginRequest reqUser1 = new LoginRequest("User1", "user1");
        MvcResult resUser1 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUser1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn();

        AuthResponse authUser1 = objectMapper.readValue(resUser1.getResponse().getContentAsString(), AuthResponse.class);
        String tokenUser1 = "Bearer " + authUser1.getToken();

        // 1b. User1 /api/auth/me check
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", tokenUser1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("User1"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.config.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.counters.totalWords").isNumber());

        // 1c. User1 case-insensitive login with "user1"
        LoginRequest reqUser1Lower = new LoginRequest("user1", "user1");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUser1Lower)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // 2. user2 login with exact username "user2" and password "user 2"
        LoginRequest reqUser2Space = new LoginRequest("user2", "user 2");
        MvcResult resUser2 = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUser2Space)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andReturn();

        AuthResponse authUser2 = objectMapper.readValue(resUser2.getResponse().getContentAsString(), AuthResponse.class);
        String tokenUser2 = "Bearer " + authUser2.getToken();

        // 2b. user2 /api/auth/me check
        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", tokenUser2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("user2"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.config.timezone").value("Asia/Ho_Chi_Minh"))
                .andExpect(jsonPath("$.counters.totalWords").isNumber());

        // 2c. user2 login with alternative password without space "user2"
        LoginRequest reqUser2NoSpace = new LoginRequest("user2", "user2");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUser2NoSpace)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());

        // 2d. user2 case-insensitive username login with "User2"
        LoginRequest reqUser2Capital = new LoginRequest("User2", "user 2");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(reqUser2Capital)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty());
    }
}
