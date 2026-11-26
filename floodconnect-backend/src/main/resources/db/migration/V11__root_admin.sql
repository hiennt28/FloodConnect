-- Admin gốc (REVIEW-004, đợt 3): chỉ MỘT tài khoản Admin được tạo, vô hiệu hóa và mở lại tài khoản Admin khác.
-- Admin gốc vẫn làm mọi việc của Admin thường (điều phối SOS, duyệt tổ chức, kiểm duyệt...).

ALTER TABLE users ADD COLUMN root_admin boolean NOT NULL DEFAULT false;

-- Chỉ tài khoản có vai trò ADMIN mới là Admin gốc được
ALTER TABLE users ADD CONSTRAINT users_root_admin_role_check CHECK (NOT root_admin OR role = 'ADMIN');

-- Admin đang có (tài khoản Admin tạo sớm nhất) trở thành Admin gốc. CSDL mới chưa có Admin thì không làm gì:
-- DataInitializer tạo Admin gốc lúc khởi động từ ADMIN_EMAIL / ADMIN_PHONE / ADMIN_PASSWORD.
UPDATE users SET root_admin = true
 WHERE id = (SELECT min(id) FROM users WHERE role = 'ADMIN');

-- Email của Admin gốc chuyển sang hộp thư của nhóm (mật khẩu giữ nguyên). Bỏ qua nếu email này đã có tài khoản khác dùng,
-- để migration không lỗi vì trùng email; khi đó Admin gốc giữ email cũ.
UPDATE users SET email = 'nhomdoan2004@gmail.com'
 WHERE root_admin
   AND NOT EXISTS (SELECT 1 FROM users WHERE lower(email) = 'nhomdoan2004@gmail.com');

-- Bảo đảm chỉ có đúng một Admin gốc
CREATE UNIQUE INDEX uq_users_one_root_admin ON users (root_admin) WHERE root_admin;
