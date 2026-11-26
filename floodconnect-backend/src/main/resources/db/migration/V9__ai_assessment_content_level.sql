-- V9 (2026-11-17): tích hợp dịch vụ AI chung floodconnect-ai (đánh giá mức + matching)
-- 1. Mức chỉ theo nội dung của mỗi lần đánh giá (trước khi xét thời tiết và cụm ca lân cận). Khi chấm ca mới, backend gửi kèm
--    các ca lân cận cho AI cùng mức theo nội dung của chúng: cụm chỉ dựa trên mức này, không dựa trên mức cuối đã được nâng, để
--    hai ca gần nhau không nâng mức qua lại cho nhau mà không có thêm bằng chứng. Dòng cũ để NULL (AI dùng mức hiện có của ca).
ALTER TABLE incident_ai_assessments ADD COLUMN content_level varchar(20);
ALTER TABLE incident_ai_assessments ADD CONSTRAINT incident_ai_assessments_content_level_check
    CHECK (content_level IS NULL OR content_level IN ('KHONG', 'THAP', 'TRUNG_BINH', 'CAO', 'KHAN_CAP'));

-- 2. Nguồn kết quả: AI_SERVICE (dịch vụ AI) hoặc RULES (bộ quy tắc dự phòng trong Java khi AI tắt / lỗi / chưa nạp model).
ALTER TABLE incident_ai_assessments ADD COLUMN source varchar(20);
ALTER TABLE incident_ai_assessments ADD CONSTRAINT incident_ai_assessments_source_check
    CHECK (source IS NULL OR source IN ('AI_SERVICE', 'RULES'));
