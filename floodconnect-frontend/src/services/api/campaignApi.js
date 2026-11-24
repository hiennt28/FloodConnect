import { api } from '@/services/http';

/* Chiến dịch gây quỹ: xem, quản lý (tổ chức), ủng hộ, báo cáo. */
export const campaignApi = {
  list: (status, q, page, size) => api(`/api/campaigns?status=${status}&q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  get: (id) => api(`/api/campaigns/${id}`),
  donations: (id, page, size) => api(`/api/campaigns/${id}/donations?page=${page}&size=${size}`),
  donate: (id, body) => api(`/api/campaigns/${id}/donate`, { method: 'POST', body }),
  report: (id, body) => api(`/api/campaigns/${id}/report`, { method: 'POST', body }),
  myDonations: () => api('/api/donations/mine'),
  paymentCallback: (body) => api('/api/payments/callback', { method: 'POST', body }), // backend yêu cầu đăng nhập: chỉ chính người ủng hộ mới xác nhận được
  // phía tổ chức
  mine: () => api('/api/org/campaigns'),
  create: (form) => api('/api/org/campaigns', { method: 'POST', form }),
  update: (id, body) => api(`/api/org/campaigns/${id}`, { method: 'PUT', body }),
  setAvatar: (id, form) => api(`/api/org/campaigns/${id}/avatar`, { method: 'PUT', form }),
  removeImage: (id, mediaId) => api(`/api/org/campaigns/${id}/images/${mediaId}`, { method: 'DELETE' }),
  addImages: (id, form) => api(`/api/org/campaigns/${id}/images`, { method: 'POST', form }),
  end: (id) => api(`/api/org/campaigns/${id}/end`, { method: 'POST' }),
  extend: (id, endDate) => api(`/api/org/campaigns/${id}/extend`, { method: 'POST', body: { endDate } }),
};
