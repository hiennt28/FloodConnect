import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '@/hooks';
import { Field } from '@/components/common';
import { Shell } from '@/components/layout/AuthShell';

export function Forgot() {
  const { auth } = useApp();
  const nav = useNavigate();
  const [email, setEmail] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    const m = await auth.requestReset(email.trim());
    setBusy(false);
    if (m) { setErr(m); return; }
    setErr('');
    nav('/reset-password', { state: { email: email.trim() } });
  };

  return (
    <Shell title="Quên mật khẩu" sub="Nhập email đăng ký để nhận mã đặt lại mật khẩu (hiệu lực 10 phút, dùng một lần).">
      <form className="stack" onSubmit={submit}>
        <Field label="Email"><input className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} required /></Field>
        {err && <div className="alert alert-danger">{err}</div>}
        <button className="btn btn-primary btn-lg" disabled={busy}>{busy ? 'Đang gửi…' : 'Gửi mã'}</button>
        <Link to="/login" className="small">Quay lại đăng nhập</Link>
      </form>
    </Shell>
  );
}
