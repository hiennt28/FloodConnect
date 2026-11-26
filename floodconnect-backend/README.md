# FloodConnect – Backend (Spring Boot 3 / Java 17 / PostgreSQL)

Backend cho đề tài *Mạng xã hội cộng đồng tích hợp AI hỗ trợ quản lý sự cố lũ lụt* (Nhóm 7).
Bám theo: `MÔ TẢ TÓM TẮT BÀI TOÁN`, `Db` (26 thực thể), `TÀI LIỆU ĐẶC TẢ V2`.

## Chạy nhanh (không cần Docker)
Yêu cầu: **JDK 17+**, **Maven 3.9+**, **PostgreSQL 14+** kèm **PostGIS 3** cài trực tiếp trên máy
(Windows: sau khi cài PostgreSQL, mở *Stack Builder* → *Spatial Extensions* → PostGIS).

1. Tạo cơ sở dữ liệu (pgAdmin hoặc `psql`):
   ```bash
   psql -U postgres -f docs/create-database.sql
   ```
2. Khai báo kết nối (mặc định `postgres/postgres` ở `localhost:5432`). Nếu khác, đặt biến môi trường trước khi chạy:
   ```bash
   # Windows PowerShell
   $env:DB_USER="postgres"; $env:DB_PASSWORD="mat_khau_cua_ban"
   # Linux/macOS
   export DB_USER=postgres DB_PASSWORD=mat_khau_cua_ban
   ```
   hoặc sửa thẳng `src/main/resources/application.yml`.
3. Tạo `.env` từ `.env.example`, **bắt buộc** điền `JWT_SECRET` và `ADMIN_PASSWORD` (xem bên dưới).
4. Chạy:
   ```bash
   mvn spring-boot:run          # http://localhost:8080  — Swagger: /swagger-ui.html (chỉ khi SWAGGER_ENABLED=true trong .env)
   ```
**Cấu trúc CSDL do Flyway tự dựng**, không phải chạy tay file SQL nào: khi khởi động, Flyway chạy lần lượt các file
`src/main/resources/db/migration/V*.sql` chưa chạy (V1 cấu trúc bảng + PostGIS, V2 dữ liệu địa giới, V3 sửa ràng buộc và chỉ mục...)
và ghi lịch sử vào bảng `flyway_schema_history`. Lần đầu mất thêm vài giây để nạp dữ liệu địa giới. Sau đó Hibernate chỉ **kiểm tra**
entity khớp CSDL (`ddl-auto=validate`); nếu lệch (vd. thêm field vào entity mà chưa viết migration), backend báo lỗi ngay khi khởi động.

Lần chạy đầu cũng tạo tài khoản Admin `ADMIN_EMAIL` (mặc định `admin@floodconnect.vn`) với mật khẩu là `ADMIN_PASSWORD`, **bắt buộc
đặt trong `.env`** trước lần chạy đầu (≥ 8 ký tự, có chữ và số; thiếu thì backend không khởi động). Đổi `ADMIN_PASSWORD` sau lần chạy
đầu không đổi mật khẩu Admin đã có: đổi trong Trang cá nhân.

**Thay đổi cấu trúc CSDL** (thêm bảng/cột, sửa ràng buộc, thêm chỉ mục): thêm file mới `V<số kế tiếp>__mo_ta_ngan.sql` vào
`db/migration`, **không sửa** file V đã chạy (Flyway kiểm tra checksum và sẽ từ chối khởi động).

**CSDL tạo trước khi có Flyway (trước 12/11/2026):** chạy cho đủ `docs/legacy/upgrade-2026-11.sql`, rồi khởi động backend **một lần**
với `FLYWAY_BASELINE=true` (Flyway ghi nhận CSDL đã có V1, V2 rồi tự chạy V3 trở đi), các lần sau bỏ biến này. Không đặt biến này
thì Flyway từ chối chạy trên CSDL có sẵn bảng mà chưa có lịch sử Flyway. Các script cũ: xem `docs/legacy/README.md`.

### Dữ liệu địa giới hành chính (bảng `admin_wards`)
Ranh giới 3.321 phường/xã (34 tỉnh, sau sắp xếp 2025), dùng để biết một tọa độ thuộc phường/xã nào và vẽ đường viền xã (form SOS,
bản đồ đặt ghim trụ sở tổ chức), lấy tên tỉnh/xã chuẩn theo mã xã, điểm đại diện của xã (tọa độ bài đăng khi không có GPS) và kiểm
tra ghim trụ sở nằm đúng xã. Bảng và dữ liệu do Flyway tạo (V1, `V2__admin_wards_data.sql`), không qua Hibernate.

Nguồn: [thanglequoc/vietnamese-provinces-database](https://github.com/thanglequoc/vietnamese-provinces-database) (giấy phép MIT),
commit `8b78ba5` (22/09/2026). Danh sách đơn vị lấy từ Tổng cục Thống kê; ranh giới gốc từ Bản đồ tra cứu đơn vị hành chính
Việt Nam (sapnhap.bando.com.vn). Ranh giới đã đơn giản hóa (sai số khoảng 33 m), chỉ dùng để kiểm tra và hiển thị,
không dùng làm căn cứ pháp lý. Tên đơn vị giữ cách viết dấu đang dùng trong hệ thống ("Hòa", "Thủy").

Cập nhật khi có nghị quyết điều chỉnh địa giới: tải bản mới của repo (chỉ cần thư mục `json/geojson`, để ngoài dự án) rồi chạy
`node scripts/geo/build-admin-geo.mjs <thư mục json/geojson> <mã commit>` ở thư mục gốc; script sinh lại
`vn-admin-2025.json` (frontend), `scripts/geo/out/admin_wards_data.sql` (chép thành một migration Flyway **mới**, không sửa V2) và
`docs/admin-geo-rename.sql` (câu đổi tên tỉnh/xã của tổ chức đã lưu, gộp vào cùng migration mới đó). Xem giải thích ở đầu script.

**Bắt buộc**: đặt `JWT_SECRET` (chuỗi ngẫu nhiên ≥ 32 byte) trong `.env`, thiếu hoặc dùng chuỗi mẫu thì backend không khởi động.
Tạo khóa bằng PowerShell: `$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)`.

Biến môi trường chính (xem `application.yml`): `DB_URL, DB_USER, DB_PASSWORD, JWT_SECRET (bắt buộc, ≥32 byte), FRONTEND_URL,
CORS_ORIGINS, MAIL_ENABLED/MAIL_USER/MAIL_PASSWORD, AI_SERVICE_URL, AI_TOKEN, ADMIN_PASSWORD (bắt buộc lần chạy đầu), SWAGGER_ENABLED,
AUTH_RATE_LIMIT_ENABLED, FLYWAY_BASELINE (chỉ dùng một lần cho CSDL cũ)`.

### Kiểm thử
- `mvn test`: unit test, không cần CSDL.
- `mvn -Pit verify`: thêm **kiểm thử tích hợp** (các lớp `*IT.java` trong `src/test/java/vn/floodconnect/it`): khởi động ứng dụng thật
  trên CSDL riêng `floodconnect_test` (tạo một lần: `psql -U postgres -f docs/create-test-database.sql`; Flyway tự dựng bảng), gọi API
  như frontend. Mỗi bài test xóa sạch dữ liệu bảng trước khi chạy và chỉ chịu chạy khi tên CSDL kết thúc bằng `_test`.
  Cấu hình ở `src/test/resources/application-it.yml` (tắt mail, thời tiết, tác vụ định kỳ; đổi CSDL bằng `IT_DB_URL`).

## Kiến trúc
```
src/main/java/vn/floodconnect/
├── common/        dto/ enums/ exception/ response/ storage/ util/   (không phụ thuộc module nghiệp vụ)
├── config/        SecurityConfig, CORS (WebConfig), WebSocket STOMP, dữ liệu mẫu
├── security/      JwtService, JwtAuthFilter, CurrentUser
├── auth/          controller/ dto/ entity/ repository/ service/   (đăng nhập, đăng ký, refresh token, email)
├── user/          controller/ dto/ entity/ repository/ service/ (UserService, UserMapper, AccountLockService) scheduler/
├── incident/      controller/ dto/ entity/ repository/ service/ (SosService: người dân; ActiveSosService: mọi ca đang hoạt động;
│                  IncidentMapper; MapService; StatusFilter)
├── rescue/        controller/ (OrgRescueController) service/ (RescueService: tổ chức xử lý ca; SosAdminService: Admin điều phối;
│                  AdminSosMapService: bản đồ điều phối của Admin)
│                  matching/ (MatchingService 0,25·L + 0,30·S + 0,30·C + 0,15·W) scheduler/ (RescueScheduler) dto/ entity/ repository/
├── organization/  controller/ (OrganizationController) service/ (OrgService, OrgMapper, OrgContext) dto/ entity/ repository/
├── post/          controller/ dto/ entity/ repository/ service/ (PostService, PostMapper)
├── notification/  controller/ dto/ entity/ repository/ service/ event/ (NotificationPublisher, *NotificationEvent, NotificationEventListener)
├── ai/            dto/ service/   (AiClient: cầu nối dịch vụ AI floodconnect-ai /v1/assess, /v1/match, có ngắt mạch + dự phòng;
│                  SosAssessor: đánh giá mức qua AI hoặc quy tắc Java; CapabilityRules; WeatherService)
├── campaign/      controller/ (CampaignController, OrgCampaignController, PaymentController) service/ (CampaignService,
│                  DonationService, CampaignMapper) payment/ scheduler/ dto/ entity/ repository/
├── admin/         controller/ dto/ service/ (AdminOrganizationService, AdminAccountService, AdminModerationService, AdminStatsService)
├── stats/         controller/ dto/ service/   (PublicStatsService: số liệu công khai cho trang giới thiệu, lưu tạm 5 phút)
└── FloodConnectApplication.java
Mỗi module tự chuyển entity sang DTO bằng *Mapper của mình. Các endpoint /api/org/* nằm ở 3 controller theo module.

Thông báo: module khác gọi `NotificationPublisher` (phát sự kiện), `NotificationEventListener` nhận và giao cho
`NotificationService` lưu DB + đẩy WebSocket. Listener chạy đồng bộ, cùng transaction với nghiệp vụ (giữ nguyên hành vi cũ).

Kiểm thử (`mvn test`): ngoài test đơn vị, `architecture/ArchitectureTest` chặn các phụ thuộc sai
(common/ import module nghiệp vụ, class khác import controller, controller gọi thẳng repository, module khác gọi thẳng NotificationService).
```
- **Xác thực**: JWT access (30 phút) + refresh token xoay vòng (băm SHA-256 trong DB); khóa đăng nhập 5 phút sau 5 lần sai;
  xác thực email và đặt lại mật khẩu bằng mã OTP 6 số (hiệu lực 10 phút, tối đa 5 lần sai, gửi lại cách nhau 60 giây);
  không lộ việc email có tồn tại. Đăng ký kiểm tra DNS của tên miền email khi `MAIL_ENABLED=true` (tắt kiểm tra ở chế độ giả lập).
- **Phân quyền**: `/api/admin/**` = ADMIN, `/api/org/**` = RESCUE_ORG, còn lại cần đăng nhập (khách xem được bảng tin/bài/bình luận/hồ sơ công khai).
  `GET /api/public/**` không cần đăng nhập: chỉ trả con số tổng hợp cho trang giới thiệu, không có dữ liệu cá nhân.
- **Realtime**: `ws://host/ws` (STOMP), CONNECT kèm header `Authorization: Bearer <jwt>`, subscribe `/user/queue/notifications`.
- **Tệp**: `STORAGE_PROVIDER=local` (mặc định) lưu `./uploads`, phục vụ `/files/**`; `STORAGE_PROVIDER=cloudinary` tải lên Cloudinary.
  Đổi/xóa ảnh (ảnh đại diện, logo, ảnh bìa và ảnh minh họa chiến dịch, ảnh bỏ khi sửa bài) thì tệp cũ bị xóa bằng
  `FileStorageService.deleteAfterCommit(url)`: chỉ chạy **sau khi** giao dịch commit (rollback thì tệp còn nguyên), chặn đường dẫn `../`,
  chỉ xóa tệp do chính hệ thống lưu (URL ngoài bị bỏ qua), xóa lỗi chỉ ghi log. Bài viết bị xóa mềm/gỡ vẫn giữ ảnh để Admin xem lại.
  **Kiểm tra tệp tải lên** (`FileStorageService.checkedType`): tối đa 5 MB; định dạng xác định từ **nội dung** (chữ ký vài byte đầu:
  JPG, PNG, WEBP, PDF), không tin kiểu do trình duyệt gửi theo đuôi tên tệp. Tệp đổi đuôi giả (vd. ảnh HEIC của iPhone đổi tên `.jpg`)
  → `400 INVALID_FILE_CONTENT`; định dạng thật nhưng chỗ đó không nhận (vd. ảnh đại diện WEBP) → `400 INVALID_FILE_TYPE`; đuôi khi lưu
  theo nội dung thật. Tệp vừa tải lên mà giao dịch CSDL sau đó bị hủy (vd. tệp thứ hai của cùng bài bị từ chối) thì bị xóa luôn,
  không thành tệp mồ côi.

## Quy tắc nghiệp vụ đã cài (trích)
| Nhóm | Nội dung |
|---|---|
| Đăng ký | Email/SĐT trùng một đăng ký **chưa xác thực và quá 24 giờ** được tự xóa, giải phóng cho người đăng ký mới (trừ hồ sơ tổ chức Admin đã xét duyệt — giữ nguyên) |
| SOS | **Chỉ tài khoản người dân gửi được** (tổ chức, Admin: 403 `SOS_CITIZEN_ONLY`). Mỗi người chỉ có **một ca đang hoạt động** tại một thời điểm (phải Hủy/Hoàn thành ca trước, hoặc ca đã bị Admin đánh dấu Không hợp lệ, mới gửi ca mới); không nhận ảnh đính kèm; cảnh báo trùng (500 m / 30 phút) rồi để người dân quyết định (`forceCreate`); AI chấm mức; `KHONG` → chỉ Admin xác minh, mức khác → Admin + tổ chức trong 50 km |
| Dịch vụ AI | `AI_SERVICE_URL` (vd `http://localhost:8000`) trỏ tới dịch vụ AI chung **floodconnect-ai** (thư mục cùng cấp). Đánh giá ca: `POST /v1/assess` (PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm ca lân cận + rule engine, chỉ nâng không hạ); backend gửi kèm các ca đang hoạt động trong `AI_CLUSTER_RADIUS_M` (1000 m) / `AI_CLUSTER_WINDOW_MINUTES` (360) cùng mức theo nội dung của chúng và cờ Admin đã điều chỉnh, rồi tự lưu kết quả (cột mới `content_level`, `source` của `incident_ai_assessments`, Flyway V9). Matching: `POST /v1/match` (luật cứng + năng lực + BGE-M3 + khoảng cách + tải; trọng số đổi theo mức đầy đủ của tin, gửi kèm trong `matching.weights`). AI chưa cấu hình, lỗi, quá hạn chờ hoặc chưa nạp model: dùng bộ quy tắc Java bên dưới và ghi rõ trong lý do; sau một lần lỗi, phần đó dùng quy tắc dự phòng trong `AI_RETRY_AFTER_SECONDS` (30) giây để không chờ hết hạn chờ ở từng ca. `AI_TOKEN` = `ADMIN_TOKEN` của dịch vụ AI. Hợp đồng API: `floodconnect-ai/docs/TICH_HOP_BACKEND.md` |
| AI (quy tắc dự phòng) | Chỉ **nâng** mức, không hạ: nhóm nguy cơ cao tối thiểu CAO; thời tiết xấu / ≥2 ca lân cận nâng 1 bậc; mơ hồ → cảnh báo xác minh; người dân sửa mô tả → AI chấm lại y như gửi ca mới, mức theo đúng kết quả AI (có thể lên hoặc xuống); bộ quy tắc dự phòng nhận cả câu gõ không dấu (`VnText`: so nguyên từ sau khi bỏ dấu, trừ các từ bỏ dấu dễ nhầm như mưa/mua, sập/sắp) |
| AI Matching (dự phòng) | `0,25·L + 0,30·S + 0,30·C + 0,15·W` (mỗi thành phần 0..100, chỉ gợi ý). **L** vị trí (khoảng cách trụ sở → ca). **S** tương đồng ngữ nghĩa (embedding; không có AI service thì so theo nhóm từ khóa) giữa mô tả ca + ghi chú xác minh và tên, mô tả, khu vực hoạt động của tổ chức. **C** năng lực (`ai/service/CapabilityRules`): nhu cầu của ca (mô tả + ghi chú xác minh) và năng lực tổ chức (tên + mô tả) theo 4 nhóm cứu hộ – sơ tán / y tế / tiếp tế / vận chuyển – hậu cần; mô tả tổ chức có phủ định ("chưa có xuồng") thì không tính; C = nhu cầu được đáp ứng / số nhu cầu × 100, ca chưa rõ nhu cầu → 50 cho mọi tổ chức. **W** tình trạng tiếp nhận: số ca đang nhận, ca gần vị trí, số lần từ chối / không phản hồi trong `MATCHING_REFUSAL_WINDOW_HOURS` giờ gần đây (mặc định 3). Mô tả tổ chức bắt buộc (≤ 2000 ký tự) |
| Nhận ca | Tổ chức chủ động `claim` (khóa dòng chống nhận trùng) hoặc Admin `assign` → tổ chức `accept`/`reject`; quá 15 phút chưa ai nhận / quá 5 phút không phản hồi → thông báo Admin, ghi `TIMEOUT` vào lịch sử cho AI Matching; ca Đã tiếp nhận quá 2 giờ chưa hoàn thành (`ACCEPTED_REMINDER_HOURS`) → nhắc tổ chức và Admin một lần, không tự đổi trạng thái |
| Sửa/hủy | `PENDING`: sửa mô tả hoặc hủy. Sửa mô tả: AI chấm lại y như gửi ca mới (mức, cờ "nên xác minh" theo đúng kết quả AI, có thể hạ); thành `KHONG` thì báo Admin xác minh và ca rời danh sách tổ chức, từ `KHONG` thành mức khác thì báo Admin + tổ chức trong 50 km, đổi giữa Thấp..Khẩn cấp thì không báo lại; ca Admin đã điều chỉnh mức thì không chấm lại · `ASSIGNED`: chỉ hủy — nội dung giữ nguyên từ lúc Admin giao ca, để tổ chức quyết định đúng trên mô tả đã dùng khi tính matching (sửa → `409 SOS_NOT_EDITABLE`) · `ACCEPTED`: không sửa, không hủy, chỉ báo "Tôi đã an toàn". Vị trí ca không sửa được sau khi gửi |
| Danh sách ca | Ca **đang hoạt động** (Chờ xử lý, Đã phân công, Đã tiếp nhận) luôn tải đủ, kể cả ca gửi đã lâu: `GET /api/sos/active` (trần `SOS_ACTIVE_MAX` = 1000, trả kèm `total`; Admin thấy cả ca chờ xác minh). Ca **đã kết thúc** xem theo trang: `GET /api/sos?status=COMPLETED` (người dân), `GET /api/admin/sos?status=ENDED` (Admin), `GET /api/org/sos/history` (tổ chức). Bản đồ Admin `GET /api/admin/sos/map`: ca đang hoạt động trước, ca đã kết thúc trong `days` ngày (tính theo lúc kết thúc), tối đa `MAP_MAX_MARKERS` = 1000 |
| Ca lân cận | Admin xem một ca → `GET /api/admin/sos/{id}/nearby`: các ca khác trong 500 m **tính tại lúc xem** (có cả ca gửi sau), gồm ca đang hoạt động (mọi mức) và ca đã hoàn thành gửi từ 24 giờ trước ca đó trở về sau; xếp gần nhất trước. Số "ca SOS khác trong 500 m" lưu trong đánh giá AI chỉ là số lúc gửi, không dùng để xác minh |
| Admin nhãn | Chỉ khi `PENDING`, lưu lịch sử điều chỉnh; Admin chỉ chọn `THAP`..`KHAN_CAP` (nhãn `KHONG` chỉ do AI gán, nghĩa là "chờ xác minh"); ca KHONG được nâng mức → gửi tổ chức trong 50 km. Xác minh ca KHONG **bắt buộc ghi chú xác minh** (`note` trong `adjust-level`, ≤ 1000 ký tự, cột `incidents.verification_note`, Flyway V7): thông tin Admin hỏi được khi liên hệ người gửi; mô tả gốc giữ nguyên; Admin, chủ ca và tổ chức xem được (cùng quyền với mô tả); ca mức khác không nhận ghi chú (`NOTE_NOT_ALLOWED`) |
| Nên xác minh | Ca đã gửi tổ chức nhưng AI chưa chắc chắn (mô tả ngắn/độ tin cậy < 70%, hoặc sửa mô tả bị chấm thấp hơn) có cờ `needsVerification`. Admin liên hệ người gửi rồi `POST /api/admin/sos/{id}/confirm-verification` (ca đang hoạt động, mức khác `KHONG`): gỡ cờ, giữ nguyên mức và trạng thái, lưu Admin/thời điểm xác minh; AI gắn lại cờ khi nội dung đổi. Khác với "Cần xác minh" (nhãn `KHONG`, ca chưa gửi tổ chức) |
| Không hợp lệ | Admin đã liên hệ xác minh, ca không phải yêu cầu cứu hộ → `POST /api/admin/sos/{id}/mark-invalid` (bắt buộc lý do, chỉ ca `PENDING`, mọi mức) → trạng thái kết thúc `INVALID`, lưu lý do/Admin/thời điểm, báo người gửi kèm lý do; ẩn với tổ chức/người dân khác; ca KHONG không bao giờ tự đóng (AI có thể nhầm ca thật) |
| Chiến dịch | UPCOMING sửa hết; ACTIVE chỉ sửa nội dung + kéo dài hạn; EXPIRED chỉ gia hạn; các nguyên nhân kết thúc khác phải tạo mới; đạt mục tiêu → tự ENDED(`TARGET_REACHED`) |
| Ủng hộ | Số tiền mỗi lần trong `PAYMENT_MIN_AMOUNT`..`PAYMENT_MAX_AMOUNT` (mặc định 5.000 – 999.999.999 đ, ngoài khoảng → `INVALID_AMOUNT`). **Chỉ qua VNPay** (trên trang VNPay người ủng hộ chọn ngân hàng, quét QR hoặc nhập thẻ; `paymentMethod` khác `VNPAY` → `PAYMENT_METHOD_NOT_SUPPORTED`). Tiền vào tài khoản merchant của hệ thống; chiến dịch công khai **ngân hàng, số tài khoản, chủ tài khoản** của tổ chức (nơi nhận khi giải ngân — giải ngân/đối soát nằm ngoài phạm vi đề tài). Giao dịch `PENDING` được lưu và commit trước, rồi mới gọi cổng (không giữ kết nối DB khi cổng chậm), cổng lỗi → `FAILED`; callback cổng → `SUCCESS/FAILED` (idempotent, khóa dòng, cộng tiền trong transaction); quá `PAYMENT_PENDING_EXPIRE_MINUTES` (mặc định 30, phải > hạn 15 phút của VNPay) chưa có kết quả → `EXPIRED`, nhưng nếu cổng báo thành công về muộn vẫn ghi nhận; callback giả lập bắt buộc đăng nhập và chỉ chính người ủng hộ mới gọi được |
| Khóa | Người dân/tổ chức bị khóa vẫn đăng nhập nhưng không đăng bài/tương tác/báo cáo (tổ chức còn không nhận ca mới, tạo chiến dịch); chiến dịch của tổ chức bị khóa → `ENDED/ACCOUNT_LOCKED`; ca "Đã phân công" (chưa xác nhận) trả về Chờ xử lý để Admin điều phối lại; tự mở khóa khi hết hạn; đặt lại mật khẩu thành công thì tự mở khóa đăng nhập và tự xác thực email |
| Kiểm duyệt | Xử lý theo cấp bài/chiến dịch: hoàn tất mọi báo cáo `PENDING`, thông báo người báo cáo (+ chủ bài/tổ chức khi xóa/dừng); xóa mềm |

## API chính
Phân trang: API danh sách nhận `page` (từ 0) và `size`; server tự kẹp (`common/util/Paging`): `size` trong 1..100, `page` âm → 0, trang quá xa → trang rỗng. Trường `size` trong kết quả là số đã kẹp.

Auth `POST /api/auth/{register,register-org,login,refresh,logout,verify-email,resend-verification,forgot-password,reset-password}` (`verify-email`/`reset-password` nhận `{email, code}` — mã OTP 6 số, không còn link) ·
Hồ sơ `GET/PUT /api/users/me`, `/me/password`, `PUT|DELETE /me/avatar`, `/me/gps` · `GET /api/users/search?q&type=CITIZEN|ORG`, `/api/users/{id}`, `POST|DELETE /api/users/{id}/follow` ·
Bài viết `GET /api/posts?filter=all|following`, `POST/PUT/DELETE /api/posts[/{id}]`, `/like`, `/report`, `/comments?page=&size=` (bình luận gốc mới nhất trước), `/api/comments/{id}` (một bình luận, mở từ thông báo), `/api/comments/{id}/replies?page=&size=` ·
Thông báo `GET /api/notifications?page=&size=` (mới nhất trước), `/unread-count`, `PUT /{id}/read`, `/read-all` ·
SOS `POST /api/sos`, `GET /api/sos/active` (mọi ca đang hoạt động, luôn đủ, trần `SOS_ACTIVE_MAX` = 1000, trả kèm `total`), `GET /api/sos?status=&page=&size=` (ca đã hoàn thành theo trang), `/sos/mine`, `/sos/{id}`, `PUT /sos/{id}`, `POST /sos/{id}/cancel`, `GET /api/map/markers` (Admin: chỉ trụ sở tổ chức) ·
Địa giới (bảng `admin_wards`) `GET /api/geo/locate?lat=&lng=` (tọa độ thuộc phường/xã nào), `GET /api/geo/wards/{code}` (điểm đại diện + đường viền GeoJSON); chưa nạp dữ liệu → `503 GEO_UNAVAILABLE`; công khai cho khách (GET, không cần đăng nhập) vì form đăng ký tổ chức cần đặt ghim trụ sở. Bài đăng chỉ gửi `wardCode` (+ thôn/xóm, số nhà): server lấy tên tỉnh/xã và tọa độ (GPS nếu nằm đúng xã, không thì điểm đại diện của xã). Hồ sơ tổ chức gửi `wardCode` + tọa độ ghim trụ sở đặt trên bản đồ: server lấy tên tỉnh/xã theo mã và bắt ghim nằm trong xã đó (`LOCATION_OUTSIDE_WARD`), để AI Matching dùng tọa độ chính xác. Cả hai cần nạp dữ liệu địa giới ·
Tổ chức `GET /api/org/sos/suggested`, `/org/sos?filter=ACTIVE|ALL|...` (giao diện đồng bộ `ACTIVE`), `/org/sos/history?page=&size=` (Hoàn thành, Đã hủy, theo trang), `POST /org/sos/{id}/{claim,accept,reject,complete}`, `PUT /org/me` (tổ chức đã duyệt sửa mô tả, trang web, địa chỉ, ghim trụ sở; không gồm tên, loại, giấy tờ), `PUT /org/me/operating-status`, `PUT /org/me/resubmit`, `/api/org/campaigns…` ·
Chiến dịch `GET /api/campaigns`, `/{id}`, `/{id}/donations`, `POST /{id}/donate`, `/{id}/report`, `GET /api/donations/mine` ·
Admin `/api/admin/{sos,orgs,citizens,post-reports,campaign-reports,stats/*}…` (xem `AdminController`); `orgs?filter=NEW|ACTIVE|REJECTED|VIOLATING|LOCKED`, `citizens?filter=VIOLATING|LOCKED`, `citizens/search?q=&page=&size=` (tất cả người dân, tìm không dấu theo tên / email / SĐT); `sos?status=ACTIVE|ENDED|<trạng thái>&page=&size=` (bảng ca đã kết thúc theo trang); bản đồ điều phối `sos/map?status=ACTIVE|ALL|<trạng thái>&level=&days=1..90` (ca đang hoạt động luôn đủ; ca đã kết thúc chỉ trong `days` ngày, mặc định 7, tính theo lúc kết thúc; tối đa `MAP_MAX_MARKERS` = 1000 ca, trả kèm `total`); thống kê:
`stats/{citizens,orgs,top-orgs,top-donors,overview}`, `stats/sos?period=day|month|year`, `stats/sos-status?period=`, `stats/sos-timeline?days=1..90` ·
Công khai (không cần đăng nhập) `GET /api/public/stats` → `{completedSos, activeOrgs, activeCampaigns, totalRaised, updatedAt}` (lưu đệm 5 phút), `GET /api/public/available-orgs` → `{count}` (tổ chức đang Sẵn sàng, không lưu đệm).

Các endpoint có tệp đính kèm nhận **multipart**: phần `data` (JSON, `Content-Type: application/json`) + phần tệp
(`media`, `images`, `avatar`; hồ sơ tổ chức gửi theo từng mục `documents` (giấy tờ chính), `idFront`, `idBack`
(CCCD hai mặt của trưởng nhóm thiện nguyện, mỗi mặt 1 tệp), `evidence` (minh chứng, không bắt buộc)). Riêng tạo/sửa bài và SOS
còn có bản JSON thuần khi không có tệp.

**Giấy tờ pháp lý / CCCD của tổ chức lưu RIÊNG TƯ** (khác ảnh đại diện, ảnh bài viết, ảnh chiến dịch vẫn công khai):
Cloudinary kiểu `authenticated` (link không chữ ký bị chặn) hoặc thư mục `STORAGE_PRIVATE_DIR` (mặc định `./private-uploads`,
không phục vụ qua web, nhớ sao lưu). API không trả đường dẫn tệp; tải qua `GET /api/org-docs/{id}/file`, chỉ Admin và chính tổ chức
sở hữu (người khác 404, khách 401); backend đọc tệp bằng chữ ký bí mật rồi trả nội dung, không đưa link Cloudinary ra trình duyệt.
CSDL lưu mã tệp nội bộ (cột `storage_key`, Flyway V6). Gửi lại hồ sơ thì tệp bị thay được xóa luôn.
Dữ liệu cũ đang lưu công khai: chạy backend **một lần** với `MIGRATE_ORG_DOCS=true` (chuyển sang riêng tư rồi xóa bản công khai,
ghi log từng tệp), sau đó đặt lại `false`.
Lỗi trả về dạng `{status, code, message, errors{field:msg}}`.

## Kết nối AI Service (tùy chọn)
Đặt `AI_SERVICE_URL=http://localhost:8000`, service Python cần có:
`POST /predict-severity {"text"} → {"severity":"KHAN_CAP|CAO|TRUNG_BINH|THAP|KHONG","confidence":0..1}` (trường khác trong kết quả, ví dụ `incident_type`, được bỏ qua) và
`POST /embed {"texts":[..]} → {"embeddings":[[..],[..]]}`. Bỏ trống hoặc service lỗi → tự dùng bộ quy tắc dự phòng trong Java.

## Cập nhật gần nhất
Đợt sửa sau buổi review 11–12/11/2026 (mới nhất trước):
- **Ghi chú xác minh** cho ca AI xếp KHONG được Admin chọn lại mức (xem dòng "Admin nhãn"). Flyway V7 thêm cột này và cột
  `incident_matching_scores.capability_score` (điểm năng lực C; dòng chấm trước đó để NULL).
- **Công thức AI Matching mới** `0,25·L + 0,30·S + 0,30·C + 0,15·W` (xem dòng "AI Matching" trong bảng quy tắc). Mô tả tổ chức
  thành bắt buộc (đăng ký, gửi lại hồ sơ, sửa thông tin) vì năng lực của tổ chức được suy ra từ mô tả.
- **Kiểm tra tệp tải lên theo nội dung** và xóa tệp khi giao dịch bị hủy (xem mục Tệp ở trên). Bỏ đường dẫn `/payment/mock` không dùng
  (chế độ giả lập vẫn giữ: `paymentUrl` trả `null`, giao diện tự mở hộp thoại giả lập). `.env.example` liệt kê đủ mọi biến.
- **Danh sách theo trang**: ca đang hoạt động luôn đủ, ca đã kết thúc theo trang (xem bảng quy tắc, dòng "Danh sách ca"); báo cáo vi phạm
  theo trang (câu đếm riêng cho truy vấn `GROUP BY`, sắp phụ theo id để các trang không trùng/sót); `stats/overview` có thêm số bài /
  chiến dịch đang có báo cáo chờ; tab "Người dân vi phạm" truy vấn thẳng thay vì tải mọi người dân; `GET /api/comments/{id}`;
  `GET /api/public/available-orgs`; bình luận gốc và chiến dịch sắp phụ theo id.
- **Bản đồ điều phối của Admin** `GET /api/admin/sos/map` (lọc trong CSDL, có trần) thay cho việc tải mọi ca; `/api/map/markers` với
  Admin chỉ còn trụ sở tổ chức.
- **N+1**: chuyển bài đăng, bình luận, chiến dịch, báo cáo, kết quả tìm kiếm, tổ chức đề xuất sang DTO theo cả trang (`PostMapper.posts`,
  `CampaignMapper.campaigns`, `MatchingService.computeAll`...). Sửa lỗi cấu hình: khối `spring.jpa.properties.hibernate`
  (`default_batch_fetch_size`, `jdbc.time_zone`) từng nằm nhầm dưới `spring.flyway` nên mất tác dụng. `QueryCountIT` đếm số câu SQL mỗi API
  (bảng tin 10 bài: 54 → 8 câu; tổ chức đề xuất: 39 → 7).
- **Giới hạn phân trang** dùng chung `common/util/Paging` (size 1..100).
- **Tài liệu pháp lý / CCCD của tổ chức lưu riêng tư** (Flyway V6 `storage_key`), tải qua `GET /api/org-docs/{id}/file` có kiểm tra quyền.
- **Tổ chức tự sửa thông tin** `PUT /api/org/me` (mô tả, trang web, địa chỉ, ghim trụ sở; không gồm tên, loại, giấy tờ); người ngoài chỉ thấy
  Phường/Xã, Tỉnh của trụ sở.
- **Chỉ người dân gửi SOS**; giới hạn số tiền ủng hộ; giới hạn độ dài trường chữ; chuyển trạng thái chiến dịch theo từng chiến dịch có khóa dòng;
  người chưa xác thực email không xuất hiện trong tìm kiếm / hồ sơ công khai; khóa ngoại cho bảng `follows` (Flyway V4).
- **Tìm kiếm không phân biệt dấu** (extension `unaccent`, Flyway V5); Admin có tab tổ chức "Đang hoạt động" và "Tất cả người dân".
- **Flyway** quản lý cấu trúc CSDL (V1–V7) và bộ **kiểm thử tích hợp** trên PostgreSQL thật (`mvn -Pit verify`).
- **Bảo mật**: giới hạn gửi OTP (5 mã/giờ) và số lần nhập sai không bị hoàn tác khi lỗi; giới hạn tần suất gọi API đăng nhập/đăng ký theo IP;
  bắt buộc `JWT_SECRET` (≥ 32 byte, không dùng khóa mẫu) và `ADMIN_PASSWORD` cho lần chạy đầu; thông báo lỗi không lộ chi tiết thư viện;
  Swagger tắt mặc định (`SWAGGER_ENABLED`).

Trước đó:
- **Thống kê theo thời gian cho Admin** (`AdminStatsService`): `stats/sos-timeline?days=` trả số ca gửi và số ca hoàn thành từng ngày
  (giờ Việt Nam, tính bằng `AT TIME ZONE 'Asia/Ho_Chi_Minh'` trong SQL; ngày trống = 0) cùng 3 thời lượng trung bình: chờ đến lần phân công
  đầu tiên, tổ chức phản hồi lời chỉ định (nhận/từ chối, không tính TIMEOUT/hủy), gửi đến hoàn thành. `stats/overview` (ca đang mở, tổng tiền
  ủng hộ thành công) và `stats/sos-status` thay cho việc giao diện tự đếm trên 100 bản ghi mới nhất (sai khi có nhiều dữ liệu).
- **Số liệu công khai cho trang giới thiệu** (module `stats`): `GET /api/public/stats`, lưu tạm 5 phút trong bộ nhớ để khách tải lại liên tục
  không làm CSDL đếm lại mỗi lượt.
- **Xóa ảnh đại diện / logo tổ chức**: `DELETE /api/users/me/avatar` (chưa có ảnh thì không làm gì).
- **Dọn tệp ảnh cũ**: đổi ảnh đại diện, đổi ảnh bìa chiến dịch, xóa ảnh minh họa chiến dịch, bỏ ảnh khi sửa bài đều xóa tệp cũ
  sau khi commit (`FileStorageService.deleteAfterCommit`, hỗ trợ cả local và Cloudinary). Trước đây tệp cũ nằm lại vĩnh viễn.
- **Ca lân cận khi Admin xác minh**: `GET /api/admin/sos/{id}/nearby` thay cho con số "ca khác trong 500 m" chụp lúc gửi (không
  cập nhật khi có ca gửi sau, nên ca KHONG gửi trước luôn thấy 0). Giao diện Admin hiện danh sách, bấm để mở ca, có nút quay lại.
- **Bỏ "loại sự cố" và ẩn vị trí chính xác của ca SOS**: bỏ trường `incidentType` (trước đây AI suy ra bằng từ khóa, gần như mọi ca
  đều là "Ngập lụt"). Người không có quyền xem chi tiết ca (người dân khác) chỉ nhận tọa độ làm tròn 3 chữ số thập phân (~100 m)
  ở cả `IncidentDto` lẫn `/api/map/markers`; chủ ca, tổ chức và Admin vẫn nhận tọa độ chính xác. Dọn cột cũ: bước 11 của `docs/legacy/upgrade-2026-11.sql`.
- **Chuyển sang PostGIS**: tìm ca SOS/tổ chức theo bán kính bằng `ST_DWithin` trên cột `geog` (kiểu `geography`, chỉ mục GiST)
  thay cho "hộp bao + Haversine" trong Java. API và entity không đổi. CSDL đã có: chạy lại `docs/legacy/upgrade-2026-11.sql` (bước 7).
- **Xác thực email & quên mật khẩu bằng OTP**: email không còn gửi link, mà gửi mã 6 số (hiệu lực 10 phút, tối đa 5 lần sai,
  gửi lại cách nhau 60 giây). `AuthToken.tokenHash` không còn `unique` (OTP có thể trùng ngẫu nhiên giữa hai người).
- **Kiểm tra tên miền email khi đăng ký** (`EmailDomainValidator`, tra DNS MX/A), chỉ bật khi `MAIL_ENABLED=true`.
- **Đăng ký lại sau 24 giờ**: email/SĐT trùng một tài khoản chưa xác thực quá 24 giờ được tự giải phóng (xóa hẳn, vì
  tài khoản đó chưa từng đăng nhập nên không có dữ liệu nào khác phụ thuộc) — trừ hồ sơ tổ chức Admin đã duyệt/từ chối.
- **SOS không nhận ảnh đính kèm nữa**; mỗi người chỉ được có một ca đang hoạt động tại một thời điểm.
- **Thanh toán giả lập** (`/api/payments/callback`) giờ bắt buộc đăng nhập, kiểm tra đúng chủ giao dịch, bắt buộc có số tiền,
  và in cảnh báo khi khởi động nếu `PAYMENT_MOCK=true`.
- **Tổ chức bị khóa/chuyển Không hoạt động**: ca "Đã phân công" (chưa xác nhận) tự trả về Chờ xử lý để Admin điều phối lại.
- Sửa nhỏ: thu hồi quyền GPS xóa tọa độ cũ; đổi mật khẩu thu hồi các phiên đăng nhập khác; hủy SOS đóng bản ghi phân công
  đang chờ; từ chối ca phân công bắt buộc có lý do (kiểm tra ở backend); xem phản hồi bình luận kiểm tra bài gốc còn hiển thị
  không; gửi lại hồ sơ tổ chức áp dụng lại quy tắc số lượng tài liệu; số tiền/thời gian trong thông báo được định dạng
  (`common/util/Format`); bấm thích liên tiếp không còn gây lỗi.

## Điểm cần lưu ý / giả định
1. **Kiểm thử**: `mvn test` chạy 254 test đơn vị (service bằng Mockito + `ArchitectureTest`); `mvn -Pit verify` chạy thêm 66 test tích hợp
   trên PostgreSQL + PostGIS thật (mục Kiểm thử ở trên), gồm cả các truy vấn SQL viết tay (thống kê theo ngày, PostGIS, tìm kiếm `unaccent`)
   và số câu SQL mỗi API (`QueryCountIT`).
2. **Thanh toán VNPay**: `PAYMENT_MOCK=true` (mặc định trong `application.yml`) là chế độ giả lập: không có trang thanh toán, giao diện mở
   hộp thoại giả lập rồi gọi `POST /api/payments/callback`. `PAYMENT_MOCK=false` + `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` (xem `.env.example`) dùng VNPay sandbox thật
   (`VnPayGateway`: URL ký HMAC-SHA512, xác minh chữ ký ở IPN và trang trả về rồi mới gọi `DonationService.processResult()`).
3. **Lọc theo bán kính dùng PostGIS** (cảnh báo SOS trùng trong 500 m, ca lân cận khi Admin xác minh, ca đề xuất và tổ chức được báo
   trong 50 km): `ST_DWithin` trên cột `geog` mà PostgreSQL tự tính từ `latitude/longitude` (tạo trong `db/migration/V1__baseline_schema.sql`), nên entity
   và API vẫn dùng hai cột số như cũ. Khoảng cách giữa hai điểm đã biết (điểm Matching, số km trong thông báo/danh sách ca)
   vẫn tính bằng Haversine trong `common/util/Geo`, vì gọi xuống CSDL không nhanh hơn.
4. Tài liệu có chỗ chưa thống nhất, mình chọn: tổ chức `claim` → thẳng `ACCEPTED` (khớp *Mô tả tóm tắt*; bản đặc tả có chỗ ghi "Đã phân công");
   thời gian khóa nhập theo **tháng** (đặc tả) nhưng lưu `duration_days` (Db); khóa tổ chức kết thúc cả chiến dịch `UPCOMING` (đặc tả chỉ nói `ACTIVE`).
5. Thêm so với Db (không phá cấu trúc): `incidents.accepted_at/completed_at/danger_rank/*_timeout_notified`,
   `incident_ai_assessments.reasons`, `notifications.reference_type/group_key/group_count`, `fundraising_campaigns.org_info/ended_at`,
   `donations.gateway_ref/completed_at`, `rescue_organizations.locked_at`.
