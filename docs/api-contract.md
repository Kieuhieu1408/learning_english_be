# HỢP ĐỒNG GIAO TIẾP API CHUẨN (`api-contract.md`)
## Dự án: Smart English Notes (Backend Spring Boot 4 ↔ Frontend Next.js / React 19)
### Phiên bản: **v2.1.0 (Single-Contract Standard)** — Ngày chốt: 2026-10-16

---

## 1. NGUYÊN TẮC QUY ƯỚC CHUNG

1. **Giao thức & Định dạng**:
   - Mọi request/response đều sử dụng `application/json; charset=UTF-8` (ngoại trừ endpoint Chat SSE sử dụng `text/event-stream`).
   - Header xác thực bắt buộc đối với tất cả endpoint nghiệp vụ: `Authorization: Bearer <jwt_token>`.
2. **Quy tắc đặt tên (Single-Contract Rule)**:
   - Tất cả JSON property name tuân theo chuẩn **`camelCase`**.
   - Mỗi thuộc tính dữ liệu chỉ có **một tên chuẩn duy nhất**. Tuyệt đối không dùng tên kép hoặc alias ngầm gây phân mảnh logic.
3. **Định dạng Thời gian & Ngày tháng**:
   - Thời điểm (Timestamps): Chuỗi ISO-8601 UTC (ví dụ: `2026-10-16T15:30:00Z`).
   - Ngày học (Calendar Dates): Chuỗi `yyyy-MM-dd` (ví dụ: `2026-10-16`) được tính toán **theo múi giờ IANA của người dùng** (`timezone` trong `UserAppConfig`, mặc định `Asia/Ho_Chi_Minh`).
4. **Định dạng Định danh (Identifiers)**:
   - Tất cả các ID thực thể (`id`, `noteId`, `wordId`, `eventId`, v.v.) là chuỗi **UUIDv7 (RFC 9562)** do Client hoặc Server sinh.
5. **Cơ chế Idempotent (Chống trùng lặp)**:
   - Các thao tác tạo mới (`POST /api/topics`, `POST /api/notes`, `POST /api/words/context`, `POST /api/chat/conversations`) chấp nhận trường `id` do Client sinh. Nếu `id` đã tồn tại, Server thực hiện cập nhật idempotent thay vì tạo bản ghi trùng lặp.

---

## 2. PHÂN HỆ AUTHENTICATION & BOOTSTRAP

### 2.1. `POST /api/auth/register` — Đăng ký tài khoản
* **Mô tả**: Tạo tài khoản mới, khởi tạo sẵn `UserSyncMetadata` và `UserAppConfig`.
* **Request Body**:
  ```json
  {
    "username": "kieuhieu",
    "password": "SecurePassword123",
    "email": "hieu@example.com",
    "name": "Hieu Kieu",
    "timezone": "Asia/Ho_Chi_Minh"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "userId": "019277f0-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "username": "kieuhieu",
    "role": "ROLE_USER",
    "user": {
      "username": "kieuhieu",
      "name": "Hieu Kieu",
      "email": "hieu@example.com",
      "role": "ROLE_USER"
    }
  }
  ```

---

### 2.2. `POST /api/auth/login` — Đăng nhập
* **Request Body**:
  ```json
  {
    "username": "kieuhieu",
    "password": "SecurePassword123"
  }
  ```
* **Response `200 OK`**: Cùng định dạng với API Register.

---

### 2.3. `GET /api/auth/me` — Lấy thông tin phiên & Cấu hình Bootstrap
* **Mô tả**: Được FE gọi khi mở ứng dụng để lấy thông tin người dùng, preferences và bộ đếm tổng quan.
* **Headers**: `Authorization: Bearer <token>`
* **Response `200 OK`**:
  ```json
  {
    "userId": "019277f0-1a2b-7c3d-8e4f-5a6b7c8d9e0f",
    "username": "kieuhieu",
    "name": "Hieu Kieu",
    "email": "hieu@example.com",
    "role": "ROLE_USER",
    "config": {
      "timezone": "Asia/Ho_Chi_Minh",
      "dailyTarget": 20,
      "theme": "light",
      "sidebarWidth": 244
    },
    "counters": {
      "dueCount": 15,
      "reviewedToday": 4,
      "totalWords": 142,
      "streakDays": 5,
      "lastSyncAt": "2026-10-16T15:00:00Z"
    }
  }
  ```

---

## 3. PHÂN HỆ TOPICS & NOTES

### 3.1. `GET /api/topics` — Lấy danh sách Topic
* **Response `200 OK`**:
  ```json
  [
    {
      "id": "019277f1-0001-7000-8000-000000000001",
      "name": "Personal growth",
      "noteCount": 3,
      "createdAt": "2026-10-15T08:00:00Z",
      "updatedAt": "2026-10-16T09:30:00Z"
    }
  ]
  ```

### 3.2. `POST /api/topics` — Tạo / Upsert Topic
* **Request Body**:
  ```json
  {
    "id": "019277f1-0001-7000-8000-000000000002",
    "name": "Technology & AI"
  }
  ```
* **Response `200 OK`**: Trả về `TopicDto` hoàn chỉnh.

### 3.3. `PUT /api/topics/{id}` — Đổi tên Topic
* **Request Body**: `{ "name": "Deep Learning & AI" }`
* **Response `200 OK`**: Trả về `TopicDto` sau khi cập nhật.

### 3.4. `DELETE /api/topics/{id}` — Xóa Topic (Cascade Soft-Delete)
* **Mô tả**: Đặt `deleted_at = now()` cho Topic và cascade soft-delete toàn bộ các Note bên trong Topic đó. Mọi câu ngữ cảnh từ vựng liên quan được giữ nguyên (`note_title_snapshot`).
* **Response `204 No Content`**.

---

### 3.5. `GET /api/notes` — Lấy danh sách Note
* **Query Params**: `?topicId=` (optional).
* **Response `200 OK`**:
  ```json
  [
    {
      "id": "019277f2-0001-7000-8000-000000000001",
      "topicId": "019277f1-0001-7000-8000-000000000001",
      "title": "The art of paying attention",
      "content": "Attention is not a static trait...",
      "harvestedWordCount": 8,
      "version": 3,
      "createdAt": "2026-10-15T08:30:00Z",
      "updatedAt": "2026-10-16T10:00:00Z"
    }
  ]
  ```

### 3.6. `GET /api/notes/{id}` — Chi tiết Note kèm danh sách từ đã lưu
* **Response `200 OK`**:
  ```json
  {
    "id": "019277f2-0001-7000-8000-000000000001",
    "topicId": "019277f1-0001-7000-8000-000000000001",
    "title": "The art of paying attention",
    "content": "Attention is not a static trait...",
    "version": 3,
    "savedWords": [
      {
        "wordId": "019277f3-0001-7000-8000-000000000001",
        "text": "linger",
        "meaning": "nán lại, lưu lại"
      }
    ],
    "createdAt": "2026-10-15T08:30:00Z",
    "updatedAt": "2026-10-16T10:00:00Z"
  }
  ```

### 3.7. `POST /api/notes` — Tạo Note mới (Idempotent Upsert)
* **Request Body**:
  ```json
  {
    "id": "019277f2-0001-7000-8000-000000000002",
    "topicId": "019277f1-0001-7000-8000-000000000001",
    "title": "Deep Work in a Distracted World",
    "content": "To produce at your peak level..."
  }
  ```
* **Response `200 OK`**: Trả về `NoteDto` hoàn chỉnh (`version = 1`).

### 3.8. `PUT /api/notes/{id}` — Cập nhật Note (Kèm phát hiện xung đột)
* **Request Body**:
  ```json
  {
    "baseVersion": 3,
    "topicId": "019277f1-0001-7000-8000-000000000001",
    "title": "The art of paying attention (Updated)",
    "content": "Updated content..."
  }
  ```
* **Response `200 OK`**: Trả về `NoteDto` với `version = 4`.
* **Response `409 Conflict`**: Nếu `baseVersion < serverVersion` và nội dung khác biệt.
  ```json
  {
    "error": "CONFLICT",
    "message": "Note has been modified on another device.",
    "conflict": {
      "noteId": "019277f2-0001-7000-8000-000000000001",
      "serverVersion": 4,
      "cloudTitle": "The art of paying attention (Cloud)",
      "cloudContent": "Content modified from Cloud...",
      "cloudUpdatedAt": "2026-10-16T12:00:00Z"
    }
  }
  ```

### 3.9. `DELETE /api/notes/{id}` — Xóa Note (Safe Soft-Delete)
* **Mô tả**: Đặt `deleted_at = now()` trên Note; tự động đặt `note_id = NULL` trên các `word_contexts` liên kết nhưng giữ nguyên `note_title_snapshot` và câu văn ví dụ.
* **Response `204 No Content`**.

---

## 4. PHÂN HỆ TỪ VỰNG ĐA NGỮ CẢNH (`UserWord` & `WordContext`)

### 4.1. `GET /api/words` — Tra cứu kho từ vựng
* **Query Params**: `?q=&status=&sortBy=recent|word|review&limit=50&offset=0`
* **Response `200 OK`**:
  ```json
  {
    "total": 142,
    "words": [
      {
        "id": "019277f3-0001-7000-8000-000000000001",
        "text": "linger",
        "primaryMeaning": "nán lại, lưu lại",
        "ipa": "/ˈlɪŋ.ɡər/",
        "status": "TEMP_REMEMBERED",
        "correctStreak": 3,
        "easeFactor": 2.5,
        "intervalDays": 7,
        "nextReviewAt": "2026-10-23",
        "contextsCount": 2,
        "contexts": [
          {
            "id": "019277f4-0001-7000-8000-000000000001",
            "noteId": "019277f2-0001-7000-8000-000000000001",
            "noteTitle": "The art of paying attention",
            "sentence": "We lingered over coffee on a quiet Sunday.",
            "sentenceTranslation": "Chúng tôi nán lại bên tách cà phê vào một ngày Chủ nhật yên tĩnh.",
            "meaning": "nán lại, lưu lại",
            "partOfSpeech": "verb",
            "createdAt": "2026-10-15T09:00:00Z"
          }
        ],
        "createdAt": "2026-10-15T09:00:00Z",
        "updatedAt": "2026-10-16T11:00:00Z"
      }
    ]
  }
  ```

### 4.2. `POST /api/words/context` — Lưu từ vựng & Thu thập ngữ cảnh
* **Mô tả**:
  - Tự động normalize text (`lower(trim(text))` kèm strip dấu câu biên).
  - Nếu từ **chưa có**: Tạo `UserWord` (`primaryMeaning = meaning`, `status = NEW`) và tạo `WordContext` đầu tiên.
  - Nếu từ **đã có**: Bổ sung `WordContext` mới, giữ nguyên 100% chỉ số SRS của từ.
* **Request Body**:
  ```json
  {
    "wordId": "019277f3-0001-7000-8000-000000000001",
    "contextId": "019277f4-0002-7000-8000-000000000001",
    "text": "linger",
    "meaning": "nấn ná, chần chừ",
    "sentence": "She lingered at the door, reluctant to say goodbye.",
    "sentenceTranslation": "Cô ấy nấn ná ở cửa, không nỡ nói lời từ biệt.",
    "partOfSpeech": "verb",
    "ipa": "/ˈlɪŋ.ɡər/",
    "noteId": "019277f2-0001-7000-8000-000000000002",
    "noteTitleSnapshot": "Deep Work in a Distracted World"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "word": { ... },
    "isNewWord": false,
    "context": { ... }
  }
  ```

### 4.3. `DELETE /api/words/{id}` — Xóa từ vựng (Cascade contexts)
* **Response `204 No Content`**.

### 4.4. `DELETE /api/words/{wordId}/contexts/{ctxId}` — Xóa 1 ngữ cảnh cụ thể
* **Response `204 No Content`**.

---

## 5. PHÂN HỆ ÔN TẬP LẶP LẠI NGẮT QUÃNG (SRS & REVIEWS)

### 5.1. `GET /api/review/due` — Lấy danh sách thẻ đến hạn
* **Query Params**: `?limit=15&excludeIds=uuid1,uuid2`
* **Response `200 OK`**:
  ```json
  {
    "totalDue": 24,
    "words": [
      {
        "id": "019277f3-0001-7000-8000-000000000001",
        "text": "linger",
        "primaryMeaning": "nán lại, lưu lại",
        "ipa": "/ˈlɪŋ.ɡər/",
        "status": "LEARNING",
        "correctStreak": 1,
        "easeFactor": 2.5,
        "intervalDays": 1,
        "nextReviewAt": "2026-10-16",
        "contexts": [
          {
            "id": "019277f4-0001-7000-8000-000000000001",
            "sentence": "We lingered over coffee on a quiet Sunday.",
            "sentenceTranslation": "Chúng tôi nán lại bên tách cà phê vào một ngày Chủ nhật yên tĩnh.",
            "noteTitle": "The art of paying attention"
          }
        ]
      }
    ]
  }
  ```

---

### 5.2. `POST /api/review/submit` — Nộp kết quả ôn tập (Event-Sourced Idempotent)
* **Mô tả**:
  - Nhận danh sách 1 hoặc nhiều sự kiện ôn tập (`events`).
  - Idempotent tuyệt đối theo `eventId`.
  - Replay SM-2 theo múi giờ người dùng, trả về ngay trạng thái chuẩn của các thẻ vừa ôn.
* **Request Body**:
  ```json
  {
    "events": [
      {
        "eventId": "019277f5-0001-7000-8000-000000000001",
        "wordId": "019277f3-0001-7000-8000-000000000001",
        "quality": 4,
        "isCorrect": true,
        "mode": "FLASHCARD",
        "reviewedAt": "2026-10-16T15:35:10Z"
      }
    ]
  }
  ```
  *(Lưu ý: `mode` nhận đúng 2 giá trị enum: `"FLASHCARD"` hoặc `"FILL_BLANK"`).*
* **Response `200 OK`**:
  ```json
  {
    "updatedWords": [
      {
        "id": "019277f3-0001-7000-8000-000000000001",
        "status": "TEMP_REMEMBERED",
        "correctStreak": 2,
        "easeFactor": 2.5,
        "intervalDays": 3,
        "nextReviewAt": "2026-10-19"
      }
    ],
    "reviewedToday": 5,
    "streakDays": 5
  }
  ```

---

### 5.3. `POST /api/review/check-answer` — Chẩn đoán đáp án Điền từ & Từ đồng nghĩa
* **Mô tả**: Gọi khi người dùng điền từ không khớp tuyệt đối với từ gốc để AI chẩn đoán ngữ cảnh.
* **Request Body**:
  ```json
  {
    "wordId": "019277f3-0001-7000-8000-000000000001",
    "sentence": "We decided to ______ over coffee instead of rushing home.",
    "typedAnswer": "stay"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "outcome": "SYNONYM_SUGGESTION",
    "isAccepted": true,
    "quality": 3,
    "feedback": "‘stay’ fits grammatically in this context. However, the original card to practice is ‘linger’."
  }
  ```
  *(Các giá trị `outcome`: `EXACT_MATCH`, `TYPO_ACCEPTED`, `SYNONYM_SUGGESTION`, `INCORRECT`).*

---

## 6. PHÂN HỆ TIẾN ĐỘ & BENTO ANALYTICS

### 6.1. `GET /api/progress/dashboard` — Lấy số liệu Bento Grid 35 ngày
* **Mô tả**: Đọc dữ liệu tổng hợp theo múi giờ người dùng trong `< 2ms`.
* **Response `200 OK`**:
  ```json
  {
    "accuracyPercent": 88,
    "reviewedToday": 15,
    "dailyTarget": 20,
    "wordsAddedToday": 3,
    "totalWords": 142,
    "streakDays": 5,
    "longestStreakDays": 14,
    "distribution": {
      "new": 12,
      "learning": 34,
      "remembered": 68,
      "mastered": 28
    },
    "activityCalendar": [
      {
        "date": "2026-09-12",
        "count": 0,
        "wordsAdded": 0,
        "filled": false,
        "isToday": false
      },
      {
        "date": "2026-10-16",
        "count": 15,
        "wordsAdded": 3,
        "filled": true,
        "isToday": true
      }
    ]
  }
  ```
  *(Ghi chú: `activityCalendar` luôn trả về đúng 35 phần tử từ `today - 34 days` đến `today`).*

---

## 7. PHÂN HỆ ĐỒNG BỘ VI SAI (DELTA SYNC)

### 7.1. `POST /api/sync/delta` — Đồng bộ vi sai hai chiều
* **Mô tả**: Trao đổi vi sai giữa Client và Server. Server cấp monotonic `newCursor`.
* **Request Body**:
  ```json
  {
    "cursor": "2026-10-16T14:00:00Z",
    "timezone": "Asia/Ho_Chi_Minh",
    "changes": {
      "topics": [],
      "notes": [
        {
          "id": "019277f2-0001-7000-8000-000000000001",
          "baseVersion": 2,
          "title": "The art of paying attention",
          "content": "Updated offline content..."
        }
      ],
      "words": [],
      "wordContexts": [],
      "reviewEvents": [
        {
          "eventId": "019277f5-0002-7000-8000-000000000001",
          "wordId": "019277f3-0001-7000-8000-000000000001",
          "quality": 4,
          "isCorrect": true,
          "mode": "FLASHCARD",
          "reviewedAt": "2026-10-16T14:30:00Z"
        }
      ]
    },
    "deleted": {
      "topicIds": [],
      "noteIds": [],
      "wordIds": [],
      "contextIds": []
    }
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "newCursor": "2026-10-16T15:00:00Z",
    "changes": {
      "topics": [],
      "notes": [],
      "words": [],
      "wordContexts": []
    },
    "tombstones": {
      "topicIds": [],
      "noteIds": ["019277f2-0001-7000-8000-000000000099"],
      "wordIds": [],
      "contextIds": []
    },
    "idRemap": {
      "019277f3-9999-7000-8000-000000000001": "019277f3-0001-7000-8000-000000000001"
    },
    "conflicts": [],
    "counters": {
      "dueCount": 14,
      "reviewedToday": 16,
      "totalWords": 142,
      "streakDays": 5
    }
  }
  ```

---

### 7.2. `POST /api/sync/resolve-conflict` — Giải quyết xung đột Note
* **Request Body**:
  ```json
  {
    "noteId": "019277f2-0001-7000-8000-000000000001",
    "choice": "KEEP_LOCAL",
    "resolvedTitle": "My Chosen Title",
    "resolvedContent": "My Chosen Content"
  }
  ```
  *(Lựa chọn: `"KEEP_LOCAL"` hoặc `"APPLY_CLOUD"`).*
* **Response `200 OK`**: Trả về `NoteDto` với version mới nhất.

---

## 8. PHÂN HỆ CẤU HÌNH AI & DỊCH THUẬT

### 8.1. `GET /api/ai-settings` — Lấy cấu hình AI
* **Mô tả**: Tuyệt đối **không trả API Key thật** về Client (chống lộ lọt/XSS).
* **Response `200 OK`**:
  ```json
  {
    "provider": "9ROUTER",
    "customBaseUrl": "http://localhost:20128/v1",
    "hasApiKey": true,
    "apiKeyHint": "sk-...7a9f",
    "translationModel": "deepseek-chat",
    "quizModel": "deepseek-chat",
    "chatModel": "deepseek-chat"
  }
  ```

### 8.2. `PUT /api/ai-settings` — Lưu cấu hình AI
* **Mô tả**: Nếu `apiKey` rỗng hoặc không truyền, Server giữ nguyên khóa cũ đã mã hóa.
* **Request Body**:
  ```json
  {
    "provider": "9ROUTER",
    "customBaseUrl": "http://localhost:20128/v1",
    "apiKey": "sk-new-key-or-blank",
    "translationModel": "deepseek-chat",
    "quizModel": "deepseek-chat",
    "chatModel": "deepseek-chat"
  }
  ```
* **Response `200 OK`**: Trả về cấu hình an toàn giống API GET.

### 8.3. `POST /api/ai-settings/test` — Kiểm tra kết nối AI & Tải danh sách Models
* **Response `200 OK`**:
  ```json
  {
    "connected": true,
    "message": "Connected successfully to 9Router gateway.",
    "models": [
      { "id": "deepseek-chat", "name": "DeepSeek V3" },
      { "id": "gemini-2.5-flash", "name": "Gemini 2.5 Flash" }
    ]
  }
  ```

---

### 8.4. `POST /api/ai/translate-context` — Dịch từ vựng theo ngữ cảnh câu văn
* **Request Body**:
  ```json
  {
    "text": "linger",
    "sentence": "We lingered over coffee on a quiet Sunday.",
    "noteId": "019277f2-0001-7000-8000-000000000001",
    "noteTitle": "The art of paying attention"
  }
  ```
* **Response `200 OK`**:
  ```json
  {
    "text": "linger",
    "ipa": "/ˈlɪŋ.ɡər/",
    "partOfSpeech": "verb",
    "meaning": "nán lại, lưu lại",
    "sentence": "We lingered over coffee on a quiet Sunday.",
    "sentenceTranslation": "Chúng tôi nán lại bên tách cà phê vào một ngày Chủ nhật yên tĩnh."
  }
  ```

---

## 9. PHÂN HỆ AI CHAT COMPANION (SSE STREAMING)

### 9.1. Quản lý Hội thoại
- `GET /api/chat/conversations` $\rightarrow$ Danh sách `[{ id, title, updatedAt }]`.
- `POST /api/chat/conversations` $\rightarrow$ Tạo mới: `{ id (UUIDv7 client), title, noteId? }`.
- `GET /api/chat/conversations/{id}/messages` $\rightarrow$ Lịch sử tin nhắn: `[{ id, role, content, createdAt }]`.
- `PUT /api/chat/conversations/{id}` $\rightarrow$ Đổi tên: `{ title: "New title" }`.
- `DELETE /api/chat/conversations/{id}` $\rightarrow$ Xóa cuộc hội thoại và tin nhắn (`204 No Content`).

### 9.2. `POST /api/chat/send` — Gửi tin nhắn & Streaming phản hồi (SSE)
* **Headers**: `Accept: text/event-stream`, `Authorization: Bearer <token>`
* **Request Body**:
  ```json
  {
    "conversationId": "019277f6-0001-7000-8000-000000000001",
    "noteId": "019277f2-0001-7000-8000-000000000001",
    "wordIds": ["019277f3-0001-7000-8000-000000000001"],
    "message": "Giải thích sắc thái từ linger trong bài viết này giúp mình."
  }
  ```
* **Response Stream (`text/event-stream`)**:
  ```text
  event: meta
  data: {"conversationId":"019277f6-0001-7000-8000-000000000001","messageId":"019277f7-0001-7000-8000-000000000001"}

  event: chunk
  data: {"text":"Trong "}

  event: chunk
  data: {"text":"ngữ cảnh bài viết, "}

  event: chunk
  data: {"text":"từ 'linger' mang hàm ý..."}

  event: done
  data: [DONE]
  ```
* **Khi có lỗi (Error Event)**:
  ```text
  event: error
  data: {"code":"RATE_LIMIT","message":"9Router rate limit exceeded. Please retry."}
  ```
