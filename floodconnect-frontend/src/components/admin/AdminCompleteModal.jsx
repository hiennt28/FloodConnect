import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Admin xác nhận hoàn thành ca Đã tiếp nhận sau khi liên hệ: người dân đã an toàn nhưng không tự báo được, hoặc tổ chức quên bấm */
export function AdminCompleteModal({ s, onClose, onSave }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    const result = await onSave(reason);
    setBusy(false);
    if (result) setError(result);
  };

  return (
    <Modal title={`Xác nhận hoàn thành ca ${s.code}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Quay lại</button>
      <button className="btn btn-success" disabled={busy} onClick={save}>Xác nhận hoàn thành</button>
    </>}>
      <div className="stack-sm">
        <Field label="Lý do" error={error} required>
          <textarea className="input" rows={3} maxLength={500} value={reason} onChange={(e) => { setReason(e.target.value); setError(''); }} placeholder="VD: Đã gọi người dân, cả nhà đã được đưa đến nơi an toàn" />
        </Field>
        <span className="muted small">Chỉ dùng khi đã liên hệ và chắc chắn người dân đã an toàn. Ca sẽ chuyển sang Hoàn thành (ghi là Admin xác nhận, không tính là tổ chức cứu hộ xong); tổ chức{s.org?.name ? ` ${s.org.name}` : ''} và người dân nhận thông báo kèm lý do.</span>
        <span className="muted small">Nếu tổ chức không làm được mà người dân vẫn cần cứu hộ, đừng dùng chức năng này.</span>
      </div>
    </Modal>
  );
}
