import { MIN } from './constants';

export const waitLeft = (from, minutes) => Math.max(0, from + minutes * MIN - Date.now());
export const fmtCountdown = (ms) => {
  const s = Math.ceil(ms / 1000);
  return `${String(Math.floor(s / 60)).padStart(2, '0')}:${String(s % 60).padStart(2, '0')}`;
};

/* Liên hệ qua Zalo bằng số điện thoại */
export const zaloLink = (phone) => `https://zalo.me/${String(phone || '').replace(/\D/g, '')}`;

/** Mở Google Maps chỉ đường lái xe từ origin đến dest (mỗi bên là {lat, lng}). Bỏ origin thì Google dùng vị trí hiện tại của thiết bị. */
export function openGoogleMapsDirections(origin, dest) {
  const p = new URLSearchParams({ api: '1', destination: `${dest.lat},${dest.lng}`, travelmode: 'driving' });
  if (origin?.lat != null && origin?.lng != null) p.set('origin', `${origin.lat},${origin.lng}`);
  window.open(`https://www.google.com/maps/dir/?${p.toString()}`, '_blank', 'noopener,noreferrer');
}

/* Đường dẫn mở ĐÚNG một ca trên trang điều phối (trang đích đọc ?case= như khi bấm thông báo):
   Admin: hộp chi tiết ca ở Điều phối SOS; tổ chức: chọn sẵn ca trên Điều phối cứu hộ */
export const dispatchLink = (role, caseId) => `${role === 'ADMIN' ? '/admin/sos' : '/rescue'}?case=${encodeURIComponent(caseId)}`;
/* Trang chi tiết tổ chức của Admin */
export const adminOrgLink = (orgId) => `/admin/orgs/${encodeURIComponent(orgId)}`;
