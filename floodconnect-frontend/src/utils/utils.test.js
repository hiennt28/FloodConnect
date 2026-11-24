import { describe, expect, it, vi } from 'vitest';
import { EMAIL_RULE, PASSWORD_CHARS_RULE, PASSWORD_RULE, PHONE_RULE, validateCoords, fmtDist, fmtMinutes, ago, haversine, fmtCountdown, zaloLink, waitLeft, MIN,
  parseLocationText, isShortMapLink, geoJsonBounds, PROVINCES } from '@/utils';

describe('validators', () => {
  it('mật khẩu, email, số điện thoại', () => {
    expect(PASSWORD_RULE('abc12345')).toBe(true);
    expect(PASSWORD_RULE('abcdefgh')).toBe(false);
    expect(PASSWORD_RULE('a1')).toBe(false);
    expect(PASSWORD_RULE('Demo@123!')).toBe(true);
    expect(PASSWORD_RULE('mậtkhẩu123')).toBe(false);
    expect(PASSWORD_RULE('mat khau123')).toBe(false);
    expect(PASSWORD_RULE('a1' + 'x'.repeat(62))).toBe(true);
    expect(PASSWORD_RULE('a1' + 'x'.repeat(63))).toBe(false);
    expect(PASSWORD_CHARS_RULE('Demo12345')).toBe(true);
    expect(PASSWORD_CHARS_RULE('Demo12345ạ')).toBe(false);
    expect(PASSWORD_CHARS_RULE('Demo 12345')).toBe(false);
    expect(EMAIL_RULE('a@b.vn')).toBe(true);
    expect(EMAIL_RULE('a@b')).toBe(false);
    expect(PHONE_RULE('090123456')).toBe(true);
    expect(PHONE_RULE('09012')).toBe(false);
  });
  it('tọa độ: đủ cặp, hợp lệ, trong lãnh thổ Việt Nam', () => {
    expect(validateCoords(21.03, 105.85)).toBeNull();
    expect(validateCoords('', 105)).toMatch(/đủ vĩ độ/);
    expect(validateCoords('abc', 105)).toMatch(/số thập phân/);
    expect(validateCoords(95, 105)).toMatch(/-90 đến 90/);
    expect(validateCoords(40.7, -74)).toMatch(/ngoài lãnh thổ/);
  });
});

describe('vị trí dán vào và địa giới', () => {
  it('đọc tọa độ dạng chữ và link Google Maps đầy đủ', () => {
    expect(parseLocationText('16.0544, 108.2022')).toEqual({ lat: 16.0544, lng: 108.2022 });
    expect(parseLocationText('  16.0544 108.2022 ')).toEqual({ lat: 16.0544, lng: 108.2022 });
    expect(parseLocationText('16.0544;108.2022')).toEqual({ lat: 16.0544, lng: 108.2022 });
    expect(parseLocationText('https://www.google.com/maps?q=21.0368,105.8346')).toEqual({ lat: 21.0368, lng: 105.8346 });
    expect(parseLocationText('https://www.google.com/maps/search/?api=1&query=21.0368%2C105.8346')).toEqual({ lat: 21.0368, lng: 105.8346 });
    expect(parseLocationText('https://www.google.com/maps/@21.0368,105.8346,17z')).toEqual({ lat: 21.0368, lng: 105.8346 });
    // link địa điểm: lấy tọa độ địa điểm (!3d!4d), không lấy tâm khung nhìn (@)
    expect(parseLocationText('https://www.google.com/maps/place/Lang/@21.03,105.83,17z/data=!3m1!4b1!4m6!3m5!8m2!3d21.0368!4d105.8346'))
      .toEqual({ lat: 21.0368, lng: 105.8346 });
    expect(parseLocationText('16.123456789, 108.987654321')).toEqual({ lat: 16.12346, lng: 108.98765 });
  });
  it('không đọc được thì trả null; nhận ra link rút gọn', () => {
    for (const bad of ['', '   ', 'nhà tôi ở Đà Nẵng', '16.05', 'abc, def', 'https://maps.app.goo.gl/AbCdEf123']) expect(parseLocationText(bad)).toBeNull();
    expect(isShortMapLink('https://maps.app.goo.gl/AbCdEf123')).toBe(true);
    expect(isShortMapLink('https://www.google.com/maps?q=21,105')).toBe(false);
  });
  it('khung bao của đường viền GeoJSON (kinh độ trước, vĩ độ sau)', () => {
    const g = { type: 'MultiPolygon', coordinates: [[[[105.82, 21.02], [105.85, 21.02], [105.85, 21.05], [105.82, 21.02]]], [[[105.9, 21.0], [105.91, 21.0], [105.9, 21.01], [105.9, 21.0]]]] };
    expect(geoJsonBounds(g)).toEqual([[21.0, 105.82], [21.05, 105.91]]);
    expect(geoJsonBounds(null)).toBeNull();
    expect(geoJsonBounds({ type: 'MultiPolygon', coordinates: [] })).toBeNull();
  });
  it('danh sách hành chính: 34 tỉnh, 3.321 xã, mã xã không trùng, tên chuẩn Unicode NFC', () => {
    const wards = PROVINCES.flatMap((p) => p.wards);
    expect(PROVINCES).toHaveLength(34);
    expect(wards).toHaveLength(3321);
    expect(new Set(wards.map(([c]) => c)).size).toBe(3321);
    expect([...PROVINCES.map((p) => p.name), ...wards.map(([, n]) => n)].every((n) => n === n.normalize('NFC'))).toBe(true);
    expect(wards.find(([, n]) => n === 'Xã Ia Mơ')?.[0]).toBe('23938');
  });
});

describe('format / geo / sosFlow', () => {
  it('fmtMinutes: chưa có dữ liệu hiện —, đổi phút sang giờ/ngày', () => {
    expect(fmtMinutes(null)).toBe('—');
    expect(fmtMinutes(0.4)).toBe('< 1 phút');
    expect(fmtMinutes(12.6)).toBe('13 phút');
    expect(fmtMinutes(60)).toBe('1 giờ');
    expect(fmtMinutes(135)).toBe('2 giờ 15 phút');
    expect(fmtMinutes(1440 + 180)).toBe('1 ngày 3 giờ');
  });
  it('khoảng cách và thời gian tương đối', () => {
    expect(fmtDist(250.4)).toBe('250 m');
    expect(fmtDist(1530)).toBe('1.5 km');
    expect(ago(Date.now() - 10_000)).toBe('Vừa xong');
    expect(ago(Date.now() - 5 * MIN)).toBe('5 phút trước');
  });
  it('haversine Hà Nội – TP.HCM khoảng 1.100–1.200 km', () => {
    const d = haversine({ lat: 21.0285, lng: 105.8542 }, { lat: 10.8231, lng: 106.6297 });
    expect(d).toBeGreaterThan(1_100_000);
    expect(d).toBeLessThan(1_200_000);
  });
  it('đếm ngược và link Zalo', () => {
    expect(fmtCountdown(65_000)).toBe('01:05');
    expect(zaloLink('090 123-4567')).toBe('https://zalo.me/0901234567');
    vi.useFakeTimers(); vi.setSystemTime(1_000_000);
    expect(waitLeft(1_000_000 - 4 * MIN, 5)).toBe(MIN);
    expect(waitLeft(0, 5)).toBe(0);
    vi.useRealTimers();
  });
});

/* Nút trên bản đồ và thông báo mở ĐÚNG ca / tổ chức (trước đây "Điều phối ca này" chỉ mở trang điều phối chung) */
describe('đường dẫn mở đúng ca / tổ chức', async () => {
  const { dispatchLink, adminOrgLink } = await import('@/utils');
  it('Admin mở hộp chi tiết của đúng ca, tổ chức chọn sẵn đúng ca', () => {
    expect(dispatchLink('ADMIN', '42')).toBe('/admin/sos?case=42');
    expect(dispatchLink('ORG', '42')).toBe('/rescue?case=42');
  });
  it('tên tổ chức trỏ tới trang chi tiết tổ chức của Admin', () => {
    expect(adminOrgLink('7')).toBe('/admin/orgs/7');
  });
});
