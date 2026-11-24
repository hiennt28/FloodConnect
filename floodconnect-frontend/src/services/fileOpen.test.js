// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { openProtectedFile } from './fileOpen';

/* Giấy tờ tổ chức lưu riêng tư: tải bằng API kèm token rồi mở ở tab mới (#6) */
describe('openProtectedFile', () => {
  const blobs = [];
  window.URL.createObjectURL = vi.fn((b) => { blobs.push(b); return 'blob:x'; });
  window.URL.revokeObjectURL = vi.fn();
  const fakeWin = () => ({ document: {}, location: {}, close: vi.fn() });
  const res = (type, extra = {}) => ({ redirected: false, url: 'http://localhost:8080/api/org-docs/1/file',
    headers: new Headers({ 'Content-Type': type }), arrayBuffer: async () => new Uint8Array([1, 2]).buffer, ...extra });
  afterEach(() => { blobs.length = 0; vi.restoreAllMocks(); });

  it('ảnh/PDF hợp lệ: mở nội dung trong tab vừa mở, giữ đúng kiểu tệp', async () => {
    const win = fakeWin();
    vi.spyOn(window, 'open').mockReturnValue(win);
    expect(await openProtectedFile(async () => res('image/png;charset=UTF-8'))).toBeNull();
    expect(win.location.href).toBe('blob:x');
    expect(blobs[0].type).toBe('image/png');
  });

  it('kiểu tệp lạ (vd. svg, html) bị ép thành tải về, không để trình duyệt chạy nội dung', async () => {
    vi.spyOn(window, 'open').mockReturnValue(fakeWin());
    await openProtectedFile(async () => res('image/svg+xml'));
    expect(blobs[0].type).toBe('application/octet-stream');
  });

  it('ảnh mẫu bị chuyển hướng sang trang khác: mở thẳng trang đó', async () => {
    const win = fakeWin();
    vi.spyOn(window, 'open').mockReturnValue(win);
    await openProtectedFile(async () => res('image/svg+xml', { redirected: true, url: 'https://placehold.co/600x400' }));
    expect(win.location.href).toBe('https://placehold.co/600x400');
    expect(blobs).toHaveLength(0);
  });

  it('không có quyền / lỗi mạng: đóng tab vừa mở và trả lời lỗi', async () => {
    const win = fakeWin();
    vi.spyOn(window, 'open').mockReturnValue(win);
    expect(await openProtectedFile(async () => { throw new Error('Không tìm thấy giấy tờ.'); })).toBe('Không tìm thấy giấy tờ.');
    expect(win.close).toHaveBeenCalled();
  });

  it('trình duyệt chặn cửa sổ mới: báo cho người dùng', async () => {
    vi.spyOn(window, 'open').mockReturnValue(null);
    expect(await openProtectedFile(async () => res('image/png'))).toMatch(/chặn cửa sổ/);
  });
});
