import { api } from '@/services/http';

/* Mạng xã hội: bài đăng, lượt thích, bình luận, báo cáo. */
export const postApi = {
  feed: (filter, page, size) => api(`/api/posts?filter=${filter}&page=${page}&size=${size}`),
  get: (id) => api(`/api/posts/${id}`),
  create: (form) => api('/api/posts', { method: 'POST', form }),
  update: (id, form) => api(`/api/posts/${id}`, { method: 'PUT', form }),
  remove: (id) => api(`/api/posts/${id}`, { method: 'DELETE' }),
  like: (id) => api(`/api/posts/${id}/like`, { method: 'POST' }),
  report: (id, body) => api(`/api/posts/${id}/report`, { method: 'POST', body }),
  /* Bình luận gốc mới nhất trước, 20 mỗi trang; phản hồi cũ trước, 10 mỗi trang (chỉ tải khi mở) */
  comments: (postId, page = 0, size = 20) => api(`/api/posts/${postId}/comments?page=${page}&size=${size}`),
  replies: (commentId, page = 0, size = 10) => api(`/api/comments/${commentId}/replies?page=${page}&size=${size}`),
  /* Một bình luận theo mã: mở từ thông báo khi bình luận chưa nằm trong các trang đã tải */
  comment: (id) => api(`/api/comments/${id}`),
  addComment: (postId, body) => api(`/api/posts/${postId}/comments`, { method: 'POST', body }),
  editComment: (id, body) => api(`/api/comments/${id}`, { method: 'PUT', body }),
  deleteComment: (id) => api(`/api/comments/${id}`, { method: 'DELETE' }),
};
