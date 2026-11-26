-- V4 (2026-11-12): bảng follows chưa có khóa ngoại tới users (entity Follow lưu thẳng hai mã người dùng).
-- Tài khoản đăng ký bỏ dở bị xóa sau 24 giờ (AuthService.releaseIfStale) thì các dòng theo dõi liên quan thành "mồ côi":
-- số người theo dõi bị đếm cả người không còn tồn tại. Thêm khóa ngoại ON DELETE CASCADE để CSDL tự xóa các dòng đó.

-- 1) Dọn dòng mồ côi đã có (nếu có), nếu không thì không thêm được khóa ngoại
DELETE FROM follows f
WHERE NOT EXISTS (SELECT 1 FROM users u WHERE u.id = f.follower_id)
   OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = f.following_id);

-- 2) Khóa ngoại: xóa tài khoản thì xóa luôn các lượt theo dõi của tài khoản đó và lượt người khác theo dõi tài khoản đó
ALTER TABLE follows ADD CONSTRAINT fk_follows_follower  FOREIGN KEY (follower_id)  REFERENCES users (id) ON DELETE CASCADE;
ALTER TABLE follows ADD CONSTRAINT fk_follows_following FOREIGN KEY (following_id) REFERENCES users (id) ON DELETE CASCADE;

-- 3) Đếm "người đang theo dõi tôi" tra theo following_id; khóa chính (follower_id, following_id) chỉ phục vụ chiều còn lại
CREATE INDEX IF NOT EXISTS idx_follows_following ON follows (following_id);
