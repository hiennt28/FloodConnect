import { api } from '@/services/http';

/* Địa giới hành chính (bảng admin_wards ở backend): tọa độ thuộc phường/xã nào, điểm đại diện và đường viền của một xã. */
export const geoApi = {
  /* Công khai (auth:false): khách đăng ký tổ chức cũng dùng để đặt ghim trụ sở */
  locate: (lat, lng) => api(`/api/geo/locate?lat=${lat}&lng=${lng}`, { auth: false }),
  ward: (code) => api(`/api/geo/wards/${encodeURIComponent(code)}`, { auth: false }),
};
