import { api } from '@/services/http';

/* Ca SOS phía người dân và dữ liệu công khai (danh sách, bản đồ). */
export const sosApi = {
  mine: () => api('/api/sos/mine'),
  /* Mọi ca đang hoạt động (luôn đủ): { items, total, limit }. Ca đã hoàn thành xem theo trang ở page() */
  active: () => api('/api/sos/active'),
  page: ({ status, page = 0, size = 10 }) => api(`/api/sos?status=${status}&page=${page}&size=${size}`),
  detail: (id) => api(`/api/sos/${id}`),
  /* Ca SOS chỉ gồm chữ và vị trí (không kèm ảnh) nên gửi JSON */
  create: (body) => api('/api/sos', { method: 'POST', body }),
  update: (id, description) => api(`/api/sos/${id}`, { method: 'PUT', body: { description } }),
  cancel: (id) => api(`/api/sos/${id}/cancel`, { method: 'POST' }),
  /* Người dân báo đã an toàn khi ca đang Đã tiếp nhận: ca chuyển Hoàn thành, tổ chức được báo dừng cứu hộ */
  markSafe: (id, note) => api(`/api/sos/${id}/safe`, { method: 'POST', body: { note } }),
  markers: () => api('/api/map/markers'),
};
