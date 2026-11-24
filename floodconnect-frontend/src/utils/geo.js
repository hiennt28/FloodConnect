import PROVINCES_DATA from '@/assets/data/vn-admin-2025.json';

export function haversine(a, b) {
  const R = 6371000;
  const r = (x) => (x * Math.PI) / 180;
  const dLat = r(b.lat - a.lat);
  const dLng = r(b.lng - a.lng);
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(r(a.lat)) * Math.cos(r(b.lat)) * Math.sin(dLng / 2) ** 2;
  return 2 * R * Math.asin(Math.sqrt(h));
}

/* Đơn vị hành chính 2 cấp (Tỉnh/Thành phố → Phường/Xã/Đặc khu) theo Nghị quyết 202/2025/QH15:
   34 tỉnh/thành, 3.321 đơn vị cấp xã, mã theo Tổng cục Thống kê (trùng mã bảng admin_wards ở backend).
   Nguồn: repo thanglequoc/vietnamese-provinces-database (MIT), sinh bằng scripts/geo/build-admin-geo.mjs.
   Định dạng: [{ code, name, wards: [[wardCode, wardName], ...] }] */
export const PROVINCES = PROVINCES_DATA;
export const provinceByCode = (code) => PROVINCES_DATA.find((p) => p.code === code) || null;
export const wardsOf = (provinceCode) => provinceByCode(provinceCode)?.wards || [];
export const provinceName = (code) => provinceByCode(code)?.name || '';
export const wardName = (provinceCode, wardCode) => wardsOf(provinceCode).find(([c]) => c === wardCode)?.[1] || '';
/* Mã tỉnh chứa một mã xã (VD sửa bài: backend chỉ lưu mã xã) */
export const provinceOfWard = (wardCode) => PROVINCES_DATA.find((p) => p.wards.some(([c]) => c === wardCode))?.code || '';

/* Đọc tọa độ người dân dán vào (thường là vị trí người thân gửi qua Zalo / Google Maps để gửi SOS hộ). Nhận:
   "16.0544, 108.2022", "16.0544 108.2022", hoặc link Google Maps đầy đủ (…!3d16.05!4d108.2…, ?q=16.05,108.2, ?query=, ?ll=, /@16.05,108.2,15z).
   Ưu tiên !3d!4d (đúng địa điểm được chọn) trước @ (chỉ là tâm khung nhìn). Trả { lat, lng } hoặc null.
   Không kiểm tra phạm vi Việt Nam ở đây, để validateCoords báo lỗi rõ ràng. */
const NUM = '(-?\\d{1,3}(?:\\.\\d+)?)';
const LOCATION_PATTERNS = [
  new RegExp(`!3d${NUM}!4d${NUM}`),
  new RegExp(`[?&](?:q|query|ll|destination)=${NUM}(?:,|%2C)\\s*${NUM}`, 'i'),
  new RegExp(`@${NUM},${NUM}`),
  new RegExp(`^${NUM}\\s*[,;\\s]\\s*${NUM}$`),
];
export function parseLocationText(text) {
  const s = String(text || '').trim();
  if (!s) return null;
  for (const re of LOCATION_PATTERNS) {
    const m = s.match(re);
    if (m) {
      const lat = Number(m[1]);
      const lng = Number(m[2]);
      if (Number.isFinite(lat) && Number.isFinite(lng)) return { lat: +lat.toFixed(5), lng: +lng.toFixed(5) };
    }
  }
  return null;
}
/* Link rút gọn của Google Maps không chứa tọa độ (phải mở ra mới biết), nên không đọc được */
export const isShortMapLink = (text) => /(maps\.app\.goo\.gl|goo\.gl\/maps)/i.test(String(text || ''));

/* Khung bao [[minLat, minLng], [maxLat, maxLng]] của hình GeoJSON (tọa độ GeoJSON: kinh độ trước, vĩ độ sau) */
export function geoJsonBounds(geometry) {
  let minLat = 90; let minLng = 180; let maxLat = -90; let maxLng = -180;
  const walk = (c) => {
    if (typeof c[0] === 'number') {
      const [lng, lat] = c;
      minLat = Math.min(minLat, lat); maxLat = Math.max(maxLat, lat);
      minLng = Math.min(minLng, lng); maxLng = Math.max(maxLng, lng);
    } else c.forEach(walk);
  };
  if (!geometry?.coordinates?.length) return null;
  walk(geometry.coordinates);
  return minLat <= maxLat ? [[minLat, minLng], [maxLat, maxLng]] : null;
}
