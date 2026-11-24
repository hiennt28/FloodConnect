# FloodConnect – Hướng dẫn chạy nhanh

Tóm tắt các bước chạy. Chi tiết xem `floodconnect-backend/README.md` (cấu hình, API, quy tắc nghiệp vụ) và `README.md` (frontend).

## Chạy
1. Backend (trong thư mục `floodconnect-backend`):
   - Cài PostgreSQL + PostGIS, tạo database: `psql -U postgres -f docs/create-database.sql`.
   - Tạo `.env` từ `.env.example`, **bắt buộc** điền `JWT_SECRET` (chuỗi ngẫu nhiên ≥ 32 byte, cách tạo ghi trong file mẫu) và
     `ADMIN_PASSWORD` (mật khẩu Admin lần đầu). Thiếu một trong hai thì backend không khởi động.
   - `mvn spring-boot:run` (cổng 8080). **Không cần chạy tay file SQL nào**: Flyway tự tạo bảng, bật PostGIS, nạp dữ liệu địa giới
     phường/xã khi khởi động (lần đầu mất thêm vài giây).
   - CSDL tạo trước 12/11/2026: xem mục "CSDL tạo trước khi có Flyway" trong `floodconnect-backend/README.md` (chạy một lần với
     `FLYWAY_BASELINE=true`).
2. Dịch vụ AI (thư mục `floodconnect-ai`, tùy chọn nhưng nên chạy): `pip install -r requirements.txt` rồi
   `uvicorn app.main:app --port 8000` (Windows: nhấp đúp `chay_windows.bat`). Chép model `phobert-sos-final/` vào
   `floodconnect-ai/models/`. Trong `.env` của backend đặt `AI_SERVICE_URL=http://localhost:8000`. Không chạy AI (hoặc AI lỗi,
   chưa có model) thì backend tự dùng bộ quy tắc dự phòng trong Java, hệ thống vẫn hoạt động. Chi tiết: `floodconnect-ai/README.md`.
3. Frontend: `npm install` rồi `npm run dev` (cổng 5173). Địa chỉ backend nằm trong `.env` (`VITE_API_URL`).
4. Đăng nhập Admin: `admin@floodconnect.vn` với mật khẩu là `ADMIN_PASSWORD` trong `.env` của backend (bắt buộc đặt trước lần
   chạy đầu; sau đó đổi trong Trang cá nhân).

## Email xác thực / đặt lại mật khẩu
Dùng **mã OTP 6 số** (hiệu lực 10 phút). Backend mặc định không gửi mail thật (`MAIL_ENABLED=false`): nội dung email, kèm mã OTP,
được in ra console backend. Đặt `MAIL_ENABLED=true` và cấu hình SMTP để gửi thật.

## Thanh toán
`PAYMENT_MOCK=true` (mặc định): cổng thanh toán mô phỏng. `PAYMENT_MOCK=false` + `VNPAY_TMN_CODE`, `VNPAY_HASH_SECRET` trong `.env` của
backend: dùng VNPay sandbox thật.

## Lưu ý
- Chỉ tài khoản **người dân** gửi được SOS; tổ chức cứu hộ và Admin không có mục "Cứu hộ SOS" (họ tiếp nhận / điều phối ca).
- SOS chỉ gồm mô tả bằng chữ và vị trí (không đính kèm ảnh). Chỉ sửa được ca đang Chờ xử lý.
- Mỗi lần ủng hộ từ 5.000đ đến dưới 1 tỷ đồng (giới hạn thường gặp của VNPay, đổi được bằng `PAYMENT_MIN_AMOUNT` / `PAYMENT_MAX_AMOUNT`).
- Không chỗ nào bắt nhập vĩ độ, kinh độ: SOS và trụ sở tổ chức đặt ghim trên bản đồ (Phường/Xã tự cập nhật theo ghim);
  bài đăng nhập Tỉnh, Phường/Xã, thôn/xóm hoặc dùng GPS.
- Dữ liệu tự làm mới mỗi 30 giây khi tab đang mở; thông báo mới đến ngay qua WebSocket.
- Tổ chức đã duyệt tự sửa được mô tả, trang web, địa chỉ và ghim trụ sở (Hồ sơ → Thông tin tổ chức → "Sửa thông tin tổ chức"); tên, loại tổ chức và giấy tờ gắn với hồ sơ đã duyệt nên không tự đổi. Người ngoài chỉ thấy trụ sở tới Phường/Xã, Tỉnh. Giấy tờ pháp lý / CCCD lưu riêng tư: chỉ Admin và chính tổ chức mở được (bấm tên tệp, mở ở tab mới). Logo đổi/xóa được bất cứ lúc nào.
- Giao diện có chế độ Sáng / Tối / Theo máy (menu người dùng). Khách vào trang chủ thấy trang giới thiệu (`/welcome`).
