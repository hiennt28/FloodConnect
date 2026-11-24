import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Admin gốc vô hiệu hóa một Admin: bắt buộc lý do (gửi kèm email cho người bị vô hiệu hóa), không có thời hạn */
export function DisableAdminModal({ name, onClose, onSave }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    const r = await onSave(reason);
    setBusy(false);
    if (r) setError(r);
  };

  return (
    <Modal title="Vô hiệu hóa Admin" onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
      <button className="btn btn-danger" onClick={save} disabled={busy}>Vô hiệu hóa</button>
    </>}>
      <div className="stack-sm">
        <Field label="Lý do" required error={error}>
          <textarea className="input" rows={3} maxLength={500} value={reason} onChange={(e) => { setReason(e.target.value); setError(''); }}
            placeholder="VD: Hết nhiệm kỳ trực điều phối mùa lũ 2026" />
        </Field>
        <span className="muted small">
          {name} sẽ bị đăng xuất khỏi mọi thiết bị, không đăng nhập được và không nhận thông báo SOS cho tới khi bạn mở lại.
          Người đó nhận email kèm lý do.
        </span>
      </div>
    </Modal>
  );
}
