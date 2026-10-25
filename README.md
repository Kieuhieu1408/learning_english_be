# Smart English Notes - Backend API

Hệ thống Backend RESTful API và dịch vụ AI Gateway cho ứng dụng học tiếng Anh thông minh **Margin Smart English Notes**. Xây dựng trên nền tảng **Spring Boot 4.0**, **Java 25**, **PostgreSQL**, tích hợp thuật toán ôn tập ngắt quãng (Spaced Repetition System - SRS), đồng bộ thời gian thực.

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
- Hỗ trợ linh hoạt nhiều nhà cung cấp AI
- Tra cứu, dịch thuật từ vựng theo ngữ cảnh câu (`/api/ai/translate-context`).
- Mã hóa an toàn API Key của người dùng bằng chuẩn AES (`app.encryption.secret-key`).
  
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


