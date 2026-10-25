package com.learning.english.config;

import com.learning.english.constant.AppConstants;
import com.learning.english.constant.Role;
import com.learning.english.constant.WordStatus;
import com.learning.english.model.*;
import com.learning.english.repository.*;
import com.learning.english.util.TextNormalizationUtil;
import com.learning.english.util.TimezoneUtil;
import com.learning.english.util.UuidV7Generator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Component
public class DatabaseSeeder implements CommandLineRunner {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSyncMetadataRepository metadataRepository;

    @Autowired
    private UserAppConfigRepository appConfigRepository;

    @Autowired
    private TopicRepository topicRepository;

    @Autowired
    private NoteRepository noteRepository;

    @Autowired
    private UserWordRepository userWordRepository;

    @Autowired
    private WordContextRepository wordContextRepository;

    @Autowired
    private DailyStatsRepository dailyStatsRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    record SeedWordDef(
            String text,
            String primaryMeaning,
            String ipa,
            String partOfSpeech,
            String sentence,
            String sentenceTranslation
    ) {}

    private static final List<SeedWordDef> SAMPLE_WORDS = List.of(
            new SeedWordDef("linger", "nán lại, lưu lại", "/ˈlɪŋ.ɡər/", "verb",
                    "We lingered over coffee on a quiet Sunday.",
                    "Chúng tôi nán lại bên tách cà phê vào một ngày Chủ nhật yên tĩnh."),
            new SeedWordDef("cultivate", "trau dồi, nuôi dưỡng, rèn luyện", "/ˈkʌl.tɪ.veɪt/", "verb",
                    "Attention is a quiet habit we must cultivate every day.",
                    "Sự chú tâm là một thói quen tĩnh lặng mà chúng ta phải trau dồi mỗi ngày."),
            new SeedWordDef("ephemeral", "phù du, chóng tàn, ngắn ngủi", "/ɪˈfem.ər.əl/", "adjective",
                    "Social media notifications are ephemeral distractions.",
                    "Thông báo mạng xã hội là những xao nhãng phù du ngắn ngủi."),
            new SeedWordDef("fragmented", "bị phân mảnh, rời rạc", "/ˈfræɡ.men.tɪd/", "adjective",
                    "Modern work leaves our attention span deeply fragmented.",
                    "Công việc hiện đại khiến khả năng tập trung của chúng ta bị phân mảnh sâu sắc."),
            new SeedWordDef("contemplative", "trầm ngâm, sâu lắng", "/kənˈtem.plə.tɪv/", "adjective",
                    "Writing notes fosters a contemplative mindset.",
                    "Viết ghi chú thúc đẩy một tư duy trầm ngâm sâu lắng."),
            new SeedWordDef("clarity", "sự sáng sủa, minh bạch", "/ˈklær.ə.ti/", "noun",
                    "Attention creates space for creative clarity.",
                    "Sự chú tâm tạo không gian cho sự sáng suốt sáng tạo."),
            new SeedWordDef("deliberate", "có chủ đích, thận trọng", "/dɪˈlɪb.ər.ət/", "adjective",
                    "Paying attention requires deliberate practice.",
                    "Sự chú tâm đòi hỏi sự luyện tập có chủ đích."),
            new SeedWordDef("discipline", "kỷ luật, sự rèn luyện", "/ˈdɪs.ə.plɪn/", "noun",
                    "Maintaining focus is an exercise in mental discipline.",
                    "Duy trì sự tập trung là một bài tập rèn luyện kỷ luật tinh thần."),
            new SeedWordDef("meticulous", "tỉ mỉ, cẩn thận từng chi tiết", "/məˈtɪk.jə.ləs/", "adjective",
                    "He kept meticulous notes of his reading thoughts.",
                    "Anh ấy ghi chép tỉ mỉ những suy nghĩ khi đọc sách."),
            new SeedWordDef("solitude", "sự tĩnh lặng một mình", "/ˈsɒl.ɪ.tʃuːd/", "noun",
                    "Solitude allows deep reflection and clear thinking.",
                    "Sự tĩnh lặng một mình cho phép suy ngẫm sâu sắc và tư duy sáng tỏ."),
            new SeedWordDef("profound", "sâu sắc, uyên thâm", "/prəˈfaʊnd/", "adjective",
                    "The author made a profound observation about focus.",
                    "Tác giả đã đưa ra một quan sát sâu sắc về sự tập trung."),
            new SeedWordDef("serenity", "sự thanh thản, thanh bình", "/səˈren.ə.ti/", "noun",
                    "The morning stillness brought a rare sense of serenity.",
                    "Sự tĩnh lặng của buổi sáng mang lại cảm giác thanh thản hiếm có."),
            new SeedWordDef("wander", "đi lang thang, xao lãng", "/ˈwɒn.dər/", "verb",
                    "When the mind begins to wander, gently bring it back.",
                    "Khi tâm trí bắt đầu xao lãng, hãy nhẹ nhàng đưa nó trở lại."),
            new SeedWordDef("perspective", "góc nhìn, quan điểm", "/pəˈspek.tɪv/", "noun",
                    "Reading widely offers fresh perspective on life.",
                    "Đọc rộng mở ra góc nhìn mới mẻ về cuộc sống."),
            new SeedWordDef("anchor", "neo giữ, khắc sâu", "/ˈæŋ.kər/", "verb",
                    "Use note-taking to anchor your key insights.",
                    "Hãy dùng việc ghi chú để khắc sâu những hiểu biết then chốt."),
            new SeedWordDef("resilient", "kiên cường, bền bỉ", "/rɪˈzɪl.jənt/", "adjective",
                    "A resilient mind resists constant digital noise.",
                    "Một tâm trí kiên cường sẽ chống lại tiếng ồn kỹ thuật số liên tục."),
            new SeedWordDef("reluctant", "miễn cưỡng, bất đắc dĩ", "/rɪˈlʌk.tənt/", "adjective",
                    "She lingered at the door, reluctant to say goodbye.",
                    "Cô ấy nấn ná ở cửa, miễn cưỡng nói lời từ biệt."),
            new SeedWordDef("transient", "thoáng qua, tạm thời", "/ˈtræn.zi.ənt/", "adjective",
                    "Transient thoughts drift away like clouds.",
                    "Những ý nghĩ thoáng qua trôi đi như những đám mây."),
            new SeedWordDef("immerse", "đắm chìm, ngập tràn", "/ɪˈmɜːs/", "verb",
                    "To read deeply, one must immerse oneself in the text.",
                    "Để đọc sâu, người ta phải đắm mình vào trang sách."),
            new SeedWordDef("nuance", "sắc thái tinh tế", "/ˈnjuː.ɑːns/", "noun",
                    "Notice the subtle nuance in every sentence.",
                    "Hãy để ý sắc thái tinh tế trong từng câu văn.")
    );

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        User admin = userRepository.findByUsernameIgnoreCase(AppConstants.ADMIN_USERNAME)
                .orElseGet(() -> {
                    User u = User.builder()
                            .username(AppConstants.ADMIN_USERNAME)
                            .name("Admin User")
                            .password(passwordEncoder.encode(AppConstants.ADMIN_PASSWORD))
                            .email(AppConstants.ADMIN_EMAIL)
                            .role(Role.ROLE_ADMIN)
                            .build();
                    return userRepository.save(u);
                });
        seedUserData(admin);

        User user1 = userRepository.findByUsernameIgnoreCase(AppConstants.USER1_USERNAME)
                .orElseGet(() -> {
                    User u = User.builder()
                            .username(AppConstants.USER1_USERNAME)
                            .name("User 1")
                            .password(passwordEncoder.encode(AppConstants.USER1_PASSWORD))
                            .email(AppConstants.USER1_EMAIL)
                            .role(Role.ROLE_USER)
                            .build();
                    return userRepository.save(u);
                });
        seedUserData(user1);

        User user2 = userRepository.findByUsernameIgnoreCase(AppConstants.USER2_USERNAME)
                .orElseGet(() -> {
                    User u = User.builder()
                            .username(AppConstants.USER2_USERNAME)
                            .name("User 2")
                            .password(passwordEncoder.encode(AppConstants.USER2_PASSWORD))
                            .email(AppConstants.USER2_EMAIL)
                            .role(Role.ROLE_USER)
                            .build();
                    return userRepository.save(u);
                });
        seedUserData(user2);
    }

    private void seedUserData(User user) {
        if (appConfigRepository.findByUserId(user.getId()).isEmpty()) {
            UserAppConfig appConfig = UserAppConfig.builder()
                    .user(user)
                    .timezone("Asia/Ho_Chi_Minh")
                    .dailyTarget(20)
                    .sidebarWidth(244)
                    .themeMode("light")
                    .autoSpeak(false)
                    .build();
            appConfigRepository.save(appConfig);
        }

        UserSyncMetadata metadata = metadataRepository.findByUserId(user.getId())
                .orElseGet(() -> {
                    UserSyncMetadata m = UserSyncMetadata.builder()
                            .user(user)
                            .dailyWordCount(0)
                            .totalWordCount(0)
                            .build();
                    return metadataRepository.save(m);
                });

        Topic topic = topicRepository.findAllByUserIdAndDeletedAtIsNullOrderByCreatedAtAsc(user.getId()).stream()
                .filter(t -> "Personal growth".equalsIgnoreCase(t.getName()))
                .findFirst()
                .orElseGet(() -> {
                    Topic t = Topic.builder()
                            .id(UuidV7Generator.generate())
                            .user(user)
                            .name("Personal growth")
                            .build();
                    return topicRepository.save(t);
                });

        final String noteTitle = "The art of paying attention";
        Note note = noteRepository.findAllByUserIdAndDeletedAtIsNullOrderByUpdatedAtDesc(user.getId()).stream()
                .filter(n -> noteTitle.equalsIgnoreCase(n.getTitle()))
                .findFirst()
                .orElseGet(() -> {
                    String content = "Attention is not a static trait; it is a quiet habit we must cultivate every day. " +
                            "In an era dominated by ephemeral digital notifications, modern work leaves our attention span deeply fragmented. " +
                            "When we linger over a thoughtful book or practice contemplative note-taking, we create sanctuary for mental clarity. " +
                            "It requires deliberate discipline and meticulous care. Solitude and stillness bring a profound sense of serenity. " +
                            "When the mind begins to wander, gently guide it back. Reading widely offers a fresh perspective, allowing us to anchor key insights and remain resilient amidst the chaos.";
                    Note n = Note.builder()
                            .id(UuidV7Generator.generate())
                            .user(user)
                            .topic(topic)
                            .title(noteTitle)
                            .content(content)
                            .build();
                    return noteRepository.save(n);
                });

        ZoneId zoneId = ZoneId.of("Asia/Ho_Chi_Minh");
        Instant todayStart = TimezoneUtil.startOfDayInstant(TimezoneUtil.today(zoneId), zoneId);
        int seededWordCount = 0;

        for (SeedWordDef def : SAMPLE_WORDS) {
            String norm = TextNormalizationUtil.normalizeText(def.text());
            if (userWordRepository.findByUserIdAndTextNormalizedAndDeletedAtIsNull(user.getId(), norm).isEmpty()) {
                UserWord userWord = UserWord.builder()
                        .id(UuidV7Generator.generate())
                        .user(user)
                        .text(def.text())
                        .textNormalized(norm)
                        .primaryMeaning(def.primaryMeaning())
                        .ipa(def.ipa())
                        .status(WordStatus.NEW)
                        .correctStreak(0)
                        .easeFactor(2.5)
                        .intervalDays(0)
                        .nextReviewAt(todayStart)
                        .failedEssayStreak(0)
                        .build();

                WordContext context = WordContext.builder()
                        .id(UuidV7Generator.generate())
                        .user(user)
                        .userWord(userWord)
                        .note(note)
                        .noteTitleSnapshot(note.getTitle())
                        .partOfSpeech(def.partOfSpeech())
                        .meaning(def.primaryMeaning())
                        .sentence(def.sentence())
                        .sentenceTranslation(def.sentenceTranslation())
                        .build();

                userWord.getContexts().add(context);
                userWordRepository.save(userWord);
                seededWordCount++;
            }
        }

        if (seededWordCount > 0) {
            int total = (int) userWordRepository.countByUserIdAndDeletedAtIsNull(user.getId());
            metadata.setTotalWordCount(total);
            metadata.setNewCount((int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(user.getId(), WordStatus.NEW));
            metadata.setLearningCount((int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(user.getId(), WordStatus.LEARNING));
            metadata.setRememberedCount((int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(user.getId(), WordStatus.TEMP_REMEMBERED));
            metadata.setMasteredCount((int) userWordRepository.countByUserIdAndStatusAndDeletedAtIsNull(user.getId(), WordStatus.MASTERED));
            metadataRepository.save(metadata);

            LocalDate today = TimezoneUtil.today(zoneId);
            DailyStats stats = dailyStatsRepository.findByUserIdAndStudyDate(user.getId(), today)
                    .orElseGet(() -> DailyStats.builder()
                            .id(UuidV7Generator.generate())
                            .user(user)
                            .studyDate(today)
                            .wordsReviewed(0)
                            .wordsAdded(0)
                            .correctCount(0)
                            .incorrectCount(0)
                            .timeSpentSeconds(0)
                            .build());
            stats.setWordsAdded(seededWordCount);
            dailyStatsRepository.save(stats);
        }
    }
}
