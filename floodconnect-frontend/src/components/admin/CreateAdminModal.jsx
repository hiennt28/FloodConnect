import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Admin gốc tạo tài khoản Admin mới: không nhập mật khẩu, người được tạo tự đặt bằng mã gửi tới email.
   onSave(form) trả { ok: true } hoặc { errors: { fullName|email|phone } } để hiện lỗi đúng ô */
export function CreateAdminModal({ onClose, onSave }) {
  const [f, setF] = useState({ fullName: '', email: '', phone: '' });
  const [errors, setErrors] = useState({});
  const [busy, setBusy] = useState(false);
  const set = (k) => (e) => { setF({ ...f, [k]: k === 'phone' ? e.target.value.replace(/\D/g, '') : e.target.value }); setErrors({ ...errors, [k]: '' }); };
  const save = async () => {
    setBusy(true);
    const r = await onSave(f);
    setBusy(false);
    if (r?.errors) setErrors(r.errors);
  };

  return (
    <Modal title="Tạo tài khoản Admin" onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-primary" onClick={save} disabled={busy}>Tạo và gửi mã</button>
    </>}>
      <div className="stack-sm">
        <Field label="Họ và tên" required error={errors.fullName}>
          <input className="input" maxLength={255} value={f.fullName} onChange={set('fullName')} autoComplete="off" />
        </Field>
        <Field label="Email" required error={errors.email}>
          <input className="input" type="email" maxLength={255} value={f.email} onChange={set('email')} autoComplete="off" />
        </Field>
        <Field label="Số điện thoại" required error={errors.phone}>
          <input className="input" inputMode="numeric" maxLength={11} value={f.phone} onChange={set('phone')} autoComplete="off" />
        </Field>
        <span className="muted small">
          Không cần nhập mật khẩu. Hệ thống gửi mã đặt mật khẩu tới email trên; người đó đặt mật khẩu xong là đăng nhập được với quyền
          Admin (không có quyền quản lý Admin).
        </span>
      </div>
    </Modal>
  );
}
