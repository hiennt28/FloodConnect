-- CSDL riêng cho kiểm thử tích hợp (mvn -Pit verify). Chạy MỘT LẦN bằng tài khoản quản trị PostgreSQL:
--   psql -U postgres -f docs/create-test-database.sql
-- KHÔNG tự cài PostGIS hay tạo bảng gì trong CSDL này: Flyway tự dựng toàn bộ (V1, V2, V3...) khi bài test đầu tiên chạy.
-- Mỗi bài test xóa sạch dữ liệu các bảng (trừ địa giới và lịch sử Flyway) trước khi chạy; mã test chỉ chịu xóa khi tên
-- CSDL kết thúc bằng "_test", nên không bao giờ đụng tới CSDL floodconnect thật.
CREATE DATABASE floodconnect_test WITH ENCODING 'UTF8';
