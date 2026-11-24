import { useSyncExternalStore } from 'react';
import { currentTheme, getThemePref, setThemePref, subscribeTheme } from '@/utils/theme';

/* pref: lựa chọn của người dùng ('light' | 'dark' | 'system'); theme: giao diện đang hiển thị ('light' | 'dark').
   Component nào đọc màu bằng cssVar() (biểu đồ) gọi hook này để vẽ lại khi đổi giao diện. */
const snapshot = () => `${getThemePref()}|${currentTheme()}`;

export function useTheme() {
  const [pref, theme] = useSyncExternalStore(subscribeTheme, snapshot, () => 'system|light').split('|');
  return { pref, theme, setPref: setThemePref };
}
