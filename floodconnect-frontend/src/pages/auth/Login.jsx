import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useApp } from '@/hooks';
import { Field, PasswordInput } from '@/components/common';
import { Shell } from '@/components/layout/AuthShell';

const clock = (s) => `${Math.floor(s / 60)}:${String(s % 60).padStart(2, '0')}`;

export function Login() {
  const { auth, toast } = useApp();
  const nav = useNavigate();
  const [id, setId] = useState('');
  const [pw, setPw] = useState('');
  const [err, setErr] = useState('');
  const [unverified, setUnverified] = useState(null);

  const [busy, setBusy] = useState(false);

  /* Bị khóa đăng nhập: đếm ngược theo mốc thời gian kết thúc (không trôi khi tab bị trình duyệt làm chậm) */
  const [lockUntil, setLockUntil] = useState(0);
  const [now, setNow] = useState(() => Date.now());
  const lockLeft = lockUntil ? Math.max(0, Math.ceil((lockUntil - now) / 1000)) : 0;
  useEffect(() => {
    if (!lockUntil) return undefined;
    setNow(Date.now());
    const t = setInterval(() => {
      const n = Date.now();
      setNow(n);
      if (n >= lockUntil) { clearInterval(t); setLockUntil(0); }
    }, 250);
    return () => clearInterval(t);
  }, [lockUntil]);

  const submit = async (e) => {
    e.preventDefault();
    if (lockLeft > 0) return;
    setBusy(true);
    const r = await auth.login(id, pw);
    setBusy(false);
    if (r && r.locked) { setErr(''); setUnverified(null); setLockUntil(Date.now() + r.locked * 1000); return; }
    if (r && r.unverified) { setUnverified(r.unverified); setErr('Tài khoản chưa xác thực email.'); return; }
    if (r) { setErr(r); return; }
    toast('Đăng nhập thành công');
    nav('/');
  };

  const goVerify = () => {
    if (!unverified.includes('@')) { setErr('Hãy nhập email (không dùng số điện thoại) để xác thực tài khoản.'); return; }
    nav('/verify-email', { state: { email: unverified } });
  };

  return (
    <Shell title="Đăng nhập" sub="Dùng email hoặc số điện thoại đã đăng ký.">
      <form onSubmit={submit} className="stack">
        <Field label="Email hoặc số điện thoại"><input className="input" value={id} onChange={(e) => setId(e.target.value)} autoComplete="username" /></Field>
        <Field label="Mật khẩu"><PasswordInput value={pw} onChange={(e) => setPw(e.target.value)} autoComplete="current-password" /></Field>
        {lockLeft > 0 && (
          <div className="alert alert-danger">
            Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau{' '}
            <strong style={{ fontVariantNumeric: 'tabular-nums' }}>{clock(lockLeft)}</strong>.
          </div>
        )}
        {err && lockLeft === 0 && <div className="alert alert-danger">{err}
          {unverified && <button type="button" className="link" onClick={goVerify}> Nhập mã xác thực</button>}
        </div>}
        <button className="btn btn-primary btn-lg" disabled={busy || lockLeft > 0}>{busy ? 'Đang đăng nhập…' : 'Đăng nhập'}</button>
        <div className="row between small">
          <Link to="/forgot">Quên mật khẩu?</Link>
          <span>Chưa có tài khoản? <Link to="/register">Đăng ký</Link></span>
        </div>
      </form>
      <Link to="/feed" className="small">Xem bảng tin với tư cách khách</Link>
    </Shell>
  );
}
