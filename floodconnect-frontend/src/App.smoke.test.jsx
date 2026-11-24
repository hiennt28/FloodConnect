// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';

if (!window.URL.createObjectURL) window.URL.createObjectURL = vi.fn(() => 'blob:x');
vi.mock('@/components/map/BaseMap', () => ({ default: () => null }));
/* Leaflet không chạy được trong jsdom (trang Điều phối cứu hộ của tổ chức có bản đồ) */
vi.mock('@/components/map/MapView', () => ({ default: () => null, pin: () => null, VN_BOUNDS: [[8.2, 102.0], [23.5, 109.6]] }));
/* Không mở WebSocket thật trong test; chỉ kiểm tra có kết nối thông báo thời gian thực khi đã đăng nhập */
const { connectNotifications } = vi.hoisted(() => ({ connectNotifications: vi.fn(() => () => {}) }));
vi.mock('@/services/realtime', () => ({ connectNotifications }));

const { AppProvider } = await import('@/context/AppContext');
const { default: App } = await import('@/App');

describe('App (smoke)', () => {
  afterEach(cleanup); // vitest không bật globals nên Testing Library không tự dọn DOM giữa các test
  it('chưa đăng nhập: hiển thị trang đăng nhập, không gọi API', async () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response('{}'));
    render(<MemoryRouter initialEntries={['/login']}><AppProvider><App /></AppProvider></MemoryRouter>);
    expect(await screen.findAllByText(/Đăng nhập/i)).not.toHaveLength(0);
    expect(fetchSpy).not.toHaveBeenCalled();
    expect(connectNotifications).not.toHaveBeenCalled();
  });

  it('khách vào trang chủ: thấy trang giới thiệu, số liệu lấy từ API công khai không kèm token', async () => {
    const stats = { completedSos: 12, activeOrgs: 5, activeCampaigns: 3, totalRaised: 1500000000, updatedAt: '2026-11-10T08:00:00Z' };
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async () => new Response(JSON.stringify(stats), { status: 200, headers: { 'Content-Type': 'application/json' } }));
    render(<MemoryRouter initialEntries={['/']}><AppProvider><App /></AppProvider></MemoryRouter>);
    expect(await screen.findByRole('heading', { name: /Kết nối người cần cứu/ })).toBeTruthy();
    expect(await screen.findByText('1,5 tỷ đ')).toBeTruthy();
    const [url, init] = fetchSpy.mock.calls[0];
    expect(String(url)).toMatch(/\/api\/public\/stats$/);
    expect(init?.headers?.Authorization).toBeUndefined();
    fetchSpy.mockRestore();
  });

  it('đã có token (người dân): khởi động tải hồ sơ rồi đồng bộ đúng các API của vai trò', async () => {
    localStorage.setItem('floodconnect-tokens', JSON.stringify({ access: 'a', refresh: 'r' }));
    const json = (o) => new Response(JSON.stringify(o), { status: 200, headers: { 'Content-Type': 'application/json' } });
    const fetchSpy = vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      if (String(url).endsWith('/api/users/me')) return json({ id: 1, fullName: 'An', role: 'CITIZEN', createdAt: '2026-01-01T00:00:00Z' });
      if (String(url).includes('/api/sos/mine')) return json([]);
      return json({ items: [], last: true, total: 0 });
    });
    render(<MemoryRouter initialEntries={['/']}><AppProvider><App /></AppProvider></MemoryRouter>);
    await vi.waitFor(() => expect(fetchSpy.mock.calls.length).toBeGreaterThanOrEqual(5));
    const paths = fetchSpy.mock.calls.map(([u]) => String(u).replace(/^https?:\/\/[^/]+/, ''));
    expect(paths[0]).toBe('/api/users/me');
    expect(paths).toEqual(expect.arrayContaining(['/api/notifications?page=0&size=30', '/api/sos/mine', '/api/sos/active', '/api/donations/mine']));
    expect(paths.some((p) => p.startsWith('/api/admin') || p.startsWith('/api/org'))).toBe(false);
    await vi.waitFor(() => expect(connectNotifications).toHaveBeenCalledTimes(1));
    localStorage.clear();
  });

  /* Mục #16: chỉ người dân gửi SOS. Admin và tổ chức không có mục "Cứu hộ SOS", mở thẳng /sos thì bị chuyển đi, không thấy form gửi SOS */
  const json = (o) => new Response(JSON.stringify(o), { status: 200, headers: { 'Content-Type': 'application/json' } });
  const loginAs = (profile, lists = []) => {
    localStorage.setItem('floodconnect-tokens', JSON.stringify({ access: 'a', refresh: 'r' }));
    return vi.spyOn(globalThis, 'fetch').mockImplementation(async (url) => {
      const u = String(url);
      if (u.endsWith('/api/users/me')) return json(profile);
      if (u.endsWith('/api/org/me')) return json(profile.organization);
      if (lists.some((p) => u.includes(p))) return json([]);
      return json({ items: [], last: true, total: 0 });
    });
  };

  it('Admin mở /sos: chuyển sang trang điều phối, không có form gửi SOS', async () => {
    const fetchSpy = loginAs({ id: 9, fullName: 'Quản trị viên', role: 'ADMIN', createdAt: '2026-01-01T00:00:00Z' }, ['/api/admin/stats', '/api/admin/sos/heatmap']);
    render(<MemoryRouter initialEntries={['/sos']}><AppProvider><App /></AppProvider></MemoryRouter>);
    expect((await screen.findAllByText('Điều phối SOS')).length).toBeGreaterThan(0);
    expect(screen.queryByText('Cứu hộ SOS')).toBeNull();
    expect(screen.queryByText(/Xác nhận gửi SOS/)).toBeNull();
    fetchSpy.mockRestore(); localStorage.clear();
  });

  it('Tổ chức mở /sos: không có form gửi SOS, không tải "ca SOS của tôi"', async () => {
    const org = { id: 3, name: 'Đội A', orgType: 'VOLUNTEER', status: 'ACTIVE', operatingStatus: 'BUSY', latitude: 16.0544, longitude: 108.2022, documents: [] };
    const fetchSpy = loginAs({ id: 5, fullName: 'Đội A', role: 'RESCUE_ORG', organization: org, createdAt: '2026-01-01T00:00:00Z' }, ['/api/org/sos', '/api/org/campaigns']);
    render(<MemoryRouter initialEntries={['/sos']}><AppProvider><App /></AppProvider></MemoryRouter>);
    expect((await screen.findAllByText('Điều phối cứu hộ')).length).toBeGreaterThan(0);
    expect(screen.queryByText('Cứu hộ SOS')).toBeNull();
    expect(screen.queryByText(/Xác nhận gửi SOS/)).toBeNull();
    const paths = fetchSpy.mock.calls.map(([u]) => String(u).replace(/^https?:\/\/[^/]+/, ''));
    expect(paths).not.toContain('/api/sos/mine');
    fetchSpy.mockRestore(); localStorage.clear();
  });
});
