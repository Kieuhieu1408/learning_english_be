# KẾ HOẠCH REFACTOR BACKEND & PHÁT TRIỂN API MỚI (`plan.md`)
## Dự án: Smart English Notes (Target UX/UI: **margin.**)
### Phiên bản: **v2.1.0 (Single-Contract Standard)** — Ngày cập nhật: 2026-10-16

| Thông tin tài liệu | Chi tiết |
| :--- | :--- |
| **Căn cứ thiết kế** | [`BRD.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/BRD.md) (v2.1), [`answer.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/answer.md) (Đợt 1 & 2), [`api-contract.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/api-contract.md), [`UX_new.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_fe/docs/UX_new.md), [`UI_new.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_fe/docs/UI_new.md) |
| **Tech Stack** | Java 25 (Virtual Threads), Spring Boot 4.0.6, PostgreSQL 17 (Hash Partitioning + HOT Update), Spring Event Bus, SSE Streaming (`9Router` / DeepSeek / Gemini), Flyway/DDL migration, springdoc-openapi |
| **Mục tiêu sử dụng** | Làm lộ trình chuẩn (Single Source of Truth) xuyên suốt các phiên chat. **Sau khi hoàn thành mỗi bước, hãy đánh dấu `[x]` vào checklist tương ứng.** |

---

## 📊 BẢNG THEO DÕI TIẾN ĐỘ TỔNG QUAN (SESSION PROGRESS TRACKER)

- [x] **Phase 1**: Nền tảng Hạ tầng, Timezone, Soft-Delete (`deleted_at`), UUIDv7 & Database Schema (Partitioning + HOT Update) (HOÀN THÀNH)
- [x] **Phase 2**: Refactor Domain Models, Shared Unicode Normalization & Multi-Context Vocabulary Engine (`UserWord` + `WordContext`) (HOÀN THÀNH)
- [x] **Phase 3**: Spaced Repetition (SRS) Engine & Event-Sourced Review Pipeline (`eventId` Idempotency, Batch Submit, AI Synonym Check) (HOÀN THÀNH)
- [x] **Phase 4**: Delta Sync Engine (`POST /api/sync/delta`), Tombstones, BaseVersion Conflict Resolution & Multi-Device ID Remapping (HOÀN THÀNH)
- [x] **Phase 5**: Progress, Streak & 35-Day Timezone-Aware Dashboard (`GET /api/progress/dashboard`) & Midnight Scheduler (HOÀN THÀNH)
- [x] **Phase 6**: Topics & Notes CRUD with Cascade Soft-Delete, Snapshot Preservation & Idempotent Upsert (HOÀN THÀNH)
- [x] **Phase 7**: Unified AI Gateway (`9Router` / OpenAI / Gemini), Context Translation & AI Chat Companion (SSE Streaming) (HOÀN THÀNH)
- [x] **Phase 8**: Tối ưu hóa Bộ nhớ Đệm (Caffeine Due Cache & Pre-warming Pipeline - Tùy chọn) (HOÀN THÀNH)
- [x] **Phase 9**: Kiểm thử Tích hợp (Integration Tests), OpenAPI Specification (`openapi.json`) & Nghiệm thu toàn bộ API (HOÀN THÀNH)

---

## 1. Tóm Tắt Các Quyết Định Kiến Trúc Đã Chốt (Đợt 1 & Đợt 2)

1. **Mô hình Giao tiếp (Câu 8.1 - Chọn A)**:
   - **Local-First Alignment**: Frontend đọc/ghi cục bộ vào IndexedDB để đạt tốc độ phản hồi tức thì (`< 10ms`). Backend là **nguồn chân lý khi hợp nhất (Consolidation)** và là nơi hydrate dữ liệu cho thiết bị mới.
2. **Quy tắc Hợp đồng API (Câu 8.2 - Chọn A)**:
   - Backend sở hữu hợp đồng chính thức thông qua OpenAPI (`springdoc-openapi`).
   - Mỗi trường dữ liệu chỉ có **một tên chuẩn duy nhất** (xem chi tiết tại [`api-contract.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/api-contract.md)).
3. **Mô hình Dữ liệu Từ vựng Đa Ngữ cảnh (Câu 9.1, 9.2, 9.3 - Chọn A)**:
   - `user_words` giữ trường `primary_meaning` (nghĩa đại diện hiển thị trên flashcard/danh sách).
   - `word_contexts` lưu các câu ngữ cảnh với các trường chuẩn: `meaning` (nghĩa ngữ cảnh), `partOfSpeech`, `sentence`, `sentenceTranslation`, `noteId`, `noteTitleSnapshot`.
   - Hàm `normalizeText` Unicode đồng bộ: `trim -> lowercase -> bỏ ký tự không phải chữ ở hai đầu -> gộp khoảng trắng thừa`.
   - Chống trùng đa thiết bị: Tự động gộp theo `text_normalized` và trả bảng ánh xạ `idRemap: { clientId -> serverId }`.
   - Xóa Topic: Cascade soft-delete các Note bên trong, ngữ cảnh từ vựng được bảo toàn an toàn với `note_id = NULL` và `note_title_snapshot`.
4. **Thuật toán SRS & Event-Sourcing (Câu 10.1, 10.2, 10.3, 10.4, 10.5 - Chọn A)**:
   - Bậc giãn cách chuẩn: `1 -> 3 -> 7 -> 14` ngày rồi nhân với `easeFactor` (mặc định `2.5`, min `1.3`, quên thì `-0.2`).
   - Ngưỡng trạng thái: `NEW` (chưa ôn bao giờ) $\rightarrow$ `LEARNING` (streak 0..1, đã ôn $\ge 1$ lần) $\rightarrow$ `TEMP_REMEMBERED` (streak 2..4) $\rightarrow$ `MASTERED` (streak $\ge 5$ và interval $\ge 14$ ngày).
   - Event-Sourcing cho Review: Client sinh `eventId` (UUIDv7). Backend idempotent, tính lại trạng thái SM-2 đồng bộ trong bộ nhớ và ghi DB bất đồng bộ.
   - Hàng đợi `/api/review/due`: Phân trang bằng `excludeIds`, loại bỏ `offset` để không bỏ sót thẻ khi danh sách co lại.
   - Chấm điểm điền từ: Frontend tự chấm exact match & typo $\le 1$ ký tự; chỉ gọi `POST /api/review/check-answer` khi sai để AI kiểm tra từ đồng nghĩa ngữ cảnh (`SYNONYM_SUGGESTION`).
5. **Đồng bộ Vi sai & Quản lý Xung đột (Câu 11.1, 11.2, 11.3, 11.4 - Chọn A)**:
   - `POST /api/sync/delta`: Sử dụng con trỏ thời gian `cursor` do Server cấp.
   - Xử lý bản ghi đã xóa: Cột `deleted_at` (soft-delete tombstone) được gửi về client để xóa sạch bản ghi cục bộ trên các thiết bị.
   - Versioning của Note: Server là nơi duy nhất tăng `version`. Client gửi `baseVersion`. Lệch version và khác nội dung $\rightarrow$ trả `conflicts` để mở `SyncConflictModal`.
   - Định danh: Client sinh UUIDv7. Mọi API tạo mới đều là upsert idempotent theo `id`. Client áp dụng **Single-Write Path** qua `syncQueue`.
6. **Múi giờ & Thống kê Bento Grid (Câu 12.1, 12.2 - Chọn A)**:
   - Người dùng có cấu hình `timezone` (mặc định `Asia/Ho_Chi_Minh`). Mọi mốc ngày (`study_date`, `next_review_at`, streak) đều tính theo múi giờ này.
   - API `GET /api/progress/dashboard`: Chuẩn hóa tên theo các trường Frontend đang sử dụng (`accuracyPercent`, `reviewedToday`, `dailyTarget`, `wordsAddedToday`, `totalWords`, `streakDays`, `longestStreakDays`, `distribution`, `activityCalendar` 35 ngày).
7. **AI Gateway, 9Router & AI Chat (Câu 13.1, 13.2, 13.3, 13.4 - Chọn A)**:
   - Cấu hình an toàn: `PUT /api/ai-settings` chỉ ghi, `GET /api/ai-settings` không bao giờ trả API key thật.
   - `customBaseUrl` có danh sách cho phép (allow-list) chống lỗ hổng SSRF.
   - AI Chat: Hoạt động online-only. Server quản lý lịch sử duy nhất trong PostgreSQL. Client gọi qua `fetch` + `ReadableStream` nhận Server-Sent Events (`text/event-stream`).

---

## 2. Bản Đồ Toàn Bộ API Chuẩn

| Phân hệ | Method & Endpoint | Mô tả theo Hợp đồng [`api-contract.md`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/api-contract.md) |
| :--- | :--- | :--- |
| **Auth & Bootstrap** | `POST /api/auth/register` | Đăng ký tài khoản mới, khởi tạo preferences và metadata |
| | `POST /api/auth/login` | Đăng nhập, cấp JWT token |
| | `GET /api/auth/me` | Lấy profile, preferences (`timezone`, `dailyTarget`), và counters |
| **Topics** | `GET /api/topics` | Lấy danh sách Topic (`noteCount`, `deleted_at IS NULL`) |
| | `POST /api/topics` | Tạo mới / Upsert Topic (nhận `id` UUIDv7 từ client) |
| | `PUT /api/topics/{id}` | Đổi tên Topic |
| | `DELETE /api/topics/{id}` | Cascade soft-delete Topic và các Note bên trong |
| **Notes** | `GET /api/notes` | Lấy danh sách Note (`?topicId=`, trả kèm `harvestedWordCount`) |
| | `GET /api/notes/{id}` | Lấy chi tiết Note kèm danh sách từ vựng đã highlight |
| | `POST /api/notes` | Tạo mới / Upsert Note (nhận `id` UUIDv7 từ client) |
| | `PUT /api/notes/{id}` | Cập nhật Note (nhận `baseVersion`, phát hiện xung đột 409) |
| | `DELETE /api/notes/{id}` | Safe soft-delete Note (detach contexts, giữ `note_title_snapshot`) |
| **Vocabulary** | `GET /api/words` | Tra cứu kho từ vựng (`?q=&status=&sortBy=&limit=&offset=`) |
| | `POST /api/words/context` | Lưu từ vựng mới hoặc nối thêm ngữ cảnh (giữ nguyên SRS) |
| | `PUT /api/words/{id}` | Chỉnh sửa từ vựng hoặc nghĩa chính |
| | `DELETE /api/words/{id}` | Soft-delete từ vựng và các ngữ cảnh |
| | `DELETE /api/words/{wordId}/contexts/{ctxId}` | Soft-delete 1 ngữ cảnh cụ thể |
| **Review & SRS** | `GET /api/review/due` | Lấy danh sách thẻ đến hạn (`?limit=15&excludeIds=...`) |
| | `POST /api/review/submit` | Nộp danh sách sự kiện ôn tập (`events: [...]`), trả word states |
| | `POST /api/review/check-answer` | Chẩn đoán đáp án điền từ & từ đồng nghĩa bằng AI |
| **Progress & Analytics** | `GET /api/progress/dashboard` | Trả số liệu Bento Grid 35 ngày theo múi giờ user (`< 2ms`) |
| **AI & Settings** | `GET /api/ai-settings` | Lấy cấu hình AI (an toàn, không trả key plaintext) |
| | `PUT /api/ai-settings` | Lưu cấu hình AI (mã hóa AES-256, kiểm tra SSRF allow-list) |
| | `POST /api/ai-settings/test` | Test kết nối tới 9Router/Provider và tải danh sách model thực |
| | `POST /api/ai/translate-context` | Dịch từ theo câu ngữ cảnh trong bài Note |
| **AI Chat Companion** | `GET /api/chat/conversations` | Lấy danh sách lịch sử hội thoại |
| | `POST /api/chat/conversations` | Tạo hội thoại mới (nhận `id` UUIDv7 từ client) |
| | `GET /api/chat/conversations/{id}/messages` | Lấy lịch sử tin nhắn của cuộc hội thoại |
| | `PUT /api/chat/conversations/{id}` | Đổi tên hội thoại |
| | `DELETE /api/chat/conversations/{id}` | Xóa hội thoại và tin nhắn |
| | `POST /api/chat/send` | Gửi tin nhắn & Stream phản hồi qua SSE (`text/event-stream`) |
| **Delta Sync & Conflict** | `POST /api/sync/delta` | Đồng bộ vi sai hai chiều (`cursor`, `tombstones`, `idRemap`) |
| | `POST /api/sync/resolve-conflict` | Giải quyết xung đột Note (`KEEP_LOCAL` hoặc `APPLY_CLOUD`) |
| | `GET /api/sync/counters` | Lấy nhanh bộ đếm cho Header (`dueCount`, `reviewedToday`, v.v.) |

---

## 3. Kế Hoạch Chi Tiết Từng Bước & Checklist Thực Thi

---

### 🔹 PHASE 1: Nền tảng Hạ tầng, Timezone, Soft-Delete, UUIDv7 & Database Schema

**Mục tiêu**: Bổ sung các thư viện cốt lõi, tiện ích sinh UUIDv7 RFC 9562, cấu hình Async Virtual Threads, và thiết lập DDL PostgreSQL hỗ trợ Partitioning, HOT Update (`fillfactor=80`), và Soft Delete (`deleted_at`).

#### Checklist Phase 1:
- [x] **P1.1 - Cập nhật [`pom.xml`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/pom.xml)**:
  - Thêm `org.springdoc:springdoc-openapi-starter-webmvc-ui` (OpenAPI 3 / Swagger tự động).
  - Thêm `com.fasterxml.uuid:java-uuid-generator` (RFC 9562 UUIDv7).
  - Thêm `spring-boot-starter-validation`, `spring-boot-starter-cache` và `caffeine`.
- [x] **P1.2 - Tạo tiện ích sinh UUIDv7 [`util/UuidV7Generator.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/util/UuidV7Generator.java)**:
  - Hàm `UUID generate()` chuẩn RFC 9562 kết hợp 48-bit timestamp Unix milli + 74-bit random sequence.
- [x] **P1.3 - Cấu hình Virtual Threads & Async Bus [`config/AsyncConfig.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/config/AsyncConfig.java)**:
  - Bật `@EnableAsync` và `@EnableScheduling`.
  - Khai báo `TaskExecutor` sử dụng `SimpleAsyncTaskExecutor` với `setVirtualThreads(true)`.
- [x] **P1.4 - Thiết lập DDL PostgreSQL Schema [`src/main/resources/schema.sql`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/resources/schema.sql) & [`application.yaml`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/resources/application.yaml)**:
  - Bổ sung cột `deleted_at TIMESTAMP` trên các bảng: `topics`, `notes`, `user_words`, `word_contexts`, `conversations`.
  - Thiết lập bảng `user_words` với `WITH (fillfactor = 80)` cho HOT Update.
  - Đánh Composite Indexes có điều kiện (`WHERE deleted_at IS NULL`) cho `user_words`, `word_contexts`, `topics`, `notes`, `review_logs`, `daily_stats`.
  - Cấu hình allow-list SSRF cho AI trong `application.yaml`.

---

### 🔹 PHASE 2: Domain Models, Unicode Normalization & Multi-Context Vocabulary Engine

**Mục tiêu**: Refactor thực thể `Word` cũ thành `UserWord` (với `primary_meaning`, `ease_factor`, `deleted_at`) và `WordContext` (với `meaning`, `sentence`, `sentenceTranslation`, `partOfSpeech`), cài đặt thuật toán chuẩn hóa Unicode dùng chung.

#### Checklist Phase 2:
- [x] **P2.1 - Tạo Utility Chuẩn hóa Unicode [`util/TextNormalizationUtil.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/util/TextNormalizationUtil.java)**:
  - Cài đặt chuẩn 4 bước: `trim() -> toLowerCase(Locale.ENGLISH) -> strip non-letter boundary characters (regex: ^[^\p{L}]+|[^\p{L}]+$) -> collapse whitespace (\s+ -> " ")`.
- [x] **P2.2 - Tạo Domain Entities [`model/UserWord.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/UserWord.java) & [`model/WordContext.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/WordContext.java)**:
  - `UserWord`: `id`, `user`, `text`, `textNormalized`, `primaryMeaning`, `ipa`, `status`, `correctStreak`, `easeFactor` (double, default 2.5), `intervalDays`, `nextReviewAt`, `failedEssayStreak`, `version`, `deletedAt`, `createdAt`, `updatedAt`, `@OneToMany contexts`.
  - `WordContext`: `id`, `user`, `userWord`, `note` (nullable), `noteTitleSnapshot`, `partOfSpeech`, `meaning`, `sentence`, `sentenceTranslation`, `deletedAt`, `createdAt`, `updatedAt`.
  - Bổ trợ: Đã tạo `ReviewLog.java`, `DailyStats.java`, `Conversation.java`, `ChatMessage.java`.
- [x] **P2.3 - Nâng cấp [`model/UserAppConfig.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/UserAppConfig.java) & [`model/UserSyncMetadata.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/UserSyncMetadata.java)**:
  - `UserAppConfig`: Đã thêm `timezone` (mặc định `Asia/Ho_Chi_Minh`), `dailyTarget` (20), `sidebarWidth` (244), `autoSpeak`, `version`.
  - `UserSyncMetadata`: Đã thêm các bộ đếm `newCount`, `learningCount`, `rememberedCount`, `masteredCount`, `currentStreakDays`, `longestStreakDays`, `lastActiveDate`, `lastSyncedAt`, `clientVersion`.
  - `Topic.java` & `Note.java`: Đã bổ sung `deletedAt` cho soft-delete tombstone.
- [x] **P2.4 - Tạo Repositories [`repository/UserWordRepository.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/repository/UserWordRepository.java) & [`repository/WordContextRepository.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/repository/WordContextRepository.java)**:
  - Truy vấn nạp sẵn `contexts` (`@EntityGraph`) tránh $N+1$ query.
  - Truy vấn tìm kiếm lọc `deletedAt IS NULL`.
- [x] **P2.5 - Xây dựng [`service/WordService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/WordService.java) & [`controller/WordController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/WordController.java)**:
  - Cài đặt `GET /api/words`, `POST /api/words/context`, `PUT /api/words/{id}`, `DELETE /api/words/{id}`, `DELETE /api/words/{wordId}/contexts/{ctxId}`.

---

### 🔹 PHASE 3: Spaced Repetition (SRS) Engine & Event-Sourced Review Pipeline

**Mục tiêu**: Cài đặt thuật toán tính toán SRS 4 cấp độ, xử lý idempotent sự kiện nộp bài ôn tập theo `eventId`, trả về ngay trạng thái từ vựng chuẩn xác, và hỗ trợ endpoint chẩn đoán từ đồng nghĩa AI.

#### Checklist Phase 3:
- [x] **P3.1 - Xây dựng Engine SRS [`service/SrsCalculator.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/SrsCalculator.java)**:
  - Công thức: `1 -> 3 -> 7 -> 14 -> round(interval * easeFactor)`.
  - Quên (`quality < 3`): `correctStreak = 0`, `intervalDays = 1`, `easeFactor = max(1.3, easeFactor - 0.2)`, `status = LEARNING`.
  - Nhớ (`quality >= 3`): Tăng streak, cập nhật khoảng cách ngày theo ngày hiện tại của user timezone; cập nhật trạng thái `LEARNING`, `TEMP_REMEMBERED` (streak 2..4), hoặc `MASTERED` (streak $\ge 5$ và interval $\ge 14$).
- [x] **P3.2 - Tạo Entity [`model/ReviewLog.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/ReviewLog.java) & Repository [`repository/ReviewLogRepository.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/repository/ReviewLogRepository.java)**:
  - Cột `eventId UUID NOT NULL UNIQUE` (idempotency key từ client).
- [x] **P3.3 - Xây dựng Service & Controller Review [`service/ReviewService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/ReviewService.java) & [`controller/ReviewController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/ReviewController.java)**:
  - `GET /api/review/due?limit=15&excludeIds=...`: Truy vấn từ đến hạn theo múi giờ user, loại trừ các ID đang trong phiên.
  - `POST /api/review/submit`: Nhận `{ events: [...] }`, bỏ qua `eventId` đã có, tính nhẩm SM-2 in-memory, phát Spring Event bất đồng bộ để ghi DB, trả `200 OK` kèm `updatedWords`.
  - `POST /api/review/check-answer`: Gọi AI chẩn đoán đáp án khi người dùng nhập từ đồng nghĩa hợp lý.

---

### 🔹 PHASE 4: Delta Sync Engine, Tombstones & Conflict Resolution

**Mục tiêu**: Thay thế toàn bộ batch sync cũ bằng cơ chế Delta Sync vi sai `POST /api/sync/delta`, xử lý xóa bằng tombstones (`deleted_at`), tự động gộp từ vựng (`idRemap`), và phát hiện xung đột phiên bản Note.

#### Checklist Phase 4:
- [x] **P4.1 - Xây dựng DTOs Delta Sync [`dto/DeltaSyncRequest.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/dto/DeltaSyncRequest.java) & [`dto/DeltaSyncResponse.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/dto/DeltaSyncResponse.java)**:
  - Định nghĩa chuẩn các cấu trúc: `cursor`, `changes`, `deleted`, `tombstones`, `idRemap`, `conflicts`, `counters`.
- [x] **P4.2 - Cài đặt Engine Hợp nhất Delta [`service/DeltaSyncService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/DeltaSyncService.java)**:
  - Xử lý Push: Lưu thay đổi topics, notes, words, contexts và replay các reviewEvents.
  - Tự động gộp từ vựng trùng `text_normalized` khác UUID: Chuyển contexts sang từ canonical và ghi vào `idRemap`.
  - Kiểm tra xung đột Note: Nếu `dto.baseVersion() < note.getVersion()` và nội dung khác biệt $\rightarrow$ thêm vào `conflicts`.
  - Xử lý Pull: Trả các bản ghi có `updated_at > cursor` và các ID có `deleted_at > cursor` (tombstones). Cấp `newCursor = Instant.now().toString()`.
- [x] **P4.3 - Xây dựng Endpoint Giải quyết Xung đột `POST /api/sync/resolve-conflict`**:
  - Tiếp nhận quyết định `KEEP_LOCAL` hoặc `APPLY_CLOUD` từ Client.
- [x] **P4.4 - Cập nhật [`controller/SyncController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/SyncController.java)**:
  - Cung cấp `POST /api/sync/delta`, `POST /api/sync/resolve-conflict`, và `GET /api/sync/counters`.

---

### 🔹 PHASE 5: Progress, Streak & 35-Day Timezone-Aware Dashboard

**Mục tiêu**: Cung cấp dữ liệu tức thì (`< 2ms`) cho màn hình Bento Grid của **margin.** theo đúng múi giờ người dùng, và job kiểm tra đối soát streak nửa đêm.

#### Checklist Phase 5:
- [x] **P5.1 - Tạo Entity [`model/DailyStats.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/model/DailyStats.java) & Async Listener [`event/AsyncStatsListener.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/event/AsyncStatsListener.java)**:
  - Lắng nghe sự kiện thêm từ mới và sự kiện ôn tập thẻ để cập nhật cộng dồn in-place vào dòng ngày hôm nay của `daily_stats`.
- [x] **P5.2 - Xây dựng [`service/ProgressService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/ProgressService.java) & [`controller/ProgressController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/ProgressController.java)**:
  - Cài đặt `GET /api/progress/dashboard`: Trả về đúng 35 ngày (`activityCalendar`), `accuracyPercent` 30 ngày gần nhất, `streakDays`, `wordsAddedToday`, `distribution`.
- [x] **P5.3 - Xây dựng Scheduler Đối soát Nửa đêm [`scheduler/StreakMidnightScheduler.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/scheduler/StreakMidnightScheduler.java)**:
  - Quét định kỳ kiểm tra người dùng vừa bước qua ngày mới theo `timezone`: Nếu ngày hôm qua không có hoạt động học, reset `currentStreakDays = 0`.

---

### 🔹 PHASE 6: Topics & Notes CRUD with Cascade Soft-Delete & Idempotent Upsert

**Mục tiêu**: Hoàn thiện toàn bộ các thao tác quản lý Topic và Note, hỗ trợ client cấp UUIDv7 để upsert an toàn, cascade soft-delete, và bảo toàn snapshot tiêu đề bài viết.

#### Checklist Phase 6:
- [x] **P6.1 - Nâng cấp [`service/TopicService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/TopicService.java) & [`controller/TopicController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/TopicController.java)**:
  - Hỗ trợ `POST /api/topics` nhận `id` từ client (idempotent).
  - Cài đặt `PUT /api/topics/{id}` đổi tên Topic.
  - Cài đặt `DELETE /api/topics/{id}`: Soft-delete Topic và cascade soft-delete toàn bộ các Note bên trong.
- [x] **P6.2 - Nâng cấp [`service/NoteService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/NoteService.java) & [`controller/NoteController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/NoteController.java)**:
  - `GET /api/notes`: Trả danh sách Note kèm `harvestedWordCount`.
  - `GET /api/notes/{id}`: Trả chi tiết Note kèm `savedWords`.
  - `POST /api/notes`: Hỗ trợ nhận `id` từ client (idempotent).
  - `PUT /api/notes/{id}`: Kiểm tra `baseVersion`, cập nhật nội dung và đồng bộ `note_title_snapshot` nếu đổi title.
  - `DELETE /api/notes/{id}`: Soft-delete Note, detach `word_contexts` (`note_id = null`), giữ nguyên `note_title_snapshot`.

---

### 🔹 PHASE 7: Unified AI Gateway, Context Translation & AI Chat Companion (SSE Streaming)

**Mục tiêu**: Kết nối trực tiếp `9Router` (`http://localhost:20128/v1`), bảo vệ chống SSRF qua allow-list, cung cấp API dịch theo câu ngữ cảnh, và xây dựng hệ thống AI Chat Companion streaming thời gian thực qua Server-Sent Events (`text/event-stream`).

#### Checklist Phase 7:
- [x] **P7.1 - Nâng cấp [`service/AiSettingsService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/AiSettingsService.java) & [`controller/AiSettingsController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/AiSettingsController.java)**:
  - `GET /api/ai-settings`: Không bao giờ trả key plaintext (trả `hasApiKey: true/false`, `apiKeyHint`).
  - `PUT /api/ai-settings`: Kiểm tra `customBaseUrl` thuộc allow-list chống SSRF; mã hóa AES-256 cho key mới.
  - `POST /api/ai-settings/test`: Ping tới 9Router/Provider và trả danh sách models thực tế.
- [x] **P7.2 - Cài đặt API Dịch Ngữ cảnh `POST /api/ai/translate-context` trong [`controller/AIController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/AIController.java)**:
  - Nhận `{ text, sentence, noteId, noteTitle }`, gọi AI dịch sát nghĩa từ trong ngữ cảnh câu văn, trả về: `{ text, ipa, partOfSpeech, meaning, sentence, sentenceTranslation }`.
- [x] **P7.3 - Xây dựng AI Chat Companion Entities & Services [`service/ChatService.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/service/ChatService.java) & [`controller/ChatController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/ChatController.java)**:
  - Tạo thực thể `Conversation` (`title`, `noteId`, `deletedAt`) và `ChatMessage` (`role`, `content`).
  - Quản lý hội thoại: `GET/POST/PUT/DELETE /api/chat/conversations` và `GET .../messages`.
  - Cài đặt `POST /api/chat/send` (`produces = MediaType.TEXT_EVENT_STREAM_VALUE`):
    - Đọc lịch sử từ DB, nạp context bài Note/Từ vựng vào System Prompt.
    - Dùng `SseEmitter` stream token qua các events: `meta` -> `chunk` -> `done` (hoặc `error`). Lưu câu trả lời hoàn chỉnh vào `ChatMessage` khi stream kết thúc.

---

### 🔹 PHASE 8: Tối Ưu Hóa Bộ Nhớ Đệm (Caffeine Due Cache - Tùy Chọn)

**Mục tiêu**: Xây dựng tầng cache in-memory 45 từ/user trên Caffeine Cache nhằm tối ưu hóa thêm tốc độ phục vụ cho các thiết bị mới hoặc các phiên ôn tập lớn.

#### Checklist Phase 8:
- [x] **P8.1 - Xây dựng Cấu trúc Cache [`cache/SlidingWordWindow.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/cache/SlidingWordWindow.java)**.
- [x] **P8.2 - Tích hợp Caffeine Pre-warming vào [`controller/AuthController.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/controller/AuthController.java)**.

---

### 🔹 PHASE 9: Kiểm Thử Tích Hợp (Integration Tests), Xuất OpenAPI & Nghiệm Thu

**Mục tiêu**: Kiểm thử tự động 100% các luồng nghiệp vụ cốt lõi, xuất file tài liệu `openapi.json` làm nguồn chân lý cho Frontend, và chạy `./mvnw test` xanh toàn bộ.

#### Checklist Phase 9:
- [x] **P9.1 - Cập nhật Test Models & [`DatabaseSeeder.java`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/src/main/java/com/learning/english/config/DatabaseSeeder.java)**:
  - Seed bài đọc mẫu *"The art of paying attention"* và 20 từ vựng đa ngữ cảnh.
- [x] **P9.2 - Viết Test Kiểm chứng Nghiệp vụ**:
  - Test deduplication từ vựng Unicode và tự động merge ngữ cảnh.
  - Test cascade soft-delete Topic/Note và bảo toàn `note_title_snapshot`.
  - Test tính toán SRS 4 cấp độ và xử lý idempotent sự kiện theo `eventId`.
  - Test Delta Sync hai chiều, tombstones và phát hiện xung đột Note.
  - Test stream SSE trên `/api/chat/send`.
- [x] **P9.3 - Xuất file đặc tả [`docs/openapi.json`](file:///Users/hieuquockieu/Desktop/workspace/learning%20english/learning_english_be/docs/openapi.json)**:
  - Cấu hình SpringDoc tự sinh tài liệu API chuẩn xác.
- [x] **P9.4 - Chạy `./mvnw clean test` xác nhận Build & Test pass 100%**.
