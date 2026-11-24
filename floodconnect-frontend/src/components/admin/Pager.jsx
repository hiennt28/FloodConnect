/* Phân trang Trước / Sau cho bảng của Admin (dữ liệu do máy chủ chia trang). page bắt đầu từ 0; ẩn khi chỉ có một trang.
   unit: đơn vị hiển thị ("ca", "bài", "báo cáo"...) */
export function Pager({ page, size, total, last, unit = 'mục', onChange }) {
  if (!total || total <= size) return null;
  const pages = Math.max(1, Math.ceil(total / size));
  return (
    <div className="row between wrap gap8">
      <span className="muted small">{total} {unit} • Trang {page + 1}/{pages}</span>
      <div className="row gap6">
        <button className="btn btn-ghost btn-sm" disabled={page === 0} onClick={() => onChange(page - 1)}>Trang trước</button>
        <button className="btn btn-ghost btn-sm" disabled={last || page + 1 >= pages} onClick={() => onChange(page + 1)}>Trang sau</button>
      </div>
    </div>
  );
}
