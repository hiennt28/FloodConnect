import { useEffect, useState } from 'react';
import { useSearchParams } from 'react-router-dom';

/**
 * Đọc tham số ?name= một lần (đường dẫn của thông báo, vd. ?comment=55) rồi xóa khỏi địa chỉ, để F5 không lặp lại.
 * Trả về { value, key } hoặc null; key đổi mỗi lần đọc nên bấm lại cùng thông báo vẫn kích hoạt lại (cuộn, làm nổi bật).
 */
export function useOnceParam(name) {
  const [sp, setSp] = useSearchParams();
  const [hit, setHit] = useState(null);
  useEffect(() => {
    const value = sp.get(name);
    if (!value) return;
    setHit({ value, key: Date.now() });
    setSp((p) => { const n = new URLSearchParams(p); n.delete(name); return n; }, { replace: true });
  }, [sp, setSp, name]);
  return hit;
}
