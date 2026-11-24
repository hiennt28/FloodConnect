/* ================= Giao diện sáng / tối =================
   Người dùng chọn: 'light' (sáng), 'dark' (tối) hoặc 'system' (theo cài đặt của máy, mặc định).
   Lựa chọn lưu trong localStorage của trình duyệt; giao diện thực tế ('light' | 'dark') đặt ở <html data-theme>.
   CSS chỉ cần :root[data-theme="dark"] { ...biến màu... } (assets/styles/theme.css).
   index.html có một đoạn script nhỏ làm đúng việc applyTheme() trước khi tải React, để không bị nháy nền trắng. */
export const THEME_KEY = 'floodconnect-theme';
export const THEME_PREFS = ['light', 'dark', 'system'];
const listeners = new Set();

const darkQuery = () => (typeof window !== 'undefined' && window.matchMedia ? window.matchMedia('(prefers-color-scheme: dark)') : null);

export function getThemePref() {
  try {
    const v = localStorage.getItem(THEME_KEY);
    return THEME_PREFS.includes(v) ? v : 'system';
  } catch {
    return 'system'; // trình duyệt chặn bộ nhớ (chế độ riêng tư...): theo máy
  }
}

export const resolveTheme = (pref) => (pref === 'system' ? (darkQuery()?.matches ? 'dark' : 'light') : pref);

/* Giao diện đang hiển thị */
export const currentTheme = () => (typeof document !== 'undefined' && document.documentElement.dataset.theme) || 'light';

export function applyTheme(pref = getThemePref()) {
  document.documentElement.dataset.theme = resolveTheme(pref);
  listeners.forEach((fn) => fn());
}

export function setThemePref(pref) {
  if (!THEME_PREFS.includes(pref)) return;
  try { localStorage.setItem(THEME_KEY, pref); } catch { /* không lưu được thì vẫn đổi trong phiên này */ }
  applyTheme(pref);
}

/* Gọi một lần khi khởi động: áp giao diện và theo dõi khi máy đổi sáng/tối (chỉ có tác dụng với lựa chọn 'system') */
export function initTheme() {
  applyTheme();
  darkQuery()?.addEventListener?.('change', () => { if (getThemePref() === 'system') applyTheme('system'); });
}

/* Cho hook useTheme: React vẽ lại khi giao diện đổi */
export function subscribeTheme(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}

/* Đọc màu từ biến CSS trong theme.css (vd. '--river') cho các thư viện vẽ bằng thuộc tính SVG như recharts,
   vốn không hiểu var(--x). Component gọi cssVar nên dùng useTheme() để vẽ lại khi đổi giao diện. */
export const cssVar = (name, fallback = '') => {
  try {
    return getComputedStyle(document.documentElement).getPropertyValue(name).trim() || fallback;
  } catch {
    return fallback;
  }
};
