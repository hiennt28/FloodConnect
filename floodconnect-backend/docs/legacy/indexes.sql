-- Index khuyến nghị (chạy sau khi Hibernate tạo bảng lần đầu)
CREATE INDEX IF NOT EXISTS idx_incidents_status_created ON incidents (status, created_at);
CREATE INDEX IF NOT EXISTS idx_posts_status_created     ON posts (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_comments_post            ON comments (post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_notifications_user       ON notifications (user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_donations_campaign       ON donations (campaign_id, status);
CREATE INDEX IF NOT EXISTS idx_campaigns_status_start   ON fundraising_campaigns (status, start_date);
CREATE INDEX IF NOT EXISTS idx_auth_tokens_hash         ON auth_tokens (token_hash);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash      ON refresh_tokens (token_hash);

-- Mỗi người dân chỉ có một ca SOS đang hoạt động, kể cả khi gửi hai yêu cầu cùng lúc (SosService dựa vào tên chỉ mục này để báo lỗi)
CREATE UNIQUE INDEX IF NOT EXISTS uq_incidents_one_active_per_citizen ON incidents (citizen_id) WHERE status IN ('PENDING', 'ASSIGNED', 'ACCEPTED');

-- PostGIS: tìm ca SOS/tổ chức theo bán kính bằng ST_DWithin (IncidentRepository.findWithinRadiusNative,
-- RescueOrganizationRepository.findAvailableWithin). Cần cài PostGIS cho PostgreSQL trước (Windows: Stack Builder → Spatial Extensions).
-- Cột geog do PostgreSQL tự tính từ latitude/longitude mỗi khi thêm/sửa dòng (GENERATED ... STORED), code Java không ghi vào cột này.
-- ST_MakePoint nhận kinh độ trước, vĩ độ sau; 4326 là hệ tọa độ GPS (WGS84); kiểu geography tính khoảng cách bằng mét.
CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
ALTER TABLE rescue_organizations ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
CREATE INDEX IF NOT EXISTS idx_incidents_geog            ON incidents USING GIST (geog);
CREATE INDEX IF NOT EXISTS idx_rescue_organizations_geog ON rescue_organizations USING GIST (geog);
