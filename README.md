# Smart English Notes - Backend API

Hệ thống Backend RESTful API và dịch vụ AI Gateway cho ứng dụng học tiếng Anh thông minh **Margin Smart English Notes**. Xây dựng trên nền tảng **Spring Boot 4.0**, **Java 25**, **PostgreSQL**, tích hợp thuật toán ôn tập ngắt quãng (Spaced Repetition System - SRS), đồng bộ thời gian thực và đa mô hình trí tuệ nhân tạo (Gemini, DeepSeek, OpenAI, 9Router).

---

## 🚀 Tính năng nổi bật

### 1. Quản lý ghi chú & Thu hoạch từ vựng ngữ cảnh (Multi-Context Harvesting)
- Phân cấp thư mục theo Chủ đề (Topics) và Ghi chú (Notes).
- Thu hoạch từ vựng kèm ngữ cảnh câu trực tiếp từ bài đọc: tự động chuẩn hóa từ (Unicode normalization, lowercase), lưu snapshot câu và nghĩa ngữ cảnh.
- Tránh trùng lặp từ vựng và tự động liên kết nhiều ngữ cảnh với cùng một từ vựng (`WordContext`).

### 2. Thuật toán ôn tập ngắt quãng (SRS - Spaced Repetition System)
- Triển khai thuật toán **SuperMemo SM-2** được tối ưu:
  - Tự động điều chỉnh `easeFactor`, `intervalDays`, và tính toán `nextReviewAt`.
  - Phân loại trạng thái từ: `NEW`, `LEARNING`, `REVIEW`, `MASTERED`.
- Bảng thống kê tiến độ học tập 35 ngày (Bento Activity Calendar), tính streak học tập liên tục theo múi giờ người dùng.
- Tối ưu hóa hiệu năng với hàng đợi Cache trượt (Sliding Word Window Cache) qua **Caffeine Cache** giúp tải trước 45 thẻ ôn tập tiếp theo dưới nền.

### 3. Cổng tích hợp Trí tuệ nhân tạo (Multi-Provider AI Gateway)
- Hỗ trợ linh hoạt nhiều nhà cung cấp AI:
  - **Google Gemini** (Gemini 2.5 Flash / Pro)
  - **DeepSeek** (DeepSeek V3 / Chat)
  - **OpenAI** (GPT-4o Mini / GPT-4o)
  - **9Router / Local Gateway** (tương thích OpenAI API format)
- Truyền phát tin nhắn trò chuyện trực tiếp qua **Server-Sent Events (SSE)**.
- Tra cứu, dịch thuật từ vựng theo ngữ cảnh câu (`/api/ai/translate-context`).
- Mã hóa an toàn API Key của người dùng bằng chuẩn AES (`app.encryption.secret-key`).

### 4. Bảo mật cao cấp (Enterprise Security Hardening)
- **JWT Authentication:** Cơ chế Access Token ngắn hạn và Refresh Token xoay vòng (Rotation) kèm khả năng thu hồi (Revoke) từ phía server.
- **Phòng chống SSRF (Server-Side Request Forgery):** Kiểm tra nghiêm ngặt `java.net.URI` với cơ chế whitelist (chỉ cho phép Scheme, Host và Port được định nghĩa), ngăn chặn triệt để tấn công URL parser confusion (`userinfo@`) và truy cập Cloud Metadata (`169.254.169.254`).
- **Mặt nạ API Key:** Che giấu hoàn toàn API key trong phản hồi DTO (`••••••••••••`).
- **Phân quyền Swagger UI:** Chỉ người dùng có vai trò `ROLE_ADMIN` mới được xem Swagger UI, mặc định tắt ở môi trường production.
- **CORS linh hoạt:** Kiểm soát danh sách domain được phép gọi API thông qua biến môi trường, tự động lọc và làm sạch khoảng trắng/dấu ngoặc kép.

---

## 🛠️ Công nghệ sử dụng

| Công nghệ | Phiên bản / Chi tiết |
| :--- | :--- |
| **Java** | OpenJDK 25 (Preview Features enabled) |
| **Framework** | Spring Boot 4.0.6 (Spring Framework 7.0 / Spring Security 7.0) |
| **Database** | PostgreSQL (Production) / H2 In-Memory (Test Profile) |
| **ORM / JPA** | Spring Data JPA, Hibernate ORM |
| **Security** | Spring Security, JJWT (io.jsonwebtoken 0.12.6), BCrypt |
| **Cache** | Caffeine Cache 3.2.0 |
| **API Docs** | SpringDoc OpenAPI 3.1.0 (v2.8.5) |
| **Build Tool** | Apache Maven (qua Maven Wrapper `./mvnw`) |

---

## 📋 Danh mục API chính

Tất cả các API yêu cầu xác thực JWT qua header: `Authorization: Bearer <access_token>` (ngoại trừ các endpoint public trong `/api/auth/`).

### 1. Authentication (`/api/auth`)
* `POST /api/auth/register`: Đăng ký tài khoản mới.
* `POST /api/auth/login`: Đăng nhập, nhận cặp `accessToken` & `refreshToken`.
* `POST /api/auth/refresh`: Cấp mới token khi access token hết hạn (Rotation).
* `POST /api/auth/logout`: Đăng xuất và thu hồi refresh token.
* `GET /api/auth/me`: Lấy thông tin cá nhân, cấu hình timezone và bộ đếm học tập.

### 2. Topics & Notes (`/api/topics`, `/api/notes`)
* `GET /api/topics`, `POST /api/topics`: Quản lý danh mục chủ đề ghi chú.
* `GET /api/notes`, `POST /api/notes`, `PUT /api/notes/{id}`, `DELETE /api/notes/{id}`: Quản lý bài ghi chú.

### 3. Từ vựng & Ngữ cảnh (`/api/words`)
* `POST /api/words/contexts`: Thu hoạch từ vựng mới cùng ngữ cảnh câu.
* `GET /api/words`: Danh sách từ vựng đã lưu kèm bộ lọc.
* `DELETE /api/words/{id}`: Xóa từ vựng.

### 4. Ôn tập SRS (`/api/review`)
* `GET /api/review/due?limit=15`: Lấy danh sách từ đến hạn cần ôn tập hôm nay.
* `POST /api/review/submit`: Gửi đánh giá kết quả ôn tập (chất lượng thẻ từ 0 - 5 theo SM-2).

### 5. AI Services & Trò chuyện (`/api/ai`, `/api/chat`, `/api/ai-settings`)
* `POST /api/ai/translate-context`: Dịch và phân tích từ theo câu bằng AI.
* `GET /api/chat/conversations`: Danh sách các đoạn chat với AI.
* `POST /api/chat/conversations/{id}/messages/stream`: Trò chuyện trực tuyến (SSE Streaming).
* `GET /api/ai-settings`, `PUT /api/ai-settings`: Cấu hình provider, custom URL và API key cá nhân.
* `POST /api/ai-settings/test-connection`: Kiểm tra kết nối tới gateway AI.

### 6. Cấu hình & Thống kê (`/api/progress`, `/api/v1/app-config`)
* `GET /api/progress/dashboard`: Thống kê chuỗi ngày học liên tục (streak) và biểu đồ đóng góp.
* `GET /api/v1/app-config`, `PUT /api/v1/app-config`: Lưu tùy biến giao diện, sidebar và mục tiêu học mỗi ngày.

---

## ⚙️ Cấu hình biến môi trường (Environment Variables)

Các biến môi trường có thể cấu hình thông qua file `.env`, Heroku Config Vars, Docker hoặc môi trường hosting:

| Tên biến | Mặc định | Mô tả |
| :--- | :--- | :--- |
| `PORT` | `8080` | Port lắng nghe của máy chủ backend |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/learning_english` | URL kết nối cơ sở dữ liệu PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | `postgres` | Tên đăng nhập cơ sở dữ liệu |
| `SPRING_DATASOURCE_PASSWORD` | `postgres` | Mật khẩu cơ sở dữ liệu |
| `APP_JWT_SECRET` | *(Default dev key)* | Khóa bí mật ký JWT (tối thiểu 256 bits cho HS256) |
| `APP_JWT_EXPIRATION_MS` | `900000` (15 phút) | Thời gian sống của Access Token |
| `APP_JWT_REFRESH_EXPIRATION_MS` | `604800000` (7 ngày) | Thời gian sống của Refresh Token |
| `APP_ENCRYPTION_KEY` | *(Default 32-char dev key)* | Khóa bí mật 32 ký tự để mã hóa API Key của người dùng |
| `CORS_ALLOWED_ORIGINS` | `https://www.mylearningeng.me,http://localhost:3000` | Danh sách domain Frontend được phép gọi API (phân cách bằng dấu phẩy) |
| `CORS_ALLOWED_ORIGIN_PATTERNS`| `https://*.mylearningeng.me,https://*.vercel.app` | Pattern domain Frontend (hỗ trợ preview deployments) |
| `SPRINGDOC_SWAGGER_UI_ENABLED` | `false` | Bật/tắt Swagger UI (`true` / `false`) |
| `GEMINI_API_KEY` | `""` | Khóa API Gemini mặc định hệ thống |
| `OPENAI_API_KEY` | `""` | Khóa API OpenAI / DeepSeek mặc định hệ thống |

---

## 📦 Hướng dẫn cài đặt & Chạy ứng dụng

### 1. Yêu cầu môi trường
* **Java Development Kit (JDK):** JDK 25 trở lên.
* **Cơ sở dữ liệu:** PostgreSQL 14+ (khởi tạo database `learning_english`).

### 2. Chạy ứng dụng ở môi trường Local Development
```bash
# Di chuyển vào thư mục backend
cd learning_english_be

# Cấp quyền thực thi cho Maven wrapper (nếu trên macOS/Linux)
chmod +x ./mvnw

# Chạy ứng dụng Spring Boot
./mvnw spring-boot:run
```

Sau khi khởi động thành công, backend sẽ lắng nghe tại: `http://localhost:8080`

### 3. Chạy kiểm thử tự động (Unit & Integration Tests)
Dự án được cấu hình profile `test` chạy trên cơ sở dữ liệu in-memory H2, không yêu cầu kết nối cơ sở dữ liệu ngoài khi chạy test:
```bash
# Chạy toàn bộ test suite (Bao gồm kiểm tra bảo mật SSRF, Password, JWT, SRS)
./mvnw test
```

### 4. Đóng gói triển khai (Build JAR)
```bash
./mvnw clean package -DskipTests
# File JAR thành phẩm được sinh ra tại: target/english-0.0.1-SNAPSHOT.jar
```

---

## 🛡️ Kiểm tra Bảo mật & Báo cáo Lỗ hổng
Dự án được rà soát và kiểm thử định kỳ:
- Đã khắc phục triệt để các lỗ hổng SSRF, CORS Wildcard, và bảo vệ chống rò rỉ dữ liệu nhạy cảm.
- Tất cả các trường hợp tấn công được tự động kiểm thử tại `src/test/java/com/learning/english/SecurityFixesTest.java`.

---

## 📄 Bản quyền
Phát triển cho hệ thống **Margin English Learning Application**.
Mọi quyền được bảo lưu © 2026.
