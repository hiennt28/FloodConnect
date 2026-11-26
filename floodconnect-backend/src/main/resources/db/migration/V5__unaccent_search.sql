-- V5 (2026-11-12): tìm kiếm tên người dân / tổ chức không phân biệt dấu và hoa/thường.
-- Trước đây gõ "yen" không ra "Đội cứu hộ phường Yên Hòa" vì câu truy vấn so khớp từng ký tự (có dấu khác không dấu).
-- unaccent bỏ dấu tiếng Việt (gồm cả Đ/đ -> D/d): so sánh unaccent(lower(tên)) với unaccent(lower(từ khóa)),
-- nên "yen", "yên", "Yen", "Yên", "YÊN" đều khớp "Yên". unaccent là extension "trusted" (PostgreSQL 13+), chủ CSDL tạo được.
CREATE EXTENSION IF NOT EXISTS unaccent;
