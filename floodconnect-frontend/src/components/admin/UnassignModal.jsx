import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Admin hủy phân công ca Đã phân công (tổ chức chưa xác nhận): ca về Chờ xử lý, lý do được gửi cho tổ chức bị hủy */
export function UnassignModal({ s, onClose, onSave }) {
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
    <Modal title={`Hủy phân công ca ${s.code}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Đóng</button>
      <button className="btn btn-danger" onClick={save} disabled={busy}>Hủy phân công</button>
    </>}>
      <div className="stack-sm">
        <Field label="Lý do" error={error} required>
          <textarea className="input" rows={3} maxLength={500} value={reason} onChange={(e) => { setReason(e.target.value); setError(''); }}
            placeholder="VD: Đã gọi, đội báo không có xuồng để tới khu vực này" />
        </Field>
        <span className="muted small">
          Ca sẽ rời khỏi {s.org?.name || 'tổ chức đang được phân công'} và quay về “Chờ xử lý”. Tổ chức nhận thông báo kèm lý do; người dân
          và các tổ chức Sẵn sàng gần đó cũng được báo. Sau đó bạn có thể chỉ định tổ chức khác.
        </span>
      </div>
    </Modal>
  );
}
