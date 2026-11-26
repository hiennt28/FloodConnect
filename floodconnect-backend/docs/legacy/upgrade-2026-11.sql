-- Nâng cấp CSDL ĐÃ CÓ cho các đợt sửa lỗi 11/2026 (đợt 2, 3, đợt đóng ca KHONG, chuyển sang PostGIS, nhắc ca cứu hộ kéo dài, hồ sơ tổ chức theo từng ô và tên địa điểm bài viết). Chạy sau khi cập nhật code:
--   psql -U postgres -d floodconnect -f docs/upgrade-2026-11.sql
-- Mọi bước đều chạy lại được an toàn (đã chạy bản cũ của file này thì chạy lại để có thêm các bước mới, đến bước 19).
-- CSDL tạo mới từ bản này không cần chạy file này, chỉ cần chạy docs/indexes.sql như README hướng dẫn.
SET client_encoding = 'UTF8';

-- 1) AssignmentStatus có thêm giá trị CANCELLED. Hibernate 6 tạo ràng buộc CHECK cho cột enum khi tạo bảng,
--    còn ddl-auto=update không cập nhật ràng buộc này, nên phải tạo lại (nếu thiếu bước này, hủy ca đang phân công sẽ lỗi).
ALTER TABLE incident_assignments DROP CONSTRAINT IF EXISTS incident_assignments_status_check;
ALTER TABLE incident_assignments ADD CONSTRAINT incident_assignments_status_check
    CHECK (status IN ('WAITING_CONFIRM', 'ACCEPTED', 'REJECTED', 'TIMEOUT', 'CANCELLED'));

-- 2) Sửa dữ liệu cũ: các lần phân công trước đây bị ghi REJECTED do người dân hủy ca hoặc tổ chức bị khóa/chuyển
--    Không hoạt động, không phải tổ chức từ chối. Chuyển sang CANCELLED để AI Matching không còn trừ điểm tổ chức.
UPDATE incident_assignments SET status = 'CANCELLED'
WHERE status = 'REJECTED'
  AND reject_reason IN ('Người dân đã hủy ca', 'Tổ chức bị khóa', 'Tổ chức chuyển sang Không hoạt động');

-- 3) Người dân đang có từ 2 ca hoạt động trở lên. Câu này PHẢI trả về 0 dòng; nếu có dòng nào,
--    hãy hủy bớt ca thừa của người đó rồi chạy lại file này, vì bước 4 sẽ báo lỗi.
SELECT citizen_id, count(*) AS so_ca_dang_hoat_dong
FROM incidents
WHERE status IN ('PENDING', 'ASSIGNED', 'ACCEPTED')
GROUP BY citizen_id
HAVING count(*) > 1;

-- 4) Mỗi người dân chỉ có một ca SOS đang hoạt động (giống dòng cuối của docs/indexes.sql)
CREATE UNIQUE INDEX IF NOT EXISTS uq_incidents_one_active_per_citizen ON incidents (citizen_id) WHERE status IN ('PENDING', 'ASSIGNED', 'ACCEPTED');

-- 5) Đợt 3: cột version cho khóa lạc quan của chiến dịch. Hibernate (ddl-auto=update) cũng tự thêm khi khởi động;
--    câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE fundraising_campaigns ADD COLUMN IF NOT EXISTS version bigint NOT NULL DEFAULT 0;

-- 6) Đợt đóng ca KHONG: ca SOS có thêm trạng thái INVALID (Admin kết luận không phải yêu cầu cứu hộ). Giống bước 1, phải tạo lại
--    ràng buộc CHECK của cột status (nếu thiếu bước này, đánh dấu ca không hợp lệ sẽ lỗi). Các cột lý do/người/thời điểm
--    kết luận Hibernate (ddl-auto=update) cũng tự thêm khi khởi động; các câu ADD COLUMN cần khi chạy với DDL_AUTO=validate.
ALTER TABLE incidents DROP CONSTRAINT IF EXISTS incidents_status_check;
ALTER TABLE incidents ADD CONSTRAINT incidents_status_check
    CHECK (status IN ('PENDING', 'ASSIGNED', 'ACCEPTED', 'COMPLETED', 'CANCELLED', 'INVALID'));
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS invalid_reason varchar(500);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS invalidated_at timestamp(6) with time zone;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS invalidated_by_id bigint REFERENCES users (id);

-- 7) Chuyển sang PostGIS: tìm ca SOS/tổ chức theo bán kính bằng ST_DWithin thay cho "hộp bao + Haversine" trong Java.
--    Giống khối PostGIS cuối docs/indexes.sql (xem giải thích ở đó). Cần cài PostGIS trước (Windows: Stack Builder → Spatial Extensions);
--    chưa cài thì câu CREATE EXTENSION báo lỗi "could not open extension control file".
CREATE EXTENSION IF NOT EXISTS postgis;
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
ALTER TABLE rescue_organizations ADD COLUMN IF NOT EXISTS geog geography(Point, 4326)
    GENERATED ALWAYS AS (ST_SetSRID(ST_MakePoint(longitude, latitude), 4326)::geography) STORED;
CREATE INDEX IF NOT EXISTS idx_incidents_geog            ON incidents USING GIST (geog);
CREATE INDEX IF NOT EXISTS idx_rescue_organizations_geog ON rescue_organizations USING GIST (geog);
-- Chỉ mục (latitude, longitude) cũ phục vụ cách "hộp bao", không còn truy vấn nào dùng
DROP INDEX IF EXISTS idx_incidents_geo;

-- 8) Nhắc ca "Đã tiếp nhận" quá lâu chưa hoàn thành: cờ "đã nhắc" của ca. Hibernate (ddl-auto=update) cũng tự thêm khi
--    khởi động; câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS accepted_timeout_notified boolean NOT NULL DEFAULT false;

-- 9) Hồ sơ tổ chức theo từng ô (giấy tờ chính, CCCD mặt trước, CCCD mặt sau, minh chứng hoạt động) và trang web/fanpage
--    của tổ chức. Hibernate (ddl-auto=update) cũng tự thêm khi khởi động; các câu này cần khi chạy với DDL_AUTO=validate.
--    Tệp nộp trước bước này giữ slot rỗng: hệ thống coi là giấy tờ chính, riêng nhóm thiện nguyện hiển thị là hồ sơ cũ.
ALTER TABLE organization_documents ADD COLUMN IF NOT EXISTS slot varchar(20);
ALTER TABLE rescue_organizations ADD COLUMN IF NOT EXISTS website_url varchar(500);

-- 10) Tên địa điểm của bài viết (trước đây giao diện cho nhập nhưng không lưu). Hibernate (ddl-auto=update) cũng tự thêm
--     khi khởi động; câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS location_name varchar(200);
-- 10b) Vị trí bài viết nhập bằng địa chỉ (Tỉnh, Phường/Xã + thôn/xóm, số nhà) thay cho vĩ độ, kinh độ: lưu mã xã và phần
--      chi tiết để sửa bài điền lại được; location_name do server ghép. Bài cũ chỉ có tọa độ thì hai cột này để trống.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS ward_code varchar(5);
ALTER TABLE posts ADD COLUMN IF NOT EXISTS address_detail varchar(100);

-- 11) Bỏ trường "loại sự cố" (incident_type) của ca SOS: không còn dùng ở backend lẫn giao diện. ddl-auto=update không tự
--     xóa cột, nên cột cũ vẫn nằm lại trong bảng mà không ảnh hưởng gì; chạy câu này để dọn nếu muốn.
ALTER TABLE incidents DROP COLUMN IF EXISTS incident_type;

-- 12) Admin xác minh ca "nên xác minh" (nút "Đã xác minh, giữ mức", hoặc chọn mức mới sau khi liên hệ): lưu Admin xác minh
--     và thời điểm. Hibernate (ddl-auto=update) cũng tự thêm khi khởi động; các câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verified_by_id bigint REFERENCES users (id);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS verified_at timestamp(6) with time zone;

-- 13) Giao dịch ủng hộ có thêm trạng thái EXPIRED (người ủng hộ bỏ dở, quá app.payment.pending-expire-minutes chưa có kết quả).
--     Giống bước 1 và 6: ddl-auto=update không sửa ràng buộc CHECK đã có, phải tạo lại; thiếu bước này tác vụ hết hạn sẽ lỗi
--     (log cảnh báo mỗi phút) và giao dịch bỏ dở vẫn nằm ở PENDING.
ALTER TABLE donations DROP CONSTRAINT IF EXISTS donations_status_check;
ALTER TABLE donations ADD CONSTRAINT donations_status_check
    CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED', 'EXPIRED'));

-- 14) Chỉ nhận ủng hộ qua VNPay; chiến dịch khai "ngân hàng nhận tiền" (tên ngân hàng + số tài khoản + chủ tài khoản) thay cho
--     "phương thức nhận" VNPAY/MoMo cũ. Cột payment_method của chiến dịch bắt buộc (NOT NULL) mà entity không còn trường này,
--     nên PHẢI xóa, nếu không tạo chiến dịch mới sẽ lỗi. Chiến dịch cũ có bank_name rỗng: tổ chức bổ sung khi sửa chiến dịch.
--     Bảng donations giữ nguyên payment_method (các khoản ủng hộ cũ qua MoMo vẫn hiển thị đúng trong lịch sử, sao kê).
ALTER TABLE fundraising_campaigns ADD COLUMN IF NOT EXISTS bank_name varchar(100);
ALTER TABLE fundraising_campaigns DROP COLUMN IF EXISTS payment_method;

-- 15) Ca SOS Đã tiếp nhận có thêm 2 cách hoàn thành ngoài tổ chức bấm Hoàn thành: người dân báo "Tôi đã an toàn" và Admin
--     xác nhận hoàn thành sau khi liên hệ. Lưu ai hoàn thành (ORG/CITIZEN/ADMIN; ca cũ để trống = tổ chức), tài khoản đã bấm
--     và ghi chú/lý do. Hibernate (ddl-auto=update) cũng tự thêm khi khởi động; các câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS completed_by_role varchar(20)
    CHECK (completed_by_role IN ('ORG', 'CITIZEN', 'ADMIN'));
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS completed_by_id bigint REFERENCES users (id);
ALTER TABLE incidents ADD COLUMN IF NOT EXISTS completion_note varchar(500);

-- 16) Thông báo mở đúng chỗ: thêm mã đối tượng con (vd. bình luận/phản hồi trong bài viết) để bấm thông báo cuộn tới đúng
--     bình luận. Hibernate (ddl-auto=update) cũng tự thêm khi khởi động; câu này cần khi chạy với DDL_AUTO=validate.
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS sub_reference_id bigint;

-- 17) Loại thông báo mới REPORT_NEW (Admin nhận khi bài viết / chiến dịch bị báo cáo). Giống bước 1, 6 và 13: ddl-auto=update
--     không sửa ràng buộc CHECK đã có của cột type, phải tạo lại. THIẾU BƯỚC NÀY thì người dùng báo cáo bài viết / chiến dịch
--     sẽ bị lỗi (lưu thông báo vi phạm ràng buộc làm hỏng cả thao tác báo cáo).
ALTER TABLE notifications DROP CONSTRAINT IF EXISTS notifications_type_check;
ALTER TABLE notifications ADD CONSTRAINT notifications_type_check
    CHECK (type IN ('SYSTEM', 'COMMENT', 'REPLY', 'LIKE', 'FOLLOW', 'POST_REMOVED', 'REPORT_RESULT', 'ORG_REVIEW',
                    'SOS_NEW', 'SOS_STATUS', 'SOS_TIMEOUT', 'SOS_INVALID', 'ACCOUNT', 'CAMPAIGN', 'DONATION', 'REPORT_NEW'));

-- 18) Ca SOS chỉ gồm chữ và vị trí, không kèm ảnh: bỏ hẳn bảng ảnh của ca SOS (code không còn dùng). ddl-auto=update không tự
--     xóa bảng; bảng cũ nằm lại cũng không ảnh hưởng gì, chạy câu này để dọn. Câu này XÓA mọi ảnh SOS cũ (nếu có).
DROP TABLE IF EXISTS incident_media;

-- 19) Cập nhật tên đơn vị hành chính theo dữ liệu địa giới mới (repo thanglequoc/vietnamese-provinces-database, commit 8b78ba5):
--     Đồng Nai, Quảng Ninh, Bắc Ninh thành thành phố trực thuộc trung ương; 22 xã thành phường; 32 tên xã trước đây lưu
--     dạng Unicode tách dấu (NFD), nay chuẩn hóa NFC (nhìn giống hệt nhưng so chuỗi không khớp).
--     Tổ chức lưu tỉnh/xã bằng TÊN, nên phải đổi tên cũ sang tên mới, nếu không form sửa hồ sơ không nhận ra địa chỉ cũ.
--     PHẢI chạy docs/admin-geo.sql và docs/admin-geo-data.sql TRƯỚC bước này (câu cuối của 19b dùng bảng admin_wards).
--     Chạy lại được an toàn: lần hai không còn dòng nào khớp tên cũ.
-- 19a) Đổi tên (sinh tự động bởi scripts/geo/build-admin-geo.mjs: xã trước, theo tên tỉnh cũ; rồi mới đổi tên tỉnh)
UPDATE rescue_organizations SET ward = 'Xã Nậm Nèn' WHERE province = 'Điện Biên' AND ward = 'Xã Nậm Nèn';
UPDATE rescue_organizations SET ward = 'Xã Mường Lai' WHERE province = 'Lào Cai' AND ward = 'Xã Mường Lai';
UPDATE rescue_organizations SET ward = 'Xã Phúc Lợi' WHERE province = 'Lào Cai' AND ward = 'Xã Phúc Lợi';
UPDATE rescue_organizations SET ward = 'Xã Châu Quế' WHERE province = 'Lào Cai' AND ward = 'Xã Châu Quế';
UPDATE rescue_organizations SET ward = 'Xã Tân Hợp' WHERE province = 'Lào Cai' AND ward = 'Xã Tân Hợp';
UPDATE rescue_organizations SET ward = 'Xã Xuân Ái' WHERE province = 'Lào Cai' AND ward = 'Xã Xuân Ái';
UPDATE rescue_organizations SET ward = 'Xã Mỏ Vàng' WHERE province = 'Lào Cai' AND ward = 'Xã Mỏ Vàng';
UPDATE rescue_organizations SET ward = 'Xã Lương Thịnh' WHERE province = 'Lào Cai' AND ward = 'Xã Lương Thịnh';
UPDATE rescue_organizations SET ward = 'Xã Tú Lệ' WHERE province = 'Lào Cai' AND ward = 'Xã Tú Lệ';
UPDATE rescue_organizations SET ward = 'Xã Chấn Thịnh' WHERE province = 'Lào Cai' AND ward = 'Xã Chấn Thịnh';
UPDATE rescue_organizations SET ward = 'Xã Thượng Bằng La' WHERE province = 'Lào Cai' AND ward = 'Xã Thượng Bằng La';
UPDATE rescue_organizations SET ward = 'Phường Bố Hạ' WHERE province = 'Bắc Ninh' AND ward = 'Xã Bố Hạ';
UPDATE rescue_organizations SET ward = 'Phường Lạng Giang' WHERE province = 'Bắc Ninh' AND ward = 'Xã Lạng Giang';
UPDATE rescue_organizations SET ward = 'Phường Kép' WHERE province = 'Bắc Ninh' AND ward = 'Xã Kép';
UPDATE rescue_organizations SET ward = 'Phường Lục Nam' WHERE province = 'Bắc Ninh' AND ward = 'Xã Lục Nam';
UPDATE rescue_organizations SET ward = 'Phường Hiệp Hòa' WHERE province = 'Bắc Ninh' AND ward = 'Xã Hiệp Hòa';
UPDATE rescue_organizations SET ward = 'Phường Yên Phong' WHERE province = 'Bắc Ninh' AND ward = 'Xã Yên Phong';
UPDATE rescue_organizations SET ward = 'Phường Phù Lãng' WHERE province = 'Bắc Ninh' AND ward = 'Xã Phù Lãng';
UPDATE rescue_organizations SET ward = 'Phường Chi Lăng' WHERE province = 'Bắc Ninh' AND ward = 'Xã Chi Lăng';
UPDATE rescue_organizations SET ward = 'Phường Tiên Du' WHERE province = 'Bắc Ninh' AND ward = 'Xã Tiên Du';
UPDATE rescue_organizations SET ward = 'Phường Gia Bình' WHERE province = 'Bắc Ninh' AND ward = 'Xã Gia Bình';
UPDATE rescue_organizations SET ward = 'Phường Nhân Thắng' WHERE province = 'Bắc Ninh' AND ward = 'Xã Nhân Thắng';
UPDATE rescue_organizations SET ward = 'Phường Lương Tài' WHERE province = 'Bắc Ninh' AND ward = 'Xã Lương Tài';
UPDATE rescue_organizations SET ward = 'Phường Lộc Ninh' WHERE province = 'Đồng Nai' AND ward = 'Xã Lộc Ninh';
UPDATE rescue_organizations SET ward = 'Phường Tân Khai' WHERE province = 'Đồng Nai' AND ward = 'Xã Tân Khai';
UPDATE rescue_organizations SET ward = 'Phường Đồng Phú' WHERE province = 'Đồng Nai' AND ward = 'Xã Đồng Phú';
UPDATE rescue_organizations SET ward = 'Phường Tân Phú' WHERE province = 'Đồng Nai' AND ward = 'Xã Tân Phú';
UPDATE rescue_organizations SET ward = 'Phường Trị An' WHERE province = 'Đồng Nai' AND ward = 'Xã Trị An';
UPDATE rescue_organizations SET ward = 'Phường Trảng Bom' WHERE province = 'Đồng Nai' AND ward = 'Xã Trảng Bom';
UPDATE rescue_organizations SET ward = 'Phường Dầu Giây' WHERE province = 'Đồng Nai' AND ward = 'Xã Dầu Giây';
UPDATE rescue_organizations SET ward = 'Phường Long Thành' WHERE province = 'Đồng Nai' AND ward = 'Xã Long Thành';
UPDATE rescue_organizations SET ward = 'Phường Xuân Lộc' WHERE province = 'Đồng Nai' AND ward = 'Xã Xuân Lộc';
UPDATE rescue_organizations SET ward = 'Phường Nhơn Trạch' WHERE province = 'Đồng Nai' AND ward = 'Xã Nhơn Trạch';
UPDATE rescue_organizations SET ward = 'Phường An Hội Tây' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường An Hội Tây';
UPDATE rescue_organizations SET ward = 'Phường Gò Vấp' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Gò Vấp';
UPDATE rescue_organizations SET ward = 'Phường Hạnh Thông' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Hạnh Thông';
UPDATE rescue_organizations SET ward = 'Phường Tân Sơn Nhất' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Sơn Nhất';
UPDATE rescue_organizations SET ward = 'Phường Tân Sơn Hòa' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Sơn Hòa';
UPDATE rescue_organizations SET ward = 'Phường Bảy Hiền' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Bảy Hiền';
UPDATE rescue_organizations SET ward = 'Phường Tân Hòa' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Hòa';
UPDATE rescue_organizations SET ward = 'Phường Tân Bình' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Bình';
UPDATE rescue_organizations SET ward = 'Phường Xóm Chiếu' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Xóm Chiếu';
UPDATE rescue_organizations SET ward = 'Phường Khánh Hội' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Khánh Hội';
UPDATE rescue_organizations SET ward = 'Phường Vĩnh Hội' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Vĩnh Hội';
UPDATE rescue_organizations SET ward = 'Phường Tân Thuận' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Thuận';
UPDATE rescue_organizations SET ward = 'Phường Tân Mỹ' WHERE province = 'Thành phố Hồ Chí Minh' AND ward = 'Phường Tân Mỹ';
UPDATE rescue_organizations SET ward = 'Xã An Bình' WHERE province = 'Vĩnh Long' AND ward = 'Xã An Bình';
UPDATE rescue_organizations SET ward = 'Xã Nhơn Phú' WHERE province = 'Vĩnh Long' AND ward = 'Xã Nhơn Phú';
UPDATE rescue_organizations SET ward = 'Xã Bình Phước' WHERE province = 'Vĩnh Long' AND ward = 'Xã Bình Phước';
UPDATE rescue_organizations SET ward = 'Xã Quới An' WHERE province = 'Vĩnh Long' AND ward = 'Xã Quới An';
UPDATE rescue_organizations SET ward = 'Xã Trung Ngãi' WHERE province = 'Vĩnh Long' AND ward = 'Xã Trung Ngãi';
UPDATE rescue_organizations SET ward = 'Xã Hiếu Thành' WHERE province = 'Vĩnh Long' AND ward = 'Xã Hiếu Thành';
UPDATE rescue_organizations SET ward = 'Xã Tân Lược' WHERE province = 'Vĩnh Long' AND ward = 'Xã Tân Lược';
UPDATE rescue_organizations SET ward = 'Xã Vĩnh Xuân' WHERE province = 'Vĩnh Long' AND ward = 'Xã Vĩnh Xuân';
UPDATE rescue_organizations SET province = 'Thành phố Quảng Ninh' WHERE province = 'Quảng Ninh';
UPDATE rescue_organizations SET service_areas = 'Thành phố Quảng Ninh' WHERE service_areas = 'Quảng Ninh';
UPDATE rescue_organizations SET province = 'Thành phố Bắc Ninh' WHERE province = 'Bắc Ninh';
UPDATE rescue_organizations SET service_areas = 'Thành phố Bắc Ninh' WHERE service_areas = 'Bắc Ninh';
UPDATE rescue_organizations SET province = 'Thành phố Đồng Nai' WHERE province = 'Đồng Nai';
UPDATE rescue_organizations SET service_areas = 'Thành phố Đồng Nai' WHERE service_areas = 'Đồng Nai';

-- 19b) Dữ liệu mẫu cũ ghi tỉnh "Hà Nội" và tên xã thiếu chữ "Phường"/"Xã" (VD "Thanh Trì"), không khớp danh sách
UPDATE rescue_organizations SET province = 'Thành phố Hà Nội' WHERE province = 'Hà Nội';
UPDATE rescue_organizations SET service_areas = 'Thành phố Hà Nội' WHERE service_areas = 'Hà Nội';
-- Thêm "Phường"/"Xã"/"Đặc khu" vào tên xã nếu trong tỉnh chỉ có đúng một xã khớp (tránh đoán sai khi có cả "Phường X" lẫn "Xã X")
UPDATE rescue_organizations o SET ward = w.name
FROM admin_wards w
WHERE w.province_name = o.province
  AND w.name IN ('Phường ' || o.ward, 'Xã ' || o.ward, 'Đặc khu ' || o.ward)
  AND (SELECT count(*) FROM admin_wards x WHERE x.province_name = o.province
         AND x.name IN ('Phường ' || o.ward, 'Xã ' || o.ward, 'Đặc khu ' || o.ward)) = 1;