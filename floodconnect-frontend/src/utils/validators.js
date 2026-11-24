/* Quy tắc kiểm tra tọa độ (dùng chung cho bài đăng và SOS) */
export function validateCoords(lat, lng) {
  if (lat === '' || lng === '' || lat == null || lng == null) return 'Vị trí phải có đủ vĩ độ và kinh độ.';
  const la = Number(lat);
  const lo = Number(lng);
  if (Number.isNaN(la) || Number.isNaN(lo)) return 'Vĩ độ và kinh độ phải là số thập phân.';
  if (la < -90 || la > 90) return 'Vĩ độ phải nằm trong khoảng -90 đến 90.';
  if (lo < -180 || lo > 180) return 'Kinh độ phải nằm trong khoảng -180 đến 180.';
  if (la < 8.5 || la > 23.4 || lo < 102.1 || lo > 109.5) return 'Vị trí không được hỗ trợ (ngoài lãnh thổ Việt Nam).';
  return null;
}

/* Mật khẩu chỉ gồm ký tự ASCII in được, không dấu cách (khớp backend Validators.passwordCharsOk): chặn chữ tiếng Việt có dấu,
   vì cùng một chữ có thể mã hóa khác nhau tùy thiết bị/bộ gõ khiến đăng nhập sai, và người dùng dễ quên tắt Unikey khi ô đang ẩn. */
export const PASSWORD_MAX = 64;
export const PASSWORD_MSG = 'Mật khẩu 8–64 ký tự, gồm cả chữ và số; chỉ dùng chữ không dấu, số và ký hiệu (không dấu cách).';
export const PASSWORD_CHARS_MSG = 'Mật khẩu chỉ gồm chữ không dấu, số và ký hiệu (không có dấu cách). Hãy tắt bộ gõ tiếng Việt rồi nhập lại.';
export const PASSWORD_CHARS_RULE = (p) => /^[\x21-\x7E]+$/.test(p);
export const PASSWORD_RULE = (p) => PASSWORD_CHARS_RULE(p) && p.length >= 8 && p.length <= PASSWORD_MAX && /[a-zA-Z]/.test(p) && /\d/.test(p);
export const EMAIL_RULE = (e) => /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(e);
export const PHONE_RULE = (p) => /^\d{9,11}$/.test(p);
/* Trang web công khai của tổ chức: chỉ http/https (khớp backend), để không hiển thị thành liên kết javascript: */
export const WEBSITE_RULE = (u) => u.length <= 500 && /^https?:\/\/\S+$/i.test(u);
export const WEBSITE_MSG = 'Đường dẫn phải bắt đầu bằng http:// hoặc https:// và tối đa 500 ký tự.';
/* Trọng số AI Matching (giống MatchingService ở backend): hiện trên giao diện để Admin / tổ chức hiểu điểm */
export const MATCH_WEIGHTS = { L: 25, S: 30, C: 30, W: 15 };
/* Mô tả tổ chức bắt buộc (đăng ký, gửi lại hồ sơ, sửa thông tin): AI Matching suy năng lực của tổ chức từ mô tả (điểm C) */
export const ORG_DESCRIPTION_MAX = 2000;
/* Năng lực tiếp nhận (bắt buộc, khớp backend V10): số ca xử lý cùng lúc và bán kính hoạt động.
   AI Matching dùng để tính điểm tải W (đang nhận x / tối đa) và loại tổ chức ở quá xa khỏi vùng phục vụ (điểm L). */
export const ORG_CASES_RANGE = { min: 1, max: 50 };
export const ORG_RADIUS_RANGE = { min: 1, max: 100 };
export const orgCapacityErrors = (cases, radiusKm) => {
  const e = {};
  const c = String(cases ?? '').trim();
  const r = String(radiusKm ?? '').trim();
  if (!c) e.cases = 'Vui lòng nhập số ca xử lý cùng lúc.';
  else if (!/^\d+$/.test(c) || +c < ORG_CASES_RANGE.min || +c > ORG_CASES_RANGE.max) e.cases = `Số ca xử lý cùng lúc là số nguyên từ ${ORG_CASES_RANGE.min} đến ${ORG_CASES_RANGE.max}.`;
  if (!r) e.radiusKm = 'Vui lòng nhập bán kính hoạt động.';
  else if (!Number.isFinite(+r) || +r < ORG_RADIUS_RANGE.min || +r > ORG_RADIUS_RANGE.max) e.radiusKm = `Bán kính hoạt động từ ${ORG_RADIUS_RANGE.min} đến ${ORG_RADIUS_RANGE.max} km.`;
  return e;
};
export const orgDescriptionError = (d) => {
  const v = (d || '').trim();
  if (!v) return 'Vui lòng mô tả tổ chức: năng lực, phương tiện, khu vực hoạt động (AI dùng mô tả này để gợi ý ca phù hợp).';
  return v.length > ORG_DESCRIPTION_MAX ? `Mô tả tối đa ${ORG_DESCRIPTION_MAX.toLocaleString('vi-VN')} ký tự.` : null;
};
