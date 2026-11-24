import { useEffect, useState } from 'react';

/* Re-render định kỳ cho đồng hồ đếm ngược / vị trí mô phỏng */
export function useNow(ms = 1000) {
  const [now, setNow] = useState(Date.now());
  useEffect(() => { const t = setInterval(() => setNow(Date.now()), ms); return () => clearInterval(t); }, [ms]);
  return now;
}
