-- V10 (2026-11-18): mỗi tổ chức tự khai báo năng lực tiếp nhận, thay cho tham số chung app.ai.org-default-capacity
-- và app.ai.org-service-radius-km. AI Matching dùng hai giá trị này:
--   max_concurrent_cases: số ca xử lý cùng lúc -> điểm tải W = 1 - (số ca đang xử lý / số này); đủ số này là quá tải
--   service_radius_km   : bán kính hoạt động -> ngoài bán kính thì điểm vị trí L = 0, quá 1,5 lần bán kính thì bị loại
-- Tổ chức đã có trước V10 nhận đúng giá trị chung cũ (3 ca, 35 km) nên kết quả matching không đổi cho tới khi họ cập nhật.
ALTER TABLE rescue_organizations ADD COLUMN max_concurrent_cases integer;
ALTER TABLE rescue_organizations ADD COLUMN service_radius_km double precision;
UPDATE rescue_organizations SET max_concurrent_cases = 3 WHERE max_concurrent_cases IS NULL;
UPDATE rescue_organizations SET service_radius_km = 35 WHERE service_radius_km IS NULL;
ALTER TABLE rescue_organizations ALTER COLUMN max_concurrent_cases SET NOT NULL;
ALTER TABLE rescue_organizations ALTER COLUMN service_radius_km SET NOT NULL;
ALTER TABLE rescue_organizations ADD CONSTRAINT rescue_organizations_max_concurrent_cases_check
    CHECK (max_concurrent_cases BETWEEN 1 AND 50);
ALTER TABLE rescue_organizations ADD CONSTRAINT rescue_organizations_service_radius_km_check
    CHECK (service_radius_km >= 1 AND service_radius_km <= 100);
