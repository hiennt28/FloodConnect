// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest';
import { applyTheme, getThemePref, setThemePref, subscribeTheme, THEME_KEY } from './theme';

const mockSystemDark = (dark) => { window.matchMedia = vi.fn(() => ({ matches: dark, addEventListener: vi.fn() })); };

describe('giao diện sáng / tối', () => {
  afterEach(() => { localStorage.clear(); delete document.documentElement.dataset.theme; });

  it('mặc định theo máy; giá trị lạ trong bộ nhớ cũng coi như theo máy', () => {
    expect(getThemePref()).toBe('system');
    localStorage.setItem(THEME_KEY, 'tim');
    expect(getThemePref()).toBe('system');
  });

  it('theo máy: máy đang tối thì giao diện tối, máy sáng thì sáng', () => {
    mockSystemDark(true);
    applyTheme('system');
    expect(document.documentElement.dataset.theme).toBe('dark');
    mockSystemDark(false);
    applyTheme('system');
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('chọn tối: lưu lựa chọn, đặt data-theme và báo cho component đang theo dõi', () => {
    mockSystemDark(false);
    const fn = vi.fn();
    const off = subscribeTheme(fn);
    setThemePref('dark');
    expect(localStorage.getItem(THEME_KEY)).toBe('dark');
    expect(document.documentElement.dataset.theme).toBe('dark');
    expect(fn).toHaveBeenCalledTimes(1);
    off();
    setThemePref('light');
    expect(fn).toHaveBeenCalledTimes(1);
    expect(document.documentElement.dataset.theme).toBe('light');
  });

  it('bỏ qua lựa chọn không hợp lệ', () => {
    setThemePref('blue');
    expect(localStorage.getItem(THEME_KEY)).toBeNull();
  });
});
