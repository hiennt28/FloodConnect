import { api } from '@/services/http';

/* Hồ sơ người dùng, tìm kiếm, theo dõi. */
export const userApi = {
  me: () => api('/api/users/me'),
  updateMe: (body) => api('/api/users/me', { method: 'PUT', body }),
  updateAvatar: (form) => api('/api/users/me/avatar', { method: 'PUT', form }),
  removeAvatar: () => api('/api/users/me/avatar', { method: 'DELETE' }),
  changePassword: (body) => api('/api/users/me/password', { method: 'PUT', body }),
  updateGps: (body) => api('/api/users/me/gps', { method: 'PUT', body }),
  profile: (id) => api(`/api/users/${id}`),
  posts: (id, page) => api(`/api/users/${id}/posts?page=${page}&size=10`),
  search: (q, type, size) => api(`/api/users/search?q=${encodeURIComponent(q)}&type=${type}&size=${size}`),
  orgDirectory: () => api('/api/users/search?q=&type=ORG&size=100'),
  follow: (id) => api(`/api/users/${id}/follow`, { method: 'POST' }),
  unfollow: (id) => api(`/api/users/${id}/follow`, { method: 'DELETE' }),
};
