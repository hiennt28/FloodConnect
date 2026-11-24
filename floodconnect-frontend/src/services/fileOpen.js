/* Mở tệp cần đăng nhập mới xem được (giấy tờ pháp lý / CCCD của tổ chức) ở tab mới.
   Link thường <a href> không gửi được token đăng nhập, nên: tải tệp bằng API (kèm token) rồi mở nội dung trong tab mới.
   Tab được mở NGAY lúc bấm (trình duyệt chặn cửa sổ mở sau một lần chờ mạng), nội dung điền vào sau khi tải xong. */
import { API_BASE } from '@/services/http';

/* Chỉ hiển thị trực tiếp các kiểu tệp hồ sơ được phép tải lên; kiểu khác ép thành tải về (không để trình duyệt chạy nội dung lạ) */
const VIEWABLE = ['application/pdf', 'image/png', 'image/jpeg', 'image/webp'];

/** loader: hàm trả Response (api(..., { raw: true })). Trả null nếu mở được, chuỗi lỗi nếu không. */
export async function openProtectedFile(loader) {
  const win = window.open('', '_blank');
  if (!win) return 'Trình duyệt đã chặn cửa sổ mới. Hãy cho phép cửa sổ bật lên cho trang này rồi thử lại.';
  try {
    win.document.title = 'Đang mở tệp…';
    const res = await loader();
    // Ảnh mẫu / dữ liệu cũ: backend chuyển hướng sang trang khác -> mở thẳng trang đó (khác nguồn, an toàn hơn mở bằng blob)
    if (res.redirected && new URL(res.url).origin !== new URL(API_BASE).origin) { win.location.href = res.url; return null; }
    const type = (res.headers.get('Content-Type') || '').split(';')[0].trim().toLowerCase();
    const blob = new Blob([await res.arrayBuffer()], { type: VIEWABLE.includes(type) ? type : 'application/octet-stream' });
    const url = URL.createObjectURL(blob);
    win.location.href = url;
    setTimeout(() => URL.revokeObjectURL(url), 60_000);
    return null;
  } catch (e) {
    win.close();
    return e?.message || 'Không mở được tệp, vui lòng thử lại.';
  }
}
