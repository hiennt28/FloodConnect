import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Người dân báo "Tôi đã an toàn" cho ca Đã tiếp nhận: hỏi lại để tránh bấm nhầm (tổ chức sẽ dừng cứu hộ); ghi chú không bắt buộc */
export function MarkSafeModal({ s, onClose, onSave }) {
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [busy, setBusy] = useState(false);
  const save = async () => {
    setBusy(true);
    const result = await onSave(note);
    setBusy(false);
    if (result) setError(result);
  };

  return (
    <Modal title="Bạn đã an toàn?" onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Quay lại</button>
      <button className="btn btn-success" disabled={busy} onClick={save}>Tôi đã an toàn</button>
    </>}>
      <div className="stack-sm">
        <p>Chỉ xác nhận khi bạn và những người cùng gặp nạn <b>đã an toàn</b> (đã được đơn vị khác đưa đi, nước đã rút…).</p>
        <span className="muted small">Ca {s.code} sẽ chuyển sang Hoàn thành và {s.org?.name ? `tổ chức ${s.org.name}` : 'tổ chức cứu hộ'} được báo dừng cứu hộ. Nếu vẫn cần giúp đỡ, đừng bấm; hãy gọi tổ chức hoặc 112.</span>
        <Field label="Ghi chú (không bắt buộc)" error={error}>
          <textarea className="input" rows={3} maxLength={500} value={note} onChange={(e) => { setNote(e.target.value); setError(''); }} placeholder="VD: Đã được hàng xóm chở đến nhà văn hóa thôn" />
        </Field>
      </div>
    </Modal>
  );
}
