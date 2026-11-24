export const uid = (p = 'id') => `${p}_${Date.now().toString(36)}${Math.random().toString(36).slice(2, 6)}`;
export const money = (n) => `${Math.round(n || 0).toLocaleString('vi-VN')} đ`;

export function ago(t) {
  const s = Math.max(0, Math.floor((Date.now() - t) / 1000));
  if (s < 60) return 'Vừa xong';
  if (s < 3600) return `${Math.floor(s / 60)} phút trước`;
  if (s < 86400) return `${Math.floor(s / 3600)} giờ trước`;
  if (s < 30 * 86400) return `${Math.floor(s / 86400)} ngày trước`;
  return fmtDate(t);
}
export const fmtDate = (t) => new Date(t).toLocaleDateString('vi-VN');
export const fmtDateTime = (t) =>
  new Date(t).toLocaleString('vi-VN', { hour: '2-digit', minute: '2-digit', day: '2-digit', month: '2-digit', year: 'numeric' });
export const toInputDate = (t) => {
  const d = new Date(t);
  const p = (n) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
};

/* Thời lượng tính bằng phút (thống kê thời gian phản hồi). null/undefined = chưa có dữ liệu → '—', không hiện "0 phút". */
export function fmtMinutes(m) {
  if (m == null || Number.isNaN(m)) return '—';
  if (m < 1) return '< 1 phút';
  const total = Math.round(m);
  if (total < 60) return `${total} phút`;
  if (total < 1440) return `${Math.floor(total / 60)} giờ${total % 60 ? ` ${total % 60} phút` : ''}`;
  const h = Math.round((total % 1440) / 60);
  return `${Math.floor(total / 1440)} ngày${h ? ` ${h} giờ` : ''}`;
}

export const fmtDist = (m) => (m < 1000 ? `${Math.round(m)} m` : `${(m / 1000).toFixed(1)} km`);
