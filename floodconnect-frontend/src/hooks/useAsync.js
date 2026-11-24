import { useCallback, useEffect, useState } from 'react';

/* Tải dữ liệu bằng hàm bất đồng bộ, có thể tải lại */
export function useAsync(fn, deps) {
  const [data, setData] = useState(undefined);
  const reload = useCallback(() => fn().then(setData), deps); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { reload(); }, [reload]);
  return [data, reload];
}
