import { api } from '@/services/http';

/* API công khai, không gửi token (auth: false): khách chưa đăng nhập cũng gọi được. */
export const publicApi = {
  stats: () => api('/api/public/stats', { auth: false }),
  /* Số tổ chức đang Sẵn sàng (đếm mỗi lần gọi, không lưu đệm): { count } */
  availableOrgs: () => api('/api/public/available-orgs', { auth: false }),
};
