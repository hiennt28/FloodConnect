import { api } from '@/services/http';

/* Thông báo của người dùng hiện tại. */
export const notificationApi = {
  /* Trang 0 (30 mới nhất) đồng bộ cho chuông; trang Thông báo tải các trang sau bằng "Tải thêm" */
  list: (page = 0, size = 30) => api(`/api/notifications?page=${page}&size=${size}`),
  markRead: (id) => api(`/api/notifications/${id}/read`, { method: 'PUT' }),
  markAllRead: () => api('/api/notifications/read-all', { method: 'PUT' }),
};
