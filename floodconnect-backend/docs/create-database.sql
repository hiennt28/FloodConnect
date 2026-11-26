-- Chạy bằng tài khoản quản trị PostgreSQL (ví dụ: psql -U postgres -f docs/create-database.sql)
CREATE DATABASE floodconnect WITH ENCODING 'UTF8';
-- (Tùy chọn) tạo user riêng thay vì dùng postgres:
-- CREATE USER floodconnect WITH PASSWORD 'doi_mat_khau';
-- GRANT ALL PRIVILEGES ON DATABASE floodconnect TO floodconnect;
-- \c floodconnect
-- GRANT ALL ON SCHEMA public TO floodconnect;
