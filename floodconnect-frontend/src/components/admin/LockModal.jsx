import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* ---- dùng chung: popup khóa tài khoản ---- */
export function LockModal({ name, onClose, onLock }) {
  const [months, setMonths] = useState('1');
  const [reason, setReason] = useState('');
  const [err, setErr] = useState('');
  return (
    <Modal title={`Khóa tài khoản: ${name}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-danger" onClick={async () => { const r = await onLock(months, reason); if (r) setErr(r); }}>Xác nhận khóa</button>
    </>}>
      <div className="stack">
        <Field label="Thời gian khóa (tháng)" required error={err}><input className="input" type="number" min={1} step={1} value={months} onChange={(e) => setMonths(e.target.value)} /></Field>
        <Field label="Lý do" required hint="Lý do được gửi kèm thông báo và email cho tài khoản bị khóa."><textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="VD: Đăng tin giả nhiều lần" /></Field>
      </div>
    </Modal>
  );
}
