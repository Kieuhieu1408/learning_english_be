# BẢNG CÂU HỎI CHỐT THIẾT KẾ KIẾN TRÚC BACKEND MỚI (`answer.md`)
## Dự án: Smart English Notes (Thiết kế mới: **margin.**)

> **Hướng dẫn**: Dưới đây là các câu hỏi kiến trúc và nghiệp vụ cốt lõi được tổng hợp từ việc đối chiếu giữa **Bản thiết kế UX/UI mới (margin.)**, **Tài liệu BRD v1.1.0**, và **Hệ thống Backend hiện tại**.  
> Ở mỗi câu hỏi đều có sẵn **Phương án khuyến nghị (Recommended)**. Bạn chỉ cần điền lựa chọn của mình (ví dụ: `A`, `B`, hoặc ghi chú thêm) vào phần **`Trả lời:`** bên dưới mỗi câu.

---

## NHÓM 1: MÔ HÌNH GIAO TIẾP GIỮA FRONTEND & BACKEND

### Câu 1.1: Chiến lược gọi API giữa Frontend (margin.) và Backend
* **Bối cảnh**: Backend cũ thiết kế theo hướng gom mọi thứ vào 1 API đồng bộ (`POST /api/sync/batch`). Trong khi đó, bản thiết kế mới (**margin.**) cần các API chuyên biệt theo từng màn hình (`GET /api/review/due`, `POST /api/review/submit`, `POST /api/words/context`, `GET /api/progress/dashboard`, `PUT /api/topics/{id}`).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Hybrid REST + Background Sync)**:
    * Khi **Online**: Frontend gọi trực tiếp các REST API chuyên biệt (đọc cực nhanh theo trang/đợt, cập nhật từng phần không phải tải lại toàn bộ DB).
    * Khi **Offline**: Ghi vào `IndexedDB` (`syncQueue`), khi có mạng lại thì đẩy qua `/api/sync/delta`.
  * **Phương án B (Pure Online REST API)**:
    * Bỏ cơ chế `syncQueue` phức tạp ở Client; mọi thao tác đều gọi trực tiếp REST API chuẩn trên Backend (kết hợp Caching / Optimistic UI trên Frontend).
  * **Phương án C (Pure Offline Batch Sync)**:
    * Mọi thao tác đọc/ghi chỉ làm việc với `IndexedDB`, định kỳ gọi `/api/sync/batch` để đồng bộ.

**Trả lời:** `A`

---

## NHÓM 2: KIẾN TRÚC LƯU TRỮ TỪ VỰNG & ĐA NGỮ CẢNH (`Word` & `WordContext`)

### Câu 2.1: Cách lưu trữ mối quan hệ 1 Từ vựng – Nhiều Ngữ cảnh (`contexts: [...]`) để tối ưu Tốc độ Đọc (Read-First)
* **Bối cảnh**: Trong thiết kế **margin.**, mỗi từ vựng (`UserWord`) có một mảng các câu ví dụ (`contexts`: gồm `noteId`, `noteTitle`, `sentence`, `translation`) thu thập từ nhiều bài đọc khác nhau. Một người dùng thông thường chỉ có khoảng **1 đến 5 câu ngữ cảnh** cho mỗi từ.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng cho Tối ưu Read cực đại - Lưu `JSONB` hoặc Bảng `word_contexts` kèm Denormalized Snapshot)**:
    * Tạo bảng `word_contexts` chuẩn hóa (để dễ quản lý khi xóa/sửa từng ngữ cảnh), **ĐỒNG THỜI** (hoặc dùng `@BatchSize` / `JSONB` projection) đảm bảo khi gọi `GET /api/review/due?limit=15` hoặc `GET /api/words`, toàn bộ `contexts` của 15 từ được lấy ra chỉ trong **1 câu truy vấn duy nhất**, không bị lỗi $N+1$ query.
    * Lưu kèm trường `note_title_snapshot` trong mỗi ngữ cảnh để ngay cả khi người dùng **xóa bài Note gốc** (`note_id = NULL`), từ vựng vẫn hiển thị được câu ví dụ đó từng lấy từ bài đọc tên là gì.
  * **Phương án B (Lưu thuần cột `JSONB contexts` ngay trong bảng `user_words`)**:
    * Không tạo bảng `word_contexts` riêng mà lưu thẳng mảng JSON `contexts` vào 1 cột `JSONB` của bảng `user_words`. Đọc 1 dòng `user_words` là có sẵn toàn bộ các câu ví dụ trong 1 lần đọc đĩa (0 JOIN).

**Trả lời:** `A trả về json FE sẽ render `

---

### Câu 2.2: Quy tắc nhận diện "Từ đã tồn tại" trong kho từ của một người dùng
* **Bối cảnh**: Khi người dùng bôi đen và lưu một từ ở bài đọc mới, hệ thống kiểm tra xem từ đó đã có trong kho của họ chưa để gộp thêm ngữ cảnh (`WordContext`) thay vì tạo từ mới.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng)**: Gộp theo **`(user_id, lower(trim(text)))`** — Không phân biệt hoa/thường và khoảng trắng thừa. Nếu cùng một từ vừa là Danh từ ở bài Note 1, vừa là Động từ ở bài Note 2, cả hai đều nằm chung trong 1 thẻ từ `Word`, còn `partOfSpeech` (loại từ) sẽ được lưu ở từng `WordContext` tương ứng.
  * **Phương án B**: Phân biệt theo **`(user_id, lower(trim(text)), part_of_speech)`** — Nghĩa là `"record"` (Noun) và `"record"` (Verb) sẽ tách thành 2 thẻ từ vựng riêng biệt.

**Trả lời:** `A`

---

### Câu 2.3: Hành vi của điểm SRS khi người dùng tra lại một từ đã có trong kho
* **Bối cảnh**: Báo cáo thiết kế **margin.** nêu rõ: *"Khi user lưu một từ đã có trong từ điển ở một bài đọc mới, hệ thống thêm ngữ cảnh mới vào `contexts`, không tạo từ mới và không reset streak SRS"*.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Đúng theo thiết kế margin.)**: Giữ nguyên 100% các chỉ số SRS hiện tại (`status`, `correctStreak`, `intervalDays`, `nextReviewAt`), chỉ đơn thuần nối thêm câu ví dụ mới vào `contexts`.
  * **Phương án B (Smart Reminder)**: Giữ nguyên `correctStreak`, nhưng nếu từ đó đang có ngày hẹn ôn tập quá xa (ví dụ `nextReviewAt` còn hơn 14 ngày nữa mới tới), tự động kéo `nextReviewAt` về ngày mai để nhắc người dùng ôn lại ngữ cảnh mới.

**Trả lời:** `A`

---

## NHÓM 3: LUỒNG ÔN TẬP HÀNG NGÀY (`SRS & Flashcard / Fill in the blank`)

### Câu 3.1: Thứ tự ưu tiên lấy 15 thẻ trong `GET /api/review/due?limit=15`
* **Bối cảnh**: Khi người dùng có nhiều hơn 15 từ cần ôn trong ngày (ví dụ có 60 từ có `nextReviewAt <= today`), hệ thống cần quy tắc sắp xếp cố định để đánh **Composite B-Tree Index** tối ưu cho thao tác Read.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Tối ưu Index `(user_id, next_review_at ASC, status ASC)`)**:
    * Ưu tiên các từ có `next_review_at` quá hạn lâu nhất trước, sau đó ưu tiên các từ đang học dở (`LEARNING`) rồi đến từ mới (`NEW`) và từ tạm nhớ (`TEMP_REMEMBERED`).
  * **Phương án B**:
    * Trộn ngẫu nhiên hoặc chia theo tỷ lệ cố định (ví dụ: mỗi đợt 15 thẻ sẽ lấy tối đa 10 thẻ cũ cần ôn lại + 5 thẻ `NEW`).

**Trả lời:** `A`

---

### Câu 3.2: Nơi thực thi thuật toán SuperMemo-2 (`POST /api/review/submit`)
* **Bối cảnh**: Trong màn hình Review của **margin.**, người dùng bấm phím `1` (*Still learning*) hoặc phím `2` (*I remembered*) ở chế độ Flashcard, hoặc gõ từ ở chế độ *Fill in the blank*.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng)**:
    * Frontend có thể tính nhẩm để chuyển thẻ ngay lập tức (`0ms` delay trên UI), và gửi kết quả (`wordId`, `isCorrect` / `quality`, `mode`: `FLASHCARD` | `FILL_BLANK`) về Backend.
    * **Backend là nguồn chân lý (Source of Truth)** thực hiện cập nhật công thức SM-2 chuẩn vào bảng `user_words`, ghi lịch sử vào `review_logs` và cập nhật thống kê ngày.
  * **Phương án B**:
    * Gửi request `POST /api/review/submit` theo từng lô (sau khi xong đợt 15 thẻ mới gửi 1 lần) để việc lật giữa 15 thẻ hoàn toàn không gọi mạng.

**Trả lời:** `A khi test theo plashcard còn B khi test writing` *(Lưu ý: Có thể chọn kết hợp cả A và B — tức là gom kết quả hoặc gửi ngầm bất đồng bộ).*

---

### Câu 3.3: Cách thức chuyển trạng thái 4 cấp độ (`NEW` $\rightarrow$ `LEARNING` $\rightarrow$ `TEMP_REMEMBERED` $\rightarrow$ `MASTERED`)
* **Bối cảnh**: Màn hình **Progress & Analytics** của thiết kế mới có biểu đồ phân bổ đủ **4 trạng thái**: `New`, `Learning`, `Remembered` (`TEMP_REMEMBERED`), `Mastered`. Trong khi code Frontend cũ chỉ gán `correctStreak >= 4 ? MASTERED : LEARNING` (bỏ qua trạng thái `TEMP_REMEMBERED`).
* **Quy tắc đề xuất chuẩn hóa trên Backend**:
  * `NEW`: Từ mới thêm, chưa ôn tập lần nào (`correctStreak = 0`, chưa có `ReviewLog`).
  * `LEARNING`: Đang học hoặc vừa trả lời sai (`correctStreak` từ `0` đến `1`).
  * `TEMP_REMEMBERED` (*Remembered*): Đã nhớ bước đầu (`correctStreak` từ `2` đến `4`).
  * `MASTERED`: Đã thuộc vững (`correctStreak >= 5` và `intervalDays >= 14`).
* **Câu hỏi**: Bạn có đồng ý với ngưỡng chuyển đổi 4 trạng thái này không, hay muốn đặt ngưỡng `correctStreak` khác?

**Trả lời:** `có đồng ý`

---

## NHÓM 4: THỐNG KÊ TIẾN ĐỘ & BIỂU ĐỒ 35 NGÀY (`Progress & Analytics`)

### Câu 4.1: Định nghĩa mục tiêu hàng ngày & điều kiện tính chuỗi ngày học (`Streak`)
* **Bối cảnh**: Thiết kế mới hiển thị: *"Reviewed today: X / 20"*, *"4 days in a row"*, và **Streak calendar grid 35 ngày**.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Khuyến khích thói quen giống Duolingo/GitHub)**:
    * Chỉ cần người dùng có hoạt động học tập trong ngày (**ôn tập ít nhất 1 thẻ** HOẶC **thu thập ít nhất 1 từ mới**) là ngày đó được tô sáng trên lịch 35 ngày và được tính nối tiếp vào chuỗi `currentStreak`.
    * Con số mục tiêu mặc định là `20 thẻ/ngày` (lưu trong `UserAppConfig` để sau này user có thể chỉnh nếu muốn).
  * **Phương án B (Khắt khe)**:
    * Phải ôn tập đủ `20/20 thẻ` trong ngày thì mới được tính là hoàn thành chuỗi Streak của ngày hôm đó.

**Trả lời:** `A`

---

### Câu 4.2: Cơ chế cập nhật số liệu Dashboard (`Read-First` vs `Async Rollup`)
* **Bối cảnh**: Bạn đã định hướng rất chuẩn từ đầu là thao tác **Read phải cực nhanh**, còn thao tác tổng hợp báo cáo có thể trễ nhẹ.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Bảng tổng hợp `user_daily_stats` + Async Event)**:
    * Tạo bảng `user_daily_stats (user_id, study_date, words_added, cards_reviewed, correct_count, incorrect_count)` có Unique Index trên `(user_id, study_date)`.
    * Khi user nộp kết quả ôn tập (`/api/review/submit`), hệ thống phát một Spring Application Event bất đồng bộ (`@Async` trên Virtual Thread) để ghi `review_logs` và cộng dồn vào đúng 1 dòng của ngày hôm nay trong `user_daily_stats`.
    * Khi mở màn hình **Progress**, API `GET /api/progress/dashboard` chỉ cần đọc đúng **35 dòng gần nhất** của `user_daily_stats` (`< 2ms`), hoàn toàn không cần quét hay `GROUP BY` trên bảng `review_logs`!
  * **Phương án B**:
    * Tính toán trực tiếp bằng câu lệnh `GROUP BY DATE(reviewed_at)` trên bảng `review_logs` mỗi khi mở trang Progress.

**Trả lời:** `A`

---

## NHÓM 5: MODULE MỚI — AI CHAT COMPANION (TRỢ LÝ AI ĐA NGỮ CẢNH)

### Câu 5.1: Luồng xử lý AI Chat & Lưu trữ lịch sử hội thoại
* **Bối cảnh**: Bản thiết kế **margin.** bổ sung màn hình **AI Chat Companion** (có Sidebar danh sách hội thoại cũ: New/Rename/Delete chat; hỗ trợ giải thích từ vựng, tóm tắt bài Note đang đọc, tạo bài tập từ vựng).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Backend-driven Chat)**:
    * Toàn bộ hội thoại (`conversations`, `chat_messages`) được lưu tại PostgreSQL ở Backend.
    * Khi user gửi tin nhắn qua `POST /api/chat/send`, Backend tự động lấy API Key đã giải mã của user trong `UserAiSettings`, đính kèm nội dung bài `Note` hoặc danh sách `Word` liên quan vào System Prompt, gọi Gemini/DeepSeek thông qua Virtual Threads và lưu lịch sử vào DB.
  * **Phương án B (Client-side Chat)**:
    * Frontend tự gọi trực tiếp Gemini/DeepSeek ở trình duyệt rồi chỉ gửi lịch sử tin nhắn về Backend để lưu trữ.

**Trả lời:** `A. Chúng ta thêm khác kết nối mới thông qua 9router và các api của 9router bạn hãy search nó là gì? chúng ta nên xem xét dùng litellm để quản lý các provider (litellm có hỗ trợ api của 9router không?)` 

---

### Câu 5.2: Phản hồi tin nhắn Chat (Streaming vs. Chuẩn REST JSON)
* **Các lựa chọn**:
  * **Phương án A (Đơn giản, đồng bộ nhanh với thiết kế hiện tại)**: `POST /api/chat/send` trả về JSON hoàn chỉnh sau khi AI sinh xong câu trả lời (tận dụng luôn cấu trúc `AIClient` hiện có của Backend).
  * **Phương án B (Trải nghiệm cao cấp)**: Hỗ trợ thêm **Server-Sent Events (`SSE` - `text/event-stream`)** để chữ hiện dần ra theo thời gian thực.

**Trả lời:** `B`

---

## NHÓM 6: ĐỒNG BỘ & GIẢI QUYẾT XUNG ĐỘT (`Conflict Resolution`)

### Câu 6.1: Phạm vi áp dụng hộp thoại giải quyết xung đột 2 cột (`This device` vs `Cloud version`)
* **Bối cảnh**: Hiện tại Backend đang âm thầm bỏ qua bản ghi nếu `dto.version() < entity.getVersion()`. Để Frontend hiển thị được Modal so sánh 2 cột như thiết kế, Backend cần trả về danh sách `conflicts` trong `SyncResponse`.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Thông minh & Ít làm phiền người dùng)**:
    * **Với `Note`**: Nếu lệch `version` và nội dung (`content`/`title`) khác nhau $\rightarrow$ Trả về trong mảng `conflicts` để bật Modal 2 cột cho người dùng chọn.
    * **Với `Word` & `WordContext`**: Tự động **Auto-Merge** các ngữ cảnh câu ví dụ mới từ cả 2 thiết bị và lấy trạng thái SRS mới nhất mà không bắt người dùng phải giải quyết xung đột thủ công cho từng từ vựng lẻ.
  * **Phương án B**:
    * Bật Modal giải quyết xung đột cho cả `Note` lẫn từng `Word`.

**Trả lời:** `A`

---

## NHÓM 7: CÁC THIẾT KẾ TỐI ƯU HIỆU NĂNG (READ-FIRST, ASYNC-WRITE & DATABASE INTERNALS)

> Đây là nhóm câu hỏi chốt lại các kỹ thuật tối ưu chuyên sâu mà bạn đã đặt vấn đề từ đầu: **Chống Full Table Scan ở các page cuối**, **Tối ưu tốc độ Read tức thì**, **Chấp nhận độ trễ ở luồng Write (Async/Job)**, và **Tích hợp 9Router / LiteLLM**.

### Câu 7.1: Giải quyết bài toán Bảng `words` phình to & Dữ liệu tài khoản mới nằm ở các Page cuối
* **Bối cảnh**: Như bạn đã phân tích, khi có nhiều người dùng, từ vựng của một user bị rải rác trên nhiều Disk Pages và dữ liệu của user mới nằm ở các page cuối.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Hash Partitioning + Covering Index + UUIDv7)**:
    1. **Hash Partitioning theo `user_id`**: Chia bảng `user_words` và `word_contexts` thành $N$ phân vùng vật lý (ví dụ 16 hoặc 32 partitions: `PARTITION BY HASH (user_id)`). Toàn bộ từ của 1 user luôn rơi vào đúng 1 partition nhỏ.
    2. **Composite Index bám sát câu lệnh Read**:
       * `idx_user_words_review ON user_words (user_id, next_review_at ASC, status ASC)` (Phục vụ lấy 15 từ ôn tập).
       * `idx_user_words_lookup ON user_words (user_id, text_normalized)` (Phục vụ kiểm tra từ trùng khi bôi đen).
    3. **Sử dụng UUIDv7 (Time-ordered UUID)** thay cho UUIDv4 ngẫu nhiên để các bản ghi tạo cùng thời điểm nằm liền kề nhau trên B-Tree leaf page, chống phân mảnh Index.
  * **Phương án B (Chỉ dùng Composite Index thông thường, chưa bật Partitioning)**:
    * Giữ 1 bảng `user_words` đơn lẻ và chỉ đánh B-Tree Index trên `user_id`.

**Trả lời:** `A`

---

### Câu 7.2: Chống phình bảng (MVCC Bloat) khi cập nhật kết quả ôn tập hàng ngày (Hot/Cold Split)
* **Bối cảnh**: Trong PostgreSQL, mỗi lần `UPDATE` điểm SRS (`correctStreak`, `intervalDays`, `nextReviewAt`) là DB phải ghi một tuple mới. Khi đã tách bảng `word_contexts` (chứa các câu văn `TEXT` dài và nặng) ra khỏi bảng `user_words`:
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Giữ bảng `user_words` siêu gọn để kích hoạt PostgreSQL HOT Update)**:
    * Bảng `user_words` **chỉ chứa từ gốc (`text`), `ipa` và các cột số/ngày tháng của SRS** (không chứa các đoạn văn dài `example_context`, `translation` — vì chúng đã nằm bên bảng `word_contexts` chỉ ghi 1 lần, không bị update khi ôn tập!).
    * Đặt `WITH (fillfactor = 80)` cho bảng `user_words` để chừa 20% khoảng trống trên mỗi Disk Page 8KB $\rightarrow$ Khi user ôn tập và `UPDATE` điểm SRS, PostgreSQL sẽ cập nhật **ngay trên cùng 1 Page vật lý (Heap-Only Tuple - HOT Update)** mà không làm phình bảng hay phải nhảy sang page cuối!
  * **Phương án B (Tách thêm thành 3 bảng: `user_words` + `user_word_srs` + `word_contexts`)**:
    * Tách riêng các cột SRS sang một bảng 1-1 `user_word_srs` độc lập hoàn toàn với `user_words`.

**Trả lời:** `A`

---

### Câu 7.3: Tối ưu Tốc độ Read Cực Đại (Pre-warmed Read Cache cho 15 từ ôn tập đầu tiên)
* **Bối cảnh**: Bạn nhấn mạnh việc **đọc từ vựng và ôn tập hàng ngày là thao tác cần nhanh nhất**.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - In-Memory Cache + Async Pre-warming khi Login/Mở App)**:
    * Sử dụng **Caffeine Cache** (hoặc Redis) ngay trên Backend cho `DueWordsQueue` và `DashboardCounters` của từng `userId`.
    * Ngay khi user vừa đăng nhập hoặc gọi API khởi động (`/api/auth/me` hoặc `/api/sync/counters`), một **Virtual Thread chạy ngầm** sẽ truy vấn sẵn 15–30 từ cần ôn tập hôm nay (kèm `contexts` dạng JSON) nạp sẵn vào RAM Cache.
    * Khi user bấm sang tab **Review Session** (`GET /api/review/due?limit=15`), Backend trả kết quả trực tiếp từ RAM trong **`< 1ms`** mà không tốn dù chỉ 1 lần đọc đĩa!
  * **Phương án B (Đọc trực tiếp từ PostgreSQL qua Index mỗi lần gọi)**:
    * Không dùng Cache cho danh sách ôn tập, mỗi lần bấm Review sẽ query thẳng vào PostgreSQL (`~3ms – 10ms`).

**Trả lời:** `A. Sau lần đầu người dùng truy cập sẽ cache thêm 15 từ tiếp theo phòng trường hợp người dùng chuyển bài học chúng ta cần giữ 45 từ cho 3 cache gồm 15 từ đầu, hiện tại và 15 từ tiếp theo. Kiểm soát cache có tối đa 45 từ`

---

### Câu 7.4: Kiến trúc Luồng Ghi Bất Đồng Bộ (Async Write & Background Job cho Báo cáo)
* **Bối cảnh**: Bạn đã xác định rõ: *"Khi tạo 1 từ mới hay update kết quả ôn tập và chạy job đồng bộ báo cáo, thao tác này có thể có độ trễ"*.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Write-Behind với Spring Event + Virtual Threads + Scheduled Rollup)**:
    1. **Khi nộp kết quả ôn tập (`POST /api/review/submit`) hoặc thêm từ mới**:
       * API kiểm tra hợp lệ và đẩy sự kiện vào **Async Event Queue (xử lý bởi JDK 25 Virtual Threads)** rồi trả về `202 Accepted / 200 OK` ngay lập tức cho Frontend (`< 3ms`).
    2. **Worker bất đồng bộ ở tầng dưới**:
       * Thực hiện Batch Insert vào `review_logs`, cập nhật điểm SM-2 vào `user_words`, và cập nhật bảng thống kê ngày `user_daily_stats` + `streak`.
    3. **Nightly / Periodic Reconciliation Job (`@Scheduled`)**:
       * Chạy định kỳ để đối soát lại `total_word_count`, reset/kiểm tra chuỗi `streak` qua ngày mới và dọn dẹp `idempotency_keys` hết hạn.
  * **Phương án B (Ghi đồng bộ 100% trong cùng 1 HTTP Request Transaction)**:
    * Bắt Client chờ DB ghi xong cả `user_words`, `review_logs`, và tính xong `user_daily_stats` rồi mới trả kết quả HTTP.

**Trả lời:** `A`

---

### Câu 7.5: Kiến trúc Kết nối AI qua `9Router` vs `LiteLLM`
* **Kết quả Research về `9Router` & `LiteLLM`**:
  * **9Router (`decolua/9router`)** là một Smart AI Gateway mã nguồn mở chạy local/self-hosted (mặc định tại `http://localhost:20128/v1`), xuất ra giao thức **chuẩn OpenAI-compatible API (`/v1/chat/completions`, `/v1/models`)**. Điểm mạnh nhất của 9Router là **3-Tier Auto Fallback** (tự động chuyển từ gói Subscription/CLI $\rightarrow$ API giá rẻ như DeepSeek/GLM $\rightarrow$ Free Tier khi gặp lỗi `429 Rate Limit`) và nén tiết kiệm token.
  * **LiteLLM có hỗ trợ 9Router không?** **CÓ 100%!** Vì 9Router nói ngôn ngữ chuẩn OpenAI API, LiteLLM có thể trỏ tới 9Router thông qua cấu hình `openai/` + `api_base: http://localhost:20128/v1`.
  * **Tuy nhiên (Góc nhìn Kiến trúc)**: Vì **bản thân 9Router đã là một AI Router/Proxy chuẩn OpenAI**, và Spring Boot của chúng ta đã có sẵn `OpenAIAdapter`, nếu đặt thêm LiteLLM đứng trước 9Router sẽ tạo ra **2 tầng Proxy trùng lặp tính năng** (`Spring Boot -> LiteLLM -> 9Router -> LLM Provider`).
* **Các lựa chọn thiết kế cho Backend**:
  * **Phương án A (Khuyên dùng - Unified OpenAI-Compatible Adapter hỗ trợ trực tiếp cả `9Router`, `LiteLLM`, `DeepSeek`, `Gemini`)**:
    * Nâng cấp `UserAiSettings` thêm trường `customBaseUrl` và bổ sung Provider `9ROUTER` (mặc định trỏ tới `http://localhost:20128/v1` hoặc URL cấu hình trong `application.yaml`) bên cạnh `DEEPSEEK` và `GOOGLE`.
    * Khi dùng `9ROUTER` (hoặc `LITELLM`), Backend gọi thẳng chuẩn `/v1/chat/completions` (hỗ trợ cả **SSE Streaming** cho Chat) tới `9Router` mà không cần cài chồng cả LiteLLM lẫn 9Router cùng lúc.
  * **Phương án B (Triển khai cả `LiteLLM` làm Gateway trung tâm đứng trước `9Router` trong `docker-compose.yml`)**:
    * Dựng thêm container LiteLLM quản lý quota/key của từng user trong app, rồi LiteLLM gọi tiếp sang 9Router.

**Trả lời:** `A`


---
---

# ĐỢT 2 — ĐỐI CHIẾU FE ↔ BE: AI SAI, AI ĐÚNG? (CHỐT LẠI THIẾT KẾ BACKEND)

> **Bối cảnh**: Sau Đợt 1, BE đã chốt thiết kế (BRD v2.0 + `plan.md`). Khi đối chiếu với mã nguồn FE thực tế (`learning_english_fe`) và code BE hiện tại (`learning_english_be/src`), phát hiện nhiều điểm lệch hợp đồng. **Không phải chỗ nào lệch cũng là lỗi của FE** — nhiều chỗ là BE thiết kế thiếu/mâu thuẫn nội bộ, và có chỗ cả hai cùng sai.
>
> **Cách trả lời**: Mỗi câu có mục **"Ai sai?"** (phân tích), các phương án, và **Khuyến nghị**. Điền lựa chọn vào **`Trả lời:`**. Các câu được sắp theo mức ảnh hưởng: Nhóm 8 là nguyên tắc nền, trả lời trước vì nó quyết định nhiều câu sau.
>
> **Lưu ý**: Tôi đề xuất **bỏ hướng "`@JsonAlias` hỗ trợ cả hai tên"** đã nêu ở báo cáo trước. Alias chỉ che giấu vấn đề, để lại nợ kỹ thuật và hai nguồn chân lý. Thay vào đó mỗi field chỉ có **một tên chuẩn duy nhất**, bên nào lệch thì sửa.

---

## BẢNG TỔNG HỢP SƠ BỘ: LỖI NẰM Ở ĐÂU?

### 🔴 FE sai (cần sửa dù BE chọn phương án nào)

| # | Vị trí FE | Vấn đề |
| :-: | :--- | :--- |
| F1 | `components/review/ReviewPage.tsx` → `services/review.ts` | Truyền `mode = "Flashcards"` / `"Fill in the blank"` (nhãn UI) lên BE và lưu vào `reviewLogs`; BE quy định enum `FLASHCARD` \| `FILL_BLANK`. |
| F2 | `AppShell.loadData` | `dueCount = dueCards.length` — luôn bị chặn ở 15 vì chỉ lấy `limit=15`. Badge "due" sai khi có > 15 từ. |
| F3 | `services/review.ts` (fallback) | Khi không đủ từ đến hạn, FE **đệm thêm từ chưa tới hạn** → làm sai lịch SRS và sai khái niệm "due". |
| F4 | `services/review.ts`, `progress.ts`, `words.ts` | Dùng `new Date().toISOString().split("T")[0]` → ngày **UTC**, không phải ngày của người dùng (VN = UTC+7: học trước 07:00 sáng bị tính là hôm qua). |
| F5 | `ProgressPage.tsx`, `services/progress.ts` | Hardcode `/ 20` thay vì `dailyTarget`; `activityCalendar` fallback là **dữ liệu giả** (`i % 7 < 4`, `isCurrent = i === 28`); `newAddedCount = total − 20`; streak mặc định `4`. |
| F6 | `services/ai-settings.ts` | Ghi `apiKey` **plaintext** vào `localStorage` + Dexie → vi phạm NFR bảo mật của chính BRD FE (§6.4). |
| F7 | `services/topics.ts`, `notes.ts` | `crypto.randomUUID()` rồi POST không kèm `id`; nếu server trả `id` khác thì FE xóa-rồi-ghi lại topic nhưng **không cập nhật `notes.topicId`** → note mồ côi. |
| F8 | `services/notes.ts` | `version = existing.version + 1` tăng **phía client**, trong khi BE dùng `@Version` (server tăng). Hai bên cùng "sở hữu" một con số. |
| F9 | `lib/syncManager.ts` | Bỏ qua `wordContexts` và `reviewLogs` khi sync; `db.*.clear()` trước khi `bulkPut` (khớp batch cũ nhưng **mất dữ liệu chưa sync** nếu có thay đổi trong lúc request). |
| F10 | `services/words.ts`, `notes.ts`, `topics.ts` | Vừa **ghi `syncQueue`** vừa **gọi REST trực tiếp** cho cùng một thay đổi → sau đó batch sync gửi lại lần nữa (rủi ro xử lý đôi). |
| F11 | `services/topics.ts` `deleteTopicApi` | Đặt `topicId = null` cho note thuộc topic (note mồ côi), trong khi BE plan nói xóa Topic **xóa luôn Note**. |
| F12 | `ai-settings.ts`, `chat.ts`, `WordPopover.tsx` | `testAiConnectionApi` giả lập; `sampleReply` giả vờ là AI; Popover dùng `dictionary` demo. Đây là **tàn dư prototype**, chưa nối API thật. |

### 🔴 BE sai / thiếu (cần sửa dù FE chọn gì)

| # | Vị trí BE | Vấn đề |
| :-: | :--- | :--- |
| B1 | `plan.md` P3 + câu 7.3 | Cửa sổ trượt 3-slot dùng `offset`, nhưng SRS **làm danh sách "due" co lại** sau mỗi lần chấm (và ghi DB bất đồng bộ) → `offset=15` sẽ **bỏ sót thẻ**. Đây là lỗi mà chính prototype đã cảnh báo ("không truy vấn lại queue sau từng lần chấm"). |
| B2 | `SyncService.syncBatch` | Dùng `@Version` của JPA làm version của client: `dto.version() < word.getVersion()` → **âm thầm bỏ qua** bản ghi (mất dữ liệu, không báo conflict). |
| B3 | Thiết kế delta sync | **Chưa có tombstone** (bản ghi xóa) → thiết bị B không biết thiết bị A đã xóa gì. Hiện batch trả toàn bộ dữ liệu nên chưa lộ lỗi; delta sync sẽ lộ ngay. |
| B4 | `UNIQUE (user_id, text_normalized)` | Hai thiết bị offline cùng lưu từ "linger" với 2 UUID khác nhau → lúc sync **vi phạm unique**. Chưa có quy tắc merge. |
| B5 | `TopicRequest` | Chỉ có `name`, không nhận `id` từ client → gốc của lỗi F7. |
| B6 | `POST /review/submit` "202 Accepted" | Trả về ngay mà chưa có trạng thái SRS mới → FE không biết `nextReviewAt`/`status` mới để cập nhật UI. |
| B7 | Câu 7.5 (9Router `localhost:20128`) | `localhost` là **máy chạy BE**, không phải máy người dùng. Chỉ đúng khi BE và 9Router cùng host. |
| B8 | `/api/auth/me` | FE đã gọi, BE **chưa có**. Hợp đồng `plan.md` (user + config + counters) chưa nói rõ shape. |
| B9 | Không có | **Timezone** của người dùng chưa được lưu/dùng để tính `study_date`, streak, `next_review_at`. |
| B10 | `Word.difficulty` mặc định `2.0`, `plan.md` chưa nói rõ ý nghĩa | FE dùng `2.5` (SM-2 chuẩn: ease factor). Tên `difficulty` dễ gây nhầm (giá trị lớn = *dễ*). |

### 🟡 Không bên nào sai — chỉ là chưa thống nhất

Tên field DTO (`accuracyPercent` vs `recallAccuracy`, `baseUrl` vs `customBaseUrl`, `sentence` vs `exampleContext`...), cấu trúc response `/review/due`, ngưỡng `MASTERED`, chu kỳ giãn cách.

---

## NHÓM 8: NGUYÊN TẮC NỀN (TRẢ LỜI TRƯỚC)

### Câu 8.1: FE là "Local-first" hay "Online-first"? (Quyết định số phận của 3-Slot Sliding Cache)

* **Ai sai?** Đây là **mâu thuẫn tư tưởng**, không phải lỗi code:
  * **FE hiện tại là Local-first thật sự**: mọi thao tác đọc/ghi vào IndexedDB trước (`<10ms`), REST chỉ là "best-effort" bọc `try/catch`. BRD FE §1 và §6 coi đây là **nguyên tắc cốt lõi**.
  * **Câu 1.1 (chọn A) ghi**: *"Online: FE gọi trực tiếp REST... Offline: ghi IndexedDB"* — tức **Online-first**.
  * **Câu 7.3** đầu tư Caffeine 3-slot cho `GET /review/due` `<1ms`. Nhưng nếu FE đã có sẵn toàn bộ từ trong IndexedDB và tự tính danh sách due cục bộ, **FE gần như không bao giờ gọi endpoint này** trong dùng thường ngày → cache 45 từ chỉ có ích cho thiết bị mới/vừa đăng nhập.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Local-first, BE là bản chuẩn để đồng bộ)**:
    * FE đọc từ IndexedDB (ôn tập, vocab, notes). BE là **nguồn chân lý khi hợp nhất** và nơi **hydrate thiết bị mới**.
    * `GET /api/review/due` vẫn giữ (dùng khi thiết bị mới, hoặc để đối chiếu), nhưng **cache 3-slot hạ xuống "tối ưu hóa tùy chọn" ở cuối kế hoạch** (làm khi đã đo thấy chậm). Truy vấn index `(user_id, next_review_at, status)` đã đủ nhanh (~ms).
    * Ưu tiên đầu tư BE vào **Sync đúng** (Nhóm 11) và **tính toán chuẩn** (SRS, Progress).
  * **Phương án B (Online-first, FE là "view" mỏng)**: FE bỏ đọc cục bộ khi online, gọi REST mỗi lần (stale-while-revalidate). Phải sửa lại phần lớn FE và BRD FE; offline chỉ đọc cache.
  * **Phương án C (Giữ nguyên cả hai)**: FE local-first, BE vẫn làm 3-slot cache như plan. Chấp nhận chi phí xây cache nhiều mà hiệu quả thấp.

**Trả lời:** A

---

### Câu 8.2: Ai sở hữu "hợp đồng API" và quy tắc đặt tên field?

* **Ai sai?** Cả hai đều tự đặt tên rồi mới phát hiện lệch. Không bên nào sai, nhưng cần một nguồn chân lý để không lặp lại.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - BE sở hữu hợp đồng bằng OpenAPI, FE sinh/đối chiếu type)**:
    * BE bật `springdoc-openapi`, xuất `openapi.json`. FE dùng nó làm chuẩn (có thể sinh type bằng `openapi-typescript`).
    * **Mỗi field một tên duy nhất**. Quy ước: `camelCase`, thời gian là ISO-8601 UTC (`Instant`), ngày học là `yyyy-MM-dd` theo múi giờ người dùng, ID là UUID. Không dùng `@JsonAlias` kép.
    * Tên field được chốt **theo hướng ít phải sửa nhất** (xem từng câu bên dưới, đa số nghiêng về tên FE đang dùng vì UI đã gắn chặt).
  * **Phương án B**: Viết tay một file `docs/api-contract.md` dùng chung hai repo, không sinh code.
  * **Phương án C**: Cứ để mỗi bên tự đặt, sửa khi lệch (hiện trạng).

**Trả lời:** A 

---

## NHÓM 9: MÔ HÌNH DỮ LIỆU TỪ VỰNG

### Câu 9.1: "Nghĩa của từ" lưu ở đâu? (Va chạm tên `translation` giữa FE và BE)

* **Ai sai?** **Đây là chỗ nguy hiểm nhất về ngữ nghĩa**, hai bên dùng cùng tên cho hai thứ khác nhau:

  | Khái niệm | FE hiện tại | BE (`plan.md` / ERD) |
  | :--- | :--- | :--- |
  | Nghĩa của **từ** (`nán lại`) | `word.translation` (và alias `meaning`) — cấp **từ** | `word_contexts.translation` — cấp **ngữ cảnh** |
  | Dịch của **câu** | `context.translation` | `word_contexts.example_translation` |
  | Câu ví dụ | `context.sentence` | `word_contexts.example_context` |
  | Loại từ | `word.partOfSpeech` + `context.partOfSpeech` | chỉ `word_contexts.part_of_speech` |

  * Hệ quả: ERD `user_words` **không có cột nghĩa**. Trong khi FE hiển thị **một nghĩa chung** trên thẻ ôn tập, bảng Vocabulary (`w.meaning`) và Popover ("Already in your vocabulary").
  * Về ngôn ngữ học BE có lý (câu 2.2: `record` danh từ ≠ `record` động từ nên nghĩa theo ngữ cảnh). Nhưng thiết kế `margin.` (prototype) lại hiển thị **một nghĩa chính** cho mỗi từ.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Nghĩa theo ngữ cảnh + `primaryMeaning` ở cấp từ)**:
    * `word_contexts` có: `meaning` (nghĩa trong ngữ cảnh này), `partOfSpeech`, `sentence`, `sentenceTranslation`, `noteId`, `noteTitleSnapshot`.
    * `user_words` thêm **một cột `primary_meaning`** (nghĩa hiển thị trên thẻ/danh sách; mặc định = nghĩa của ngữ cảnh đầu tiên, người dùng có thể sửa). Cột này ít khi đổi nên không ảnh hưởng HOT Update đáng kể.
    * API tên field chuẩn: `meaning`, `sentence`, `sentenceTranslation`, `partOfSpeech`.
  * **Phương án B (Nghĩa chỉ ở cấp từ, như FE)**: `user_words.meaning` duy nhất; `word_contexts` chỉ chứa câu + dịch câu. Đơn giản, khớp FE, nhưng mất khả năng phân biệt nghĩa theo loại từ.
  * **Phương án C (Giữ đúng ERD hiện tại, bỏ cột nghĩa ở `user_words`)**: DTO tự suy ra `meaning` từ ngữ cảnh mới nhất. Phải sửa FE nhiều nhất, đọc `contexts[0].meaning` khắp nơi.

**Trả lời:** A 

---

### Câu 9.2: Quy tắc chống trùng khi nhiều thiết bị cùng lưu một từ (và trùng ngữ cảnh)

* **Ai sai?** **BE thiếu quy tắc.** Có `UNIQUE (user_id, text_normalized)` nhưng không nói gì khi hai thiết bị offline tạo cùng từ với hai UUID khác nhau → sync sẽ nổ lỗi unique hoặc nhân đôi từ. FE hiện chỉ chống trùng **cục bộ** (`captureWordContext`).
  * Hai bên cũng **chuẩn hóa chuỗi khác nhau**: FE `normalize` = trim + lowercase + **bỏ ký tự không phải chữ ở hai đầu** + gộp khoảng trắng. ERD BE ghi `lower(trim(text))` (chỉ trong `plan.md` P2.6 mới nhắc thêm "bỏ dấu câu bao ngoài").
  * Chống trùng ngữ cảnh: prototype dùng khóa `(wordId, noteId, câu đã chuẩn hóa)`; FE hiện chỉ so **câu** trong cùng từ; BE chưa quy định.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Merge theo `text_normalized`, trả bảng ánh xạ ID)**:
    * Một hàm `normalize` **duy nhất** dùng chung (BE là chuẩn: trim + lowercase + bỏ ký tự không phải chữ (Unicode) ở hai đầu + gộp khoảng trắng; FE copy y hệt, có test dùng chung bộ ví dụ).
    * Khi sync nhận từ có `text_normalized` đã tồn tại với **ID khác**: **gộp vào từ hiện có** — giữ trạng thái SRS của bản **có `reviewed` mới nhất / streak cao hơn** (hoặc replay từ review events, xem Câu 10.2), hợp nhất `contexts`. Response trả `idRemap: { clientId → serverId }` để FE sửa ID cục bộ và `word_contexts.wordId`.
    * Khóa chống trùng ngữ cảnh: `UNIQUE (user_word_id, note_id, sentence_normalized)` (khi `note_id` NULL dùng `note_title_snapshot`).
  * **Phương án B (Từ chối bản sau, trả lỗi 409)**: Đơn giản nhưng FE phải xử lý conflict thủ công cho từng từ — trái với câu 6.1 (Word tự merge).
  * **Phương án C (Cho phép trùng, dedup chạy nền)**: Bỏ unique, job nền gộp. Dễ gây hiển thị trùng tạm thời.

**Trả lời:** A

---

### Câu 9.3: Xóa Topic thì Note bên trong ra sao?

* **Ai sai?** **FE lệch BE**, và cả hai lệch prototype:
  * FE `deleteTopicApi`: `topicId = null` cho note (note mồ côi, vẫn nằm trong dữ liệu nhưng không còn hiện dưới Topic nào).
  * BE `plan.md`: *"Xóa Topic & các Note thuộc Topic"*.
  * Prototype: *"Xóa topic cảnh báo số note bên trong; giữ context đã thu thập bằng snapshot nguồn"* → hàm ý **xóa note**, ngữ cảnh từ vựng giữ qua snapshot.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Xóa cascade, giữ `note_title_snapshot`)**: Xóa Topic = xóa các Note bên trong; `word_contexts.note_id = NULL`, giữ snapshot. FE sửa theo (và hiện cảnh báo "N notes will be deleted"). Sync cần tombstone cho cả Topic lẫn từng Note (liên quan Câu 11.2).
  * **Phương án B (Chuyển note về "Uncategorized")**: Không mất bài viết; BE và FE cùng đổi sang `topic_id = NULL` + một nhóm "Uncategorized" trong Sidebar. UI cần thêm nhóm này.

**Trả lời:** A

---

## NHÓM 10: REVIEW & SRS

### Câu 10.1: Thống nhất một bộ luật SRS duy nhất (bậc giãn cách, thang điểm, ngưỡng, tên field)

* **Ai sai?** Cả ba nguồn đang khác nhau:

  | Nội dung | Prototype (design) | FE code | BE (`plan.md`, đã duyệt ở 3.3) |
  | :--- | :--- | :--- | :--- |
  | Bậc giãn cách | 1 → 6 → ×2.5 | 1 → 6 → `round(interval × difficulty)` | 1 → 3 → 7 → 14 → ×`difficulty` |
  | `MASTERED` | streak ≥ 4 | `streak ≥ 4 && interval ≥ 14` | `streak ≥ 5 && interval ≥ 14` |
  | Sai | streak 0, interval 1 | thêm `difficulty −= 0.2` (min 1.3), status `LEARNING` | streak 0, interval 1, `LEARNING` (không nhắc `difficulty`) |
  | `difficulty` mặc định | ease 2.5 | `2.5` | entity `2.0` |
  | Điểm | Forgot / Remembered | quality `1` / `4` | `quality` 1..5, đúng nếu `≥ 3` |
  | Trạng thái `NEW → LEARNING` | — | `NEW` chuyển ngay `LEARNING` hoặc `TEMP_REMEMBERED` sau lần ôn đầu | `NEW` khi chưa ôn lần nào |

  * Prototype tự nói: lịch này là **mô phỏng, "không tuyên bố là engine SM-2 production"** → không nên coi nó là chuẩn.
  * **Ý kiến**: Bậc cố định `1/3/7/14` của BE (kiểu Leitner) **không phải SM-2**; SM-2 chuẩn là `1 → 6 → ×EF`. Tên `difficulty` dễ gây hiểu nhầm vì giá trị *lớn = dễ*. Quyết định cuối thuộc về bạn, nhưng cần **một bảng luật duy nhất**.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Giữ luật BE đã duyệt, bổ sung phần còn thiếu, FE sửa theo)**:
    * Bậc: `1 → 3 → 7 → 14` rồi `round(interval × easeFactor)`.
    * `MASTERED`: `streak ≥ 5 && interval ≥ 14`. `TEMP_REMEMBERED`: `streak 2..4`. `LEARNING`: `streak 0..1` **và đã ôn ít nhất 1 lần**. `NEW`: chưa có `review_log`.
    * `easeFactor` (đổi tên cột `difficulty` → `ease_factor`, API vẫn dùng `difficulty` hoặc `easeFactor`, chốt một tên): mặc định **2.5**, sai thì `−0.2`, tối thiểu **1.3**; đúng với `quality ≥ 4` thì giữ, `quality = 3` thì `−0.15`.
    * Mapping nút FE: **Forgot → quality 1**, **Remembered → quality 4**; điền từ: khớp chính xác → 5, typo chấp nhận → 4, synonym gợi ý → 3 (đúng nhưng cần ôn), sai → 1.
    * FE giữ **cùng một hàm tính** (copy từ spec + test chung bộ bảng ví dụ) **chỉ để hiển thị lạc quan khi offline**; BE luôn tính lại (xem Câu 10.2).
  * **Phương án B (Dùng SM-2 chuẩn 1 → 6 → ×EF, đổi luật BE theo FE/prototype)**: Ít sửa FE nhất; `MASTERED` cân nhắc `streak ≥ 4` hoặc `≥ 5`. Cần chốt lại cả ngưỡng `TEMP_REMEMBERED`.
  * **Phương án C**: Giữ riêng hai bộ luật (FE offline khác BE) và để BE ghi đè khi sync. Trải nghiệm nhảy số, khó giải thích.

**Trả lời:** A 

---

### Câu 10.2: Khi học offline, đồng bộ **sự kiện ôn tập** hay đồng bộ **trạng thái từ**?

* **Ai sai?** **Cả hai đều chưa trả lời được câu này** — và đây là nơi dễ sinh lỗi lệch dữ liệu nhất. Hiện FE:
  * Tự tính SRS cục bộ, đẩy **cả đối tượng Word** vào `syncQueue` (`UPDATE`), nhưng **không đẩy `reviewLogs`**.
  * BE câu 3.2 nói "Backend là nguồn chân lý SM-2". Nhưng nếu FE gửi trạng thái đã tính sẵn thì BE chỉ có thể "last write wins" (và hiện bị B2: bỏ qua theo `version`).
  * Hệ quả: `user_daily_stats`, `review_logs`, accuracy và streak trên BE **không có dữ liệu** cho các lượt ôn lúc offline → Progress sai.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Event-sourcing nhẹ: đồng bộ `review_logs`, BE tính lại SRS)**:
    * FE ghi mỗi lượt ôn thành sự kiện **bất biến** `{ eventId (UUID do client sinh), wordId, quality, isCorrect, mode, reviewedAt (giờ client, ISO UTC) }` và sync lên. `reviewLogs` Dexie đổi khóa chính từ auto-increment sang `eventId`.
    * BE **idempotent theo `eventId`**; nhận sự kiện của nhiều thiết bị, sắp theo `reviewedAt`, **replay SM-2** để ra trạng thái chuẩn → trả về `word` state mới nhất. FE ghi đè SRS cục bộ bằng bản của BE.
    * `user_daily_stats`, streak, counters đều tính từ sự kiện này (đúng ngày theo múi giờ người dùng, Câu 12.1) → **offline vẫn tính đúng streak**.
  * **Phương án B (Đồng bộ trạng thái, last-write-wins theo `updatedAt`)**: Đơn giản, nhưng mất dữ liệu analytics và xung đột âm thầm khi hai thiết bị cùng ôn một từ.
  * **Phương án C (Hybrid)**: Gửi cả sự kiện lẫn trạng thái; BE ưu tiên sự kiện, dùng trạng thái chỉ để kiểm tra chéo. Phức tạp hơn A, ít lợi thêm.

**Trả lời:** A 

---

### Câu 10.3: Hàng đợi ôn tập — dùng `offset` hay "chốt snapshot ID"?

* **Ai sai?** **BE sai (B1).** Chuỗi sự kiện: FE gọi `/due?limit=15&offset=0` → chấm 15 thẻ → BE ghi DB **bất đồng bộ** (câu 7.4) → danh sách due **co lại** (15 từ vừa chấm không còn due) → FE bấm "Ôn tiếp 15 thẻ" gọi `offset=15` → **bỏ qua 15 thẻ** chưa học. Nếu DB chưa ghi xong thì lại **trả trùng** 15 thẻ vừa chấm.
  * Prototype (mục 5.2 và 15) nói rõ: *"Chốt queue theo ID lúc bắt đầu chặng. Không truy vấn lại queue sau từng lần chấm"*.
  * FE hiện chỉ gọi `fetchDueCardsApi(15)` mỗi chặng (không dùng offset) → bản thân FE **đúng** hướng; chính kế hoạch `offset` của BE mới là vấn đề.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Phân trang bằng `excludeIds` / `sessionId`)**:
    * `GET /api/review/due?limit=15&excludeIds=a,b,c` (hoặc `sessionId`): trả tiếp các từ due **chưa nằm trong phiên**. Bỏ `offset`.
    * Cập nhật **trạng thái trong bộ nhớ ngay lập tức (đồng bộ)** khi nhận `submit`; chỉ phần ghi PostgreSQL mới bất đồng bộ → đọc tiếp theo luôn nhất quán.
    * Response: `{ totalDue, words: [...] }`. Nếu giữ cache (theo Câu 8.1) thì cache là danh sách due theo thứ tự ưu tiên, không phải cửa sổ theo vị trí.
  * **Phương án B (Giữ `offset`, nhưng "đóng băng" danh sách due đầu phiên trên BE)**: BE tạo `sessionId` và lưu danh sách ID due đã chốt (vd 60 ID) trong cache; `offset` chỉ trượt trên danh sách đóng băng. Thêm trạng thái phiên phía BE.
  * **Phương án C**: Chỉ cho FE tự chốt hoàn toàn (Câu 8.1-A): `/due` chỉ dùng khi hydrate, FE tự phân trang cục bộ. BE bỏ cả `offset` lẫn cache cửa sổ.

**Trả lời:** A 

---

### Câu 10.4: Hợp đồng `POST /api/review/submit` (enum `mode`, gửi lẻ hay theo lô, response)

* **Ai sai?** Chia ba phần:
  * **FE sai (F1)**: gửi `mode: "Flashcards"` / `"Fill in the blank"` (nhãn UI) thay vì enum.
  * **BE thiếu (B6)**: "202 Accepted" nhưng FE cần trạng thái mới (`status`, `nextReviewAt`, `intervalDays`) để cập nhật Progress/badge ngay.
  * **Câu 3.2 (A cho flashcard, B cho writing) cần xem lại**: Ở chế độ điền từ, FE **phải gọi `check-answer` cho từng câu** (nếu dùng AI) nên mỗi câu đã có một lượt mạng; gom 15 kết quả rồi mới `submit` không tiết kiệm mạng mà còn **mất cả chặng nếu đóng tab giữa chừng**.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Một endpoint nhận mảng, trả trạng thái chuẩn)**:
    * Body: `{ events: [{ eventId, wordId, quality, isCorrect, mode: "FLASHCARD"|"FILL_BLANK", reviewedAt }] }` (1..50 phần tử). FE gửi **từng thẻ** (best-effort, ngầm) ở cả hai chế độ; nếu mất mạng thì vào hàng đợi sync (Câu 10.2). Batch 15 chỉ là **tối ưu retry**, không phải hành vi bắt buộc của chế độ Writing.
    * BE tính SM-2 **đồng bộ trong bộ nhớ** (rất nhẹ), ghi DB bất đồng bộ, trả `200` kèm `{ words: [{ id, status, correctStreak, intervalDays, nextReviewAt, easeFactor }] }`. Idempotent theo `eventId`.
  * **Phương án B (Làm đúng Câu 3.2)**: Flashcard gửi lẻ; Writing gộp 15. Hai đường code, và Writing mất kết quả khi đóng tab.
  * **Phương án C**: `202 Accepted` không kèm body; FE tự tính lạc quan và đợi lần sync kế tiếp để thấy giá trị chuẩn.

**Trả lời:** A 

---

### Câu 10.5: `POST /api/review/check-answer` — gọi AI cho mọi câu hay chỉ khi cần?

* **Ai sai?** Không ai sai, nhưng có thể thiết kế thừa. FE đã tự chấm **exact + typo 1 ký tự (Levenshtein)** ngay trong trình duyệt (`FillBlankCard`), phản hồi tức thì và dùng được offline. Plan BE lặp lại đúng phần đó ở server và còn thêm AI cho đồng nghĩa.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - FE chấm exact/typo cục bộ; chỉ gọi BE khi không khớp)**:
    * Khớp chính xác hoặc typo ≤ 1 ký tự: FE tự chấm, **không gọi mạng**.
    * Chỉ khi **sai và đang online** mới gọi `POST /api/review/check-answer` để AI kiểm tra **đồng nghĩa hợp ngữ cảnh** (`SYNONYM_SUGGESTION`). Offline thì chấm sai như thường.
    * BE **không cần** cài lại Levenshtein cho luồng chính (chỉ giữ để kiểm tra chéo nếu muốn).
  * **Phương án B (Mọi câu đều gọi BE)**: Một nguồn chấm duy nhất, nhưng mỗi câu thêm độ trễ + tốn token AI + không hoạt động offline.
  * **Phương án C (Bỏ endpoint này ở giai đoạn đầu)**: Chỉ giữ chấm cục bộ; làm AI-synonym ở giai đoạn sau.

**Trả lời:** A 

---

## NHÓM 11: ĐỒNG BỘ (SYNC)

### Câu 11.1: Giao thức Delta Sync — con trỏ thời gian và cách FE hợp nhất

* **Ai sai?** **FE sai (F9) và BE chưa thiết kế đủ:**
  * Hiện FE và BE `batch` đang **nhất quán với nhau** theo kiểu "server trả toàn bộ dữ liệu, client `clear()` rồi `bulkPut`". Mô hình này **không dùng được** cho delta (và còn **mất dữ liệu chưa sync** nếu người dùng sửa trong lúc request đang bay).
  * `lastSyncAt` hiện lấy từ `UserSyncMetadata.updatedAt` của server (ổn), nhưng nếu thiết kế mới dùng **giờ của client** làm mốc thì **lệch đồng hồ** sẽ làm mất bản ghi.
  * FE bỏ qua `wordContexts` và `reviewLogs` — hai thực thể cốt lõi của thiết kế mới.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Push + Pull với con trỏ do SERVER cấp)**:
    * `POST /api/sync/delta` body: `{ cursor, changes: { topics, notes, words, wordContexts, reviewEvents }, deleted: { topicIds, noteIds, wordIds, contextIds } }`.
    * Response: `{ newCursor, changes (bản ghi thay đổi sau cursor), tombstones, idRemap, conflicts, counters }`. `cursor` là **thời điểm/chuỗi tăng dần do server cấp** (không phải giờ client).
    * FE **upsert theo ID** (không `clear()`); chỉ xóa khỏi `syncQueue` những mục **thực sự đã được gửi** (theo `id` của queue item lúc gửi), mục phát sinh trong lúc request vẫn còn đó.
    * Giữ `POST /api/sync/batch` ở chế độ tương thích tạm thời rồi gỡ sau.
  * **Phương án B (Giữ batch toàn bộ, chỉ thêm `wordContexts`/`reviewLogs`)**: Ít việc nhất, nhưng không tiết kiệm băng thông và vẫn rủi ro mất dữ liệu khi `clear()`.
  * **Phương án C**: Delta nhưng dùng `updatedAt` của từng bản ghi + `lastSyncAt` client. Dễ sai khi lệch đồng hồ.

**Trả lời:** A 

---

### Câu 11.2: Xử lý bản ghi đã xóa (Tombstone)

* **Ai sai?** **BE chưa có (B3).** Thiết bị A xóa một Note; thiết bị B pull delta chỉ nhận bản ghi `updated_at > cursor`. Bản ghi đã xóa **không còn trong DB** → B không bao giờ biết và vẫn giữ bản ghi "ma". FE đã gửi `deletedXxxIds` (đúng hướng) nhưng BE chưa trả ngược lại.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Soft delete: cột `deleted_at`, job dọn sau 30–90 ngày)**: Xóa = đặt `deleted_at`; delta trả `deleted_at ≠ NULL` như tombstone; các truy vấn đọc thường lọc `deleted_at IS NULL` (cần index phù hợp, tính vào partition của `user_words`).
  * **Phương án B (Bảng `sync_tombstones` riêng `(user_id, entity_type, entity_id, deleted_at)`)**: Bảng gốc vẫn xóa cứng (gọn bảng `user_words`, tốt cho HOT/fillfactor); chỉ bảng tombstone phình. Phù hợp hơn nếu muốn giữ `user_words` thật gọn.
  * **Phương án C**: Không tombstone; thiết bị mới luôn full-resync định kỳ. Tốn băng thông, không đảm bảo xóa lan truyền.

**Trả lời:** A 

---

### Câu 11.3: Số phiên bản (`version`) và phát hiện xung đột của Note

* **Ai sai?** **Cả hai sai (F8 + B2):**
  * FE tự `version + 1` khi sửa note cục bộ; BE dùng `@Version` của JPA (server tự tăng) nhưng lại **gán `dto.version()` của client** (`note.setVersion(dto.version())`) và `if (dto.version() < word.getVersion()) continue;` — **bỏ qua im lặng**, không báo conflict → mất dữ liệu.
  * Thiết kế đúng cần phân biệt "**tôi đã sửa dựa trên bản server số mấy**" (`baseVersion`) với "bản server hiện tại là số mấy".
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Server sở hữu `version`, client gửi `baseVersion`)**:
    * Server là nơi **duy nhất** tăng `version` (JPA `@Version`). FE lưu `serverVersion` (số version server trả về lần gần nhất) và gửi `baseVersion` khi push.
    * `baseVersion == version hiện tại` → ghi, trả version mới. `baseVersion < version` **và** nội dung (`title`/`content`) khác → **trả `conflicts`** (Note) để mở `SyncConflictModal`; nội dung giống nhau → coi như đã đồng bộ (không làm phiền).
    * Note đang `conflict` **không bị ghi đè** đến khi người dùng chọn (`POST /api/sync/resolve-conflict` với `KEEP_LOCAL`/`APPLY_CLOUD`; `KEEP_LOCAL` tạo version mới = version hiện tại + 1).
    * Word/WordContext/Topic: không báo conflict (câu 6.1): Word theo Câu 9.2 + 10.2, Topic last-write-wins theo `updatedAt` server.
  * **Phương án B**: Client tự tăng `version` và server so sánh trực tiếp (như hiện tại). Hai bên cùng tăng một số nên vẫn sinh sai lệch khi offline lâu.
  * **Phương án C**: Dùng `updatedAt` làm cơ sở xung đột (last-write-wins), bỏ `version`. Đơn giản nhưng âm thầm mất bản sửa của thiết bị bị ghi đè.

**Trả lời:** A 

---

### Câu 11.4: ID do client sinh, thứ tự ghi (REST vs `syncQueue`) và chống xử lý đôi

* **Ai sai?** **Cả hai (F7 + F10 + B5):**
  * FE sinh `crypto.randomUUID()` (**UUIDv4**) nhưng POST `/api/topics` **không gửi `id`** (BE `TopicRequest` cũng không nhận `id`) → BE sinh ID khác → FE xóa-rồi-ghi lại topic nhưng **không sửa `notes.topicId`** → note mồ côi. Plan BE muốn **UUIDv7** để chống phân mảnh index nhưng FE đang sinh v4.
  * FE ghi `syncQueue` **và** gọi REST cho cùng một thay đổi. Sau đó batch sync đẩy lại → có thể xử lý hai lần (tăng counter hai lần, tạo trùng).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Client sinh UUIDv7, mọi endpoint `create` là upsert idempotent theo `id`)**:
    * FE thêm tiện ích sinh **UUIDv7** (≈15 dòng, không cần thư viện); mọi `POST` đều gửi `id`. BE (`TopicRequest`, `NoteRequest`, `WordContextRequest`...) **nhận `id`**, nếu đã có thì cập nhật (idempotent). BE vẫn **kiểm tra** `id` hợp lệ và thuộc user; nếu không phải v7 vẫn chấp nhận (chỉ mất lợi thế locality).
    * **Bỏ hẳn cơ chế "đổi ID sau khi server trả"** ở FE. (Chỉ còn trường hợp hợp nhất từ trùng ở Câu 9.2 dùng `idRemap`.)
    * Quy tắc **một đường ghi**: FE luôn ghi IndexedDB + `syncQueue`; **không** gọi REST trực tiếp cho thay đổi dữ liệu. Sync worker (debounce 1.5 s hoặc ngay khi có mạng) đẩy queue lên. Với thao tác cần phản hồi tức thì từ server (dịch AI, chat, kiểm tra kết nối) vẫn gọi REST trực tiếp vì chúng không phải thay đổi dữ liệu.
  * **Phương án B (Giữ REST trực tiếp khi online, nhưng thành công thì xóa khỏi queue)**: Hai đường ghi; cần xử lý thứ tự và lỗi giữa chừng cẩn thận hơn.
  * **Phương án C (Server cấp ID, FE dùng ID tạm)**: FE giữ ID tạm rồi thay bằng ID server; phải cập nhật mọi khóa ngoại cục bộ. Phức tạp và dễ lỗi nhất.

**Trả lời:** A 

---

## NHÓM 12: PROGRESS & THỜI GIAN

### Câu 12.1: Múi giờ người dùng (ngày học, streak, `next_review_at`)

* **Ai sai?** **Cả hai (F4 + B9):**
  * FE dùng `toISOString().split("T")[0]` → ngày **UTC**. Người dùng ở UTC+7 ôn lúc 06:00 sáng bị ghi là ngày hôm qua; streak và "reviewed today" sai.
  * BE chưa có khái niệm múi giờ; `study_date` và `next_review_at` là `date` nhưng không nói theo múi giờ nào. Scheduler "đối soát nửa đêm" cũng không biết nửa đêm của ai.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Lưu IANA timezone của người dùng, tính ngày theo đó)**:
    * `UserAppConfig.timezone` (vd `Asia/Ho_Chi_Minh`), FE gửi khi đăng ký / đổi (lấy từ `Intl.DateTimeFormat().resolvedOptions().timeZone`).
    * `study_date`, streak, `next_review_at` (kiểu `date`) đều theo **ngày trong múi giờ người dùng**; dấu thời gian lưu **UTC** (`Instant`). Job đối soát chạy theo từng múi giờ (hoặc quét mỗi giờ các user vừa qua nửa đêm). FE sửa dùng hàm `localDateString()` thay `toISOString()`.
  * **Phương án B (Mốc ngày cố định 04:00 sáng theo giờ địa phương)**: Giống A nhưng ngày học đổi lúc 04:00 để người học khuya không bị đứt streak. Cần chốt giờ.
  * **Phương án C**: Giữ UTC, FE chấp nhận lệch. (Không khuyến nghị.)

**Trả lời:** A 

---

### Câu 12.2: Định nghĩa số liệu Dashboard và ai là bên tính

* **Ai sai?** Chủ yếu **FE (F5)** và **lệch tên (không ai sai)**:
  * FE hiển thị `/ 20` **cứng** dù có `dailyTarget`; `newAddedCount` fallback `total − 20` là số giả; lịch 35 ngày fallback là giả; `isCurrent` cố định ở ô 28 (đúng phải là ô cuối = hôm nay).
  * FE chỉ chấp nhận response khi có `accuracyPercent` (nếu BE đổi tên thành `recallAccuracy` thì **FE bỏ qua toàn bộ**).
  * Nhãn UI "added recently" mâu thuẫn với field BE `wordsAddedToday`. "Accuracy" của FE: hôm nay, nếu chưa có log thì toàn thời gian; BE chưa định nghĩa khoảng thời gian.
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Chuẩn hóa theo tên FE đang dùng, BE là bên tính khi online, FE gộp log chờ sync)**:
    * Response `GET /api/progress/dashboard`: `{ accuracyPercent (0..100 | null, tính trên 30 ngày gần nhất), reviewedToday, dailyTarget, wordsAddedToday, totalWords, streakDays, longestStreakDays, distribution: { new, learning, remembered, mastered }, activityCalendar: [{ date, count, wordsAdded, filled, isToday }] }` — đúng 35 phần tử, phần tử cuối là hôm nay.
    * FE đổi nhãn "added recently" → "added today"; bỏ toàn bộ dữ liệu giả; dùng `dailyTarget` từ response.
    * Khi offline, FE tính cục bộ từ Dexie (có đủ `reviewLogs` theo Câu 10.2); khi online, hiển thị bản của BE **cộng thêm các log đang chờ sync** để không bị "giật lùi" số liệu.
  * **Phương án B (Đổi theo tên BE trong `plan.md`)**: `recallAccuracy`, `dailyGoal`, `statusDistribution`, `activityGrid`. FE sửa nhiều hơn A nhưng khớp tài liệu BE.
  * **Phương án C**: Chỉ BE tính (online-only), offline hiển thị bản cache cuối. Đơn giản nhưng mâu thuẫn Offline-First của FE.

**Trả lời:** A 

---

## NHÓM 13: AI, 9ROUTER & CHAT

### Câu 13.1: Hợp đồng `AiSettings` và cách lưu API Key

* **Ai sai?** **FE sai (F6, F12) và hai bên lệch tên:**
  * **FE sai**: `saveAiSettingsApi` ghi toàn bộ payload (**gồm `apiKey` plaintext**) vào `localStorage` và Dexie. Điều này **vi phạm** BRD FE §6.4 ("không lưu plaintext ở LocalStorage"). `testAiConnectionApi` chỉ `setTimeout` giả vờ kết nối thành công.
  * **Lệch tên**: FE `baseUrl`, `modelName` | BE `customBaseUrl`; FE `provider` chỉ có `DEEPSEEK`/`GOOGLE` | BE thêm `9ROUTER`, `OPENAI`; BE thêm `chatModel`.
  * **BE cũng cần quy định**: `GET /api/ai-settings` **không bao giờ trả `apiKey` thật** (kẻ có token bị đánh cắp hoặc XSS sẽ lấy được key).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Key chỉ ghi, không đọc; tên chuẩn theo BE)**:
    * `PUT /api/ai-settings`: `{ provider, customBaseUrl?, apiKey? (bỏ trống = giữ key cũ), translationModel, quizModel, chatModel }`.
    * `GET /api/ai-settings`: `{ provider, customBaseUrl, hasApiKey: true|false, apiKeyHint: "sk-…a1b2", translationModel, quizModel, chatModel }` — **không bao giờ trả key**.
    * FE chỉ cache cấu hình **không gồm key** (provider, URL, model) vào Dexie; **không** lưu key ở bất cứ đâu trong trình duyệt ngoài lúc người dùng đang gõ. Gỡ `modelName` (dùng `translationModel`). `AiProvider` thêm `9ROUTER`, `OPENAI`. Nút Test gọi `POST /api/ai-settings/test` thật.
  * **Phương án B (Giữ tên FE `baseUrl`)**: Đổi `customBaseUrl` → `baseUrl` ở BE để FE ít sửa. Quy tắc bảo mật vẫn như A.
  * **Phương án C**: Giữ nguyên hiện trạng FE (cache key cục bộ) để tiện dùng offline. (Không khuyến nghị.)

**Trả lời:** A

---

### Câu 13.2: Topo triển khai của `9Router` và an toàn của `customBaseUrl` (SSRF)

* **Ai sai?** **BE chưa nghĩ tới (B7).** `http://localhost:20128/v1` được gọi **từ tiến trình Spring Boot**, nên "localhost" là máy chạy BE:
  * Nếu BE và 9Router cùng chạy trên máy người dùng (self-hosted): đúng.
  * Nếu BE chạy trên server/cloud: người dùng **không thể** dùng 9Router chạy trên máy cá nhân của họ.
  * Cho phép người dùng nhập **`customBaseUrl` tùy ý** trên một BE dùng chung là lỗ hổng **SSRF** (BE có thể bị lợi dụng gọi `http://169.254.169.254`, `http://localhost:8080/admin`, mạng nội bộ...).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Xác định rõ mô hình triển khai, áp danh sách cho phép (allow-list))**:
    * Chốt: ứng dụng là **self-hosted một người dùng** (đúng với "single-user notebook" của prototype): BE + 9Router cùng máy/Docker network. Dùng biến môi trường `NINE_ROUTER_URL`; `customBaseUrl` chỉ được phép thuộc **allow-list cấu hình** (mặc định: URL 9Router + `api.deepseek.com`, `generativelanguage.googleapis.com`, `api.openai.com`), chặn dải IP nội bộ/metadata khi không nằm trong allow-list.
  * **Phương án B (BE triển khai cloud đa người dùng)**: 9Router chạy riêng phía server, người dùng chỉ chọn provider + dán API key của họ; `customBaseUrl` chỉ dành cho admin. Bỏ ý định "9Router local của từng người".
  * **Phương án C**: Không giới hạn `customBaseUrl` (chấp nhận rủi ro vì chỉ dùng cá nhân). (Không khuyến nghị nếu có ngày triển khai chung.)

**Trả lời:** A 

---

### Câu 13.3: `translate-context` và `words/lookup` — ai là nơi tra "từ đã có chưa"?

* **Ai sai?** Không bên nào sai, nhưng có **hai nguồn chân lý trùng nhau**:
  * FE kiểm tra từ đã có **cục bộ** trong IndexedDB (tức thì, offline-first) — đúng với triết lý FE.
  * BE plan có `GET /api/words/lookup` **và** `POST /api/ai/translate-context` trả luôn `alreadyExists`/`existingContexts` — nếu FE đã biết cục bộ thì thừa, thậm chí có thể **mâu thuẫn** với dữ liệu cục bộ chưa sync.
  * `WordPopover` hiện dùng `dictionary` demo; chưa gọi AI thật. Nếu API dịch lỗi hoặc offline, popover phải cho nhập tay (đã có "Enter manually").
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Lookup cục bộ; `translate-context` chỉ làm đúng một việc: dịch)**:
    * Bỏ `alreadyExists`/`existingContexts` khỏi response `translate-context`. Response: `{ text, ipa, partOfSpeech, meaning, sentence, sentenceTranslation }` (tên theo Câu 9.1).
    * `GET /api/words/lookup` chỉ giữ cho trường hợp thiết bị chưa hydrate xong (có thể hoãn làm).
    * FE gọi `translate-context` chỉ khi **chưa có từ cục bộ** hoặc người dùng chọn "dịch nghĩa mới cho ngữ cảnh này"; cho phép nhập tay khi lỗi/offline.
  * **Phương án B**: Giữ nguyên plan BE; FE phải gọi `lookup` ở server mỗi lần bôi đen (chậm, không offline).
  * **Phương án C**: `translate-context` có `existingWordId` do FE gửi lên (đã tra cục bộ), BE chỉ dùng để làm giàu prompt (cho AI biết nghĩa cũ).

**Trả lời:** A 

---

### Câu 13.4: Chat — lưu ở đâu, có chạy offline không, và giao thức SSE

* **Ai sai?** Phần lớn là **FE chưa nối API (F12)**, nhưng có 3 điểm BE cần chốt:
  1. **Giao thức SSE**: `EventSource` của trình duyệt **chỉ hỗ trợ `GET` và không gửi được header `Authorization`**. `POST /api/chat/send` + JWT bắt buộc FE dùng `fetch` + `ReadableStream` (tự đọc `data: ...`). `res.json()` hiện tại trong `services/chat.ts` sẽ lỗi nếu BE stream.
  2. **Nguồn lịch sử**: FE gửi cả `history` trong body và giữ hội thoại trong React state (kèm 2 hội thoại giả `attention`/`practice`); BE lại lưu hội thoại trong PostgreSQL. Nếu cả hai đều đúng → hai nguồn lịch sử có thể lệch, và client có thể **giả mạo `history`**.
  3. **Offline**: FE hiện **giả vờ trả lời bằng `sampleReply`** khi BE lỗi — người dùng tưởng đó là AI thật (prototype ghi rõ "Sample responses, not connected to AI" nhưng bản FE tích hợp đã bỏ nhãn).
* **Các lựa chọn**:
  * **Phương án A (Khuyên dùng - Chat online-only, BE là nguồn lịch sử duy nhất, SSE qua `fetch`)**:
    * **Giao thức**: `POST /api/chat/send` `{ conversationId?, noteId?, wordIds?, message }`; response `text/event-stream`: `event: meta` (`conversationId`, `messageId`) → nhiều `event: chunk` `{ "text": "…" }` → `event: done` hoặc `event: error` `{ "code", "message" }`. Hỗ trợ hủy bằng `AbortController` (FE) → BE dừng gọi model.
    * **Lịch sử**: **BE bỏ qua `history` do client gửi**, luôn lấy từ DB theo `conversationId` (kiểm tra thuộc user). FE tải danh sách bằng `GET /api/chat/conversations` và tin nhắn bằng `GET .../{id}/messages`; bỏ dữ liệu giả; `conversationId` của hội thoại mới do **FE sinh UUIDv7** (Câu 11.4) để tạo idempotent.
    * **Offline**: tắt gửi và hiện thông báo rõ ràng "Chat needs a connection"; bỏ `sampleReply` hoặc gắn nhãn "Sample (offline)" nếu muốn giữ chút trải nghiệm. Chat **không** vào `syncQueue` (online-only).
  * **Phương án B (Chat cũng sync được qua delta)**: Cho phép soạn offline và gửi sau. Phức tạp (sắp xếp, trả lời AI bị trễ), lợi ích thấp.
  * **Phương án C (Giữ cả JSON và SSE)**: Hỗ trợ `Accept: application/json` (không stream) cho client đơn giản + SSE khi có `Accept: text/event-stream`. Dễ kiểm thử bằng curl, tốn thêm một nhánh code.

**Trả lời:** A 

---

## PHỤ LỤC: HỢP ĐỒNG CẦN CHỐT NỐT SAU KHI TRẢ LỜI (Tôi sẽ cập nhật `plan.md` & `BRD.md`)

Sau khi bạn trả lời, tôi sẽ:
1. Cập nhật `docs/plan.md`: sắp xếp lại thứ tự Phase (Sync + SRS chuẩn trước, Cache sau — nếu chọn 8.1-A), sửa API Map, thêm tombstone / idRemap / `eventId`.
2. Cập nhật `docs/BRD.md` (ERD: `primary_meaning`, `ease_factor`, `timezone`, tombstone, `review_logs.event_id`...).
3. Sinh một bản **`docs/api-contract.md`** liệt kê từng endpoint với request/response chuẩn (một tên field duy nhất).
4. Lập **danh sách việc cho FE** (F1…F12) thành một file `learning_english_fe/docs/be-alignment.md` để bạn xử lý bên FE song song.
