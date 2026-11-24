import { api } from '@/services/http';

/* Xác thực: đăng nhập, đăng ký, xác thực email, quên/đặt lại mật khẩu. Không gắn JWT. */
export const authApi = {
  login: (identifier, password) => api('/api/auth/login', { method: 'POST', auth: false, body: { identifier, password } }),
  logout: (refreshToken) => api('/api/auth/logout', { method: 'POST', auth: false, body: { refreshToken } }),
  register: (body) => api('/api/auth/register', { method: 'POST', auth: false, body }),
  registerOrg: (form) => api('/api/auth/register-org', { method: 'POST', auth: false, form }),
  verifyEmail: (email, code) => api('/api/auth/verify-email', { method: 'POST', auth: false, body: { email, code } }),
  resendVerification: (email) => api('/api/auth/resend-verification', { method: 'POST', auth: false, body: { email } }),
  forgotPassword: (email) => api('/api/auth/forgot-password', { method: 'POST', auth: false, body: { email } }),
  resetPassword: (email, code, newPassword, confirmPassword) =>
    api('/api/auth/reset-password', { method: 'POST', auth: false, body: { email, code, newPassword, confirmPassword } }),
};
