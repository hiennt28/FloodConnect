import { useEffect, useRef, useState } from 'react';

/** Đếm ngược (giây) cho nút "Gửi lại mã": gọi start() ngay sau khi gửi mã để khoá nút cho đến khi hết giờ. */
export function useResendCooldown(seconds = 60) {
  const [left, setLeft] = useState(0);
  const timer = useRef(null);
  useEffect(() => () => clearInterval(timer.current), []);
  const start = (s = seconds) => {
    setLeft(s);
    clearInterval(timer.current);
    timer.current = setInterval(() => {
      setLeft((x) => {
        if (x <= 1) { clearInterval(timer.current); return 0; }
        return x - 1;
      });
    }, 1000);
  };
  return { left, start };
}
