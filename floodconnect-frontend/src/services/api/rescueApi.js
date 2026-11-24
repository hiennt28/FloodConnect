import { api } from '@/services/http';

/* Tổ chức cứu hộ xử lý ca SOS. */
export const rescueApi = {
  /* Ca đang giao cho tổ chức (Đã phân công, Đã tiếp nhận); lịch sử xem theo trang ở history() */
  cases: () => api('/api/org/sos?filter=ACTIVE'),
  history: (page = 0, size = 10) => api(`/api/org/sos/history?page=${page}&size=${size}`),
  suggested: () => api('/api/org/sos/suggested'),
  claim: (id) => api(`/api/org/sos/${id}/claim`, { method: 'POST' }),
  accept: (id) => api(`/api/org/sos/${id}/accept`, { method: 'POST' }),
  reject: (id, reason) => api(`/api/org/sos/${id}/reject`, { method: 'POST', body: { reason } }),
  complete: (id) => api(`/api/org/sos/${id}/complete`, { method: 'POST' }),
};
