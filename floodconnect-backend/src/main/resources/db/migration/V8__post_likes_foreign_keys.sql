-- V8 (2026-11-15): bảng post_likes chưa có khóa ngoại tới posts và users (entity PostLike lưu thẳng hai mã số,
-- giống follows trước V4). CSDL không chặn được lượt thích trỏ tới bài viết / tài khoản không tồn tại, và tài khoản
-- đăng ký bỏ dở bị xóa sau 24 giờ (AuthService.releaseIfStale) sẽ để lại lượt thích "mồ côi".
-- Bài viết chỉ bị xóa mềm (status = DELETED) nên CASCADE phía posts chỉ có tác dụng nếu sau này xóa hẳn bài.

-- 1) Dọn dòng mồ côi đã có (nếu có), nếu không thì không thêm được khóa ngoại
DELETE FROM post_likes pl
WHERE NOT EXISTS (SELECT 1 FROM posts p WHERE p.id = pl.post_id)
   OR NOT EXISTS (SELECT 1 FROM users u WHERE u.id = pl.user_id);

-- 2) Khóa ngoại: xóa bài viết / tài khoản thì xóa luôn các lượt thích gắn với nó
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_post FOREIGN KEY (post_id) REFERENCES posts (id) ON DELETE CASCADE;
ALTER TABLE post_likes ADD CONSTRAINT fk_post_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE;

-- 3) Khóa chính (post_id, user_id) đã phục vụ tra theo post_id; thêm chỉ mục user_id để CASCADE khi xóa tài khoản
--    không phải quét cả bảng
CREATE INDEX IF NOT EXISTS idx_post_likes_user ON post_likes (user_id);
