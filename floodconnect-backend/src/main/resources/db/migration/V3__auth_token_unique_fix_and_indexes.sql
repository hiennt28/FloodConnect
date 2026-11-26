-- V3 (2026-11-12): sửa một ràng buộc sót và thêm các chỉ mục tăng tốc chưa từng được tạo.

-- 1) Mã OTP 6 số KHÔNG được unique toàn cục: hai người (hoặc một người ở hai lần khác nhau) có thể nhận trùng mã ngẫu nhiên.
--    Ràng buộc này còn sót từ khi auth_tokens dùng token dạng liên kết (ddl-auto=update không bao giờ xóa ràng buộc cũ).
--    Mã cũ không bị xóa nên càng gửi nhiều mã, khả năng mã mới trùng một mã đã lưu càng cao -> lưu lỗi -> đăng ký / gửi lại mã báo lỗi.
--    Entity AuthToken đã ghi rõ "không còn unique toàn cục". Tên ràng buộc do Hibernate sinh, giống nhau ở mọi CSDL.
ALTER TABLE auth_tokens DROP CONSTRAINT IF EXISTS uk3wqaabxbp24ko89s7lkjxch2v;

-- 2) Chỉ mục cho các truy vấn thường dùng (trước đây nằm trong docs/indexes.sql nhưng CSDL đang chạy chưa từng có).
--    Đối chiếu với truy vấn thật trong code:
CREATE INDEX IF NOT EXISTS idx_incidents_status_created  ON incidents (status, created_at);              -- danh sách ca, tác vụ định kỳ
CREATE INDEX IF NOT EXISTS idx_incidents_assigned_org    ON incidents (assigned_org_id);                 -- ca của tổ chức, tải của tổ chức (AI Matching)
CREATE INDEX IF NOT EXISTS idx_posts_status_created      ON posts (status, created_at DESC);             -- bảng tin
CREATE INDEX IF NOT EXISTS idx_comments_post             ON comments (post_id, created_at);              -- bình luận của bài
CREATE INDEX IF NOT EXISTS idx_notifications_user        ON notifications (user_id, created_at DESC);    -- thông báo của tôi, đếm chưa đọc
CREATE INDEX IF NOT EXISTS idx_donations_campaign        ON donations (campaign_id, status);             -- sao kê chiến dịch
CREATE INDEX IF NOT EXISTS idx_campaigns_status_start    ON fundraising_campaigns (status, start_date);  -- danh sách chiến dịch
-- OTP được tra theo (người dùng, loại, mới nhất), không tra theo mã băm (thay cho idx_auth_tokens_hash cũ).
CREATE INDEX IF NOT EXISTS idx_auth_tokens_user_type     ON auth_tokens (user_id, type, created_at DESC);
-- refresh_tokens.token_hash đã có chỉ mục của ràng buộc UNIQUE nên không tạo thêm (idx_refresh_tokens_hash cũ là thừa).
