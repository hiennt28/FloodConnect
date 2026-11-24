import { api } from '@/services/http';

/* Chức năng của Admin. */
export const adminApi = {
  // điều phối SOS
  /* Bảng ca theo trang (ca đã kết thúc): status = ENDED | COMPLETED | CANCELLED | INVALID...; ca đang hoạt động lấy ở sosApi.active() */
  sosPage: ({ status, level, sortBy, dir, page = 0, size = 20 }) => {
    const q = new URLSearchParams(Object.entries({ status, level, sortBy, dir, page, size }).filter(([, v]) => v != null && v !== '').map(([k, v]) => [k, String(v)]));
    return api(`/api/admin/sos?${q}`);
  },
  heatmap: () => api('/api/admin/sos/heatmap'),
  /* Bản đồ điều phối: status = ACTIVE | ALL | một trạng thái (tên của backend); ca đã kết thúc chỉ lấy trong days ngày */
  sosMap: ({ status, level, days } = {}) => {
    const q = new URLSearchParams(Object.entries({ status, level, days }).filter(([, v]) => v != null && v !== '').map(([k, v]) => [k, String(v)]));
    return api(`/api/admin/sos/map?${q}`);
  },
  sosDetail: (id) => api(`/api/admin/sos/${id}`),
  candidates: (id) => api(`/api/admin/sos/${id}/candidates`),
  nearby: (id) => api(`/api/admin/sos/${id}/nearby`),
  assign: (id, orgId) => api(`/api/admin/sos/${id}/assign`, { method: 'POST', body: { orgId } }),
  /* Hủy phân công ca Đã phân công (tổ chức chưa xác nhận, kể cả chưa hết 5 phút): ca về Chờ xử lý, lý do bắt buộc */
  unassign: (id, reason) => api(`/api/admin/sos/${id}/unassign`, { method: 'POST', body: { reason } }),
  /* note = ghi chú xác minh, chỉ gửi khi xác minh ca AI xếp KHONG (backend bắt buộc với ca đó, từ chối với ca khác) */
  adjustLevel: (id, newLevel, note) => api(`/api/admin/sos/${id}/adjust-level`, { method: 'POST', body: note == null ? { newLevel } : { newLevel, note } }),
  confirmVerification: (id) => api(`/api/admin/sos/${id}/confirm-verification`, { method: 'POST' }),
  markInvalid: (id, reason) => api(`/api/admin/sos/${id}/mark-invalid`, { method: 'POST', body: { reason } }),
  /* Admin xác nhận hoàn thành ca Đã tiếp nhận sau khi liên hệ (người dân đã an toàn / tổ chức quên bấm) */
  completeSos: (id, reason) => api(`/api/admin/sos/${id}/complete`, { method: 'POST', body: { reason } }),
  // thống kê
  citizenStats: () => api('/api/admin/stats/citizens'),
  sosStats: (period) => api(`/api/admin/stats/sos?period=${period}`),
  sosStatusStats: (period) => api(`/api/admin/stats/sos-status?period=${period}`),
  sosTimeline: (days) => api(`/api/admin/stats/sos-timeline?days=${days}`),
  overview: () => api('/api/admin/stats/overview'),
  orgStats: () => api('/api/admin/stats/orgs'),
  topOrgs: () => api('/api/admin/stats/top-orgs'),
  topDonors: () => api('/api/admin/stats/top-donors'),
  // tổ chức
  orgs: (filter) => api(`/api/admin/orgs?filter=${filter}`),
  orgDetail: (id) => api(`/api/admin/orgs/${id}`),
  reviewOrg: (id, body) => api(`/api/admin/orgs/${id}/review`, { method: 'POST', body }),
  lockOrg: (id, body) => api(`/api/admin/orgs/${id}/lock`, { method: 'POST', body }),
  unlockOrg: (id) => api(`/api/admin/orgs/${id}/unlock`, { method: 'POST' }),
  // tài khoản Admin (chỉ Admin gốc; Admin thường nhận 403 ROOT_ADMIN_ONLY)
  admins: () => api('/api/admin/admins'),
  /* Không có mật khẩu: người được tạo tự đặt bằng mã gửi tới email */
  createAdmin: (body) => api('/api/admin/admins', { method: 'POST', body }),
  resendAdminInvite: (id) => api(`/api/admin/admins/${id}/resend-invite`, { method: 'POST' }),
  disableAdmin: (id, reason) => api(`/api/admin/admins/${id}/disable`, { method: 'POST', body: { reason } }),
  enableAdmin: (id) => api(`/api/admin/admins/${id}/enable`, { method: 'POST' }),
  // người dân
  citizens: (filter) => api(`/api/admin/citizens?filter=${filter}`),
  /* Tab "Tất cả người dân": tìm theo tên (không dấu), email, SĐT; phân trang */
  citizenSearch: (q, page, size) => api(`/api/admin/citizens/search?q=${encodeURIComponent(q)}&page=${page}&size=${size}`),
  citizenDetail: (id) => api(`/api/admin/citizens/${id}`),
  lockCitizen: (id, body) => api(`/api/admin/citizens/${id}/lock`, { method: 'POST', body }),
  unlockCitizen: (id) => api(`/api/admin/citizens/${id}/unlock`, { method: 'POST' }),
  // báo cáo bài đăng / chiến dịch
  /* Báo cáo vi phạm theo trang (Trước / Sau): { items, total, last } */
  reportedPosts: (page = 0, size = 20) => api(`/api/admin/post-reports/posts?page=${page}&size=${size}`),
  postReports: (page = 0, size = 20) => api(`/api/admin/post-reports?page=${page}&size=${size}`),
  postReportDetail: (id) => api(`/api/admin/posts/${id}/reports`),
  moderatePost: (id, body) => api(`/api/admin/posts/${id}/moderate`, { method: 'POST', body }),
  reportedCampaigns: (page = 0, size = 20) => api(`/api/admin/campaign-reports/campaigns?page=${page}&size=${size}`),
  campaignReports: (page = 0, size = 20) => api(`/api/admin/campaign-reports?page=${page}&size=${size}`),
  campaignReportDetail: (id) => api(`/api/admin/campaigns/${id}/reports`),
  moderateCampaign: (id, body) => api(`/api/admin/campaigns/${id}/moderate`, { method: 'POST', body }),
};
