-- V7 (2026-11-13)
-- 1. Ghi chú xác minh: ca AI xếp nhãn KHONG, Admin liên hệ người gửi rồi chọn lại mức khác KHONG thì bắt buộc ghi lại thông tin
--    đã hỏi được (số người, mức nước, nhu cầu...) để tổ chức nắm tình hình. Mô tả gốc của người dân giữ nguyên, ghi chú là cột
--    riêng; người ghi và thời điểm dùng chung verified_by_id / verified_at đã có.
ALTER TABLE incidents ADD COLUMN verification_note varchar(1000);

-- 2. Điểm năng lực C (0..100) trong AI Matching: mức khớp giữa nhu cầu của ca và năng lực (hỗ trợ / không hỗ trợ) suy ra từ mô tả
--    tổ chức. Dòng điểm cũ để NULL: được chấm theo công thức cũ, chưa có C (không gán số giả). Cột được dùng từ đợt cập nhật
--    công thức Matching.
ALTER TABLE incident_matching_scores ADD COLUMN capability_score double precision;
