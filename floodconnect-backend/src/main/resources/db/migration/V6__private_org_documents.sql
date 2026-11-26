-- V6 (2026-11-12): giấy tờ pháp lý / CCCD của tổ chức chuyển sang lưu RIÊNG TƯ.
-- Trước đây cột file_url chứa đường dẫn công khai (Cloudinary kiểu "upload" hoặc /files/org-docs/... của backend): ai có link là
-- xem được ảnh CCCD, không hết hạn. Nay tệp mới lưu ở chế độ riêng tư (Cloudinary kiểu "authenticated" hoặc thư mục private-uploads
-- không được phục vụ qua web), cột lưu MÃ TỆP NỘI BỘ thay vì đường dẫn, và chỉ tải được qua GET /api/org-docs/{id}/file
-- (chỉ Admin và chính tổ chức đó). Định dạng mã: "local:<thư mục>/<tên tệp>" hoặc "cloudinary:<image|raw>:<định dạng>:<public_id>".
-- Dòng cũ còn đường dẫn http(s) (dữ liệu trước khi chuyển, ảnh mẫu) vẫn đọc được; tệp cũ của chính hệ thống được chuyển bằng
-- tác vụ chạy một lần (app.storage.migrate-org-docs=true, xem OrgDocumentMigration).
ALTER TABLE organization_documents RENAME COLUMN file_url TO storage_key;
