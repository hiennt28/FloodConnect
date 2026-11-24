import { useEffect, useRef, useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useApp } from './useApp';

/**
 * Mở ca SOS được chỉ tới bằng ?case=<id> (đường dẫn của thông báo).
 * - Đọc xong thì xóa ?case= khỏi địa chỉ (F5 không mở lại; bấm thông báo khác khi đang ở trang này vẫn mở được ca mới).
 * - tryOpen(id): trang thử mở ca từ dữ liệu đang có, trả true nếu mở được.
 * - Chưa mở được: tải lại một lần (sos.loadCase: vai trò khác Admin đồng bộ lại, rồi tải chi tiết ca và ghim nếu ca đã kết thúc), rồi thử lại;
 *   vẫn không được thì gọi onMissing(id) để trang báo cho người dùng.
 */
export function useCaseParam(tryOpen, onMissing) {
  const { state, sos } = useApp();
  const [sp, setSp] = useSearchParams();
  const [target, setTarget] = useState(null); // { id, retried }
  const loading = useRef(false);

  useEffect(() => {
    const id = sp.get('case');
    if (!id) return;
    setTarget({ id, retried: false });
    setSp((p) => { const n = new URLSearchParams(p); n.delete('case'); return n; }, { replace: true });
  }, [sp, setSp]);

  useEffect(() => {
    if (!target || loading.current) return;
    if (tryOpen(target.id)) { setTarget(null); return; }
    if (target.retried) { onMissing(target.id); setTarget(null); return; }
    loading.current = true;
    Promise.resolve(sos.loadCase(target.id)).finally(() => {
      loading.current = false;
      setTarget((t) => (t && t.id === target.id ? { ...t, retried: true } : t));
    });
  }, [target, state.sos]); // eslint-disable-line react-hooks/exhaustive-deps
}
