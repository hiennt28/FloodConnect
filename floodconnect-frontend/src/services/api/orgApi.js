import { api } from '@/services/http';

/* Hồ sơ và trạng thái hoạt động của tổ chức đang đăng nhập. */
export const orgApi = {
  me: () => api('/api/org/me'),
  /* Giấy tờ pháp lý / CCCD (lưu riêng tư): chỉ Admin và chính tổ chức tải được; trả Response để mở bằng openProtectedFile */
  docFile: (docId) => api(`/api/org-docs/${docId}/file`, { raw: true }),
  /* Tổ chức đã duyệt sửa thông tin: mô tả, trang web, địa chỉ, ghim trụ sở (không gồm tên, loại tổ chức, giấy tờ) */
  update: (body) => api('/api/org/me', { method: 'PUT', body }),
  resubmit: (form) => api('/api/org/me/resubmit', { method: 'PUT', form }),
  setOperatingStatus: (status, confirm) => api('/api/org/me/operating-status', { method: 'PUT', body: { status, confirm } }),
};
