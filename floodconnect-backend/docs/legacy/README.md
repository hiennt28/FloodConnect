# Script CSDL cũ (trước khi dùng Flyway) — KHÔNG cần chạy nữa

Từ 2026-11-12, cấu trúc CSDL do **Flyway** quản lý: các file `src/main/resources/db/migration/V*.sql` tự chạy khi backend
khởi động. Các file trong thư mục này chỉ để tra cứu lịch sử:

| File | Trước đây dùng để | Nay nằm ở |
|---|---|---|
| `indexes.sql` | Tạo chỉ mục, bật PostGIS, cột `geog`, chỉ mục "mỗi người một ca" | `V1__baseline_schema.sql` (PostGIS, `geog`, chỉ mục duy nhất) + `V3__...` (chỉ mục tăng tốc) |
| `admin-geo.sql` | Tạo bảng địa giới `admin_wards` | `V1__baseline_schema.sql` |
| *(admin-geo-data.sql)* | Nạp 3.321 xã | `V2__admin_wards_data.sql` (file đã được chuyển, không còn ở `docs/`) |
| `upgrade-2026-11.sql` | 19 bước nâng cấp CSDL cũ từ 02/11 đến 10/11/2026 | Kết quả của cả 19 bước đã nằm trong `V1__baseline_schema.sql` |

**Chỉ một trường hợp còn dùng tới `upgrade-2026-11.sql`:** một CSDL tạo từ trước 12/11/2026 mà chưa chạy đủ 19 bước.
Khi đó chạy file này cho đủ, rồi khởi động backend **một lần** với `FLYWAY_BASELINE=true` (Flyway ghi nhận CSDL đã có V1, V2
và tự chạy V3 trở đi). Các lần sau bỏ biến đó đi.
