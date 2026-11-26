-- Bảng địa giới phường/xã (34 tỉnh, 3.321 xã sau sắp xếp 2025), dùng để biết một tọa độ thuộc xã nào (ST_Contains)
-- và để vẽ đường viền xã trên bản đồ form SOS. Bảng do file SQL tạo, không qua Hibernate (ddl-auto không đụng tới).
-- Chạy file này trước, rồi nạp dữ liệu bằng docs/admin-geo-data.sql. Chạy lại được an toàn.
SET client_encoding = 'UTF8';
CREATE EXTENSION IF NOT EXISTS postgis;
CREATE TABLE IF NOT EXISTS admin_wards (
    code          varchar(5)   PRIMARY KEY,           -- mã xã theo Tổng cục Thống kê, trùng mã trong vn-admin-2025.json
    province_code varchar(2)   NOT NULL,
    name          varchar(100) NOT NULL,              -- tên đầy đủ, VD "Phường Ba Đình"
    province_name varchar(100) NOT NULL,              -- cùng quy ước với frontend, VD "Thành phố Hà Nội", "Cao Bằng"
    area_km2      double precision,
    geom          geometry(MultiPolygon, 4326) NOT NULL, -- ranh giới đã đơn giản hóa; dùng geometry (không phải geography) vì chỉ cần kiểm tra điểm nằm trong
    center        geometry(Point, 4326)               -- điểm đại diện, luôn nằm trong xã (ST_PointOnSurface), để bản đồ nhảy tới
);
CREATE INDEX IF NOT EXISTS idx_admin_wards_geom     ON admin_wards USING GIST (geom);
CREATE INDEX IF NOT EXISTS idx_admin_wards_province ON admin_wards (province_code);
