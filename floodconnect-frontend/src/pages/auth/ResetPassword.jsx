import { useState } from 'react';
import { useLocation, useNavigate, useSearchParams } from 'react-router-dom';
import { useApp, useResendCooldown } from '@/hooks';
import { Field, OtpInput, PasswordInput } from '@/components/common';
import { Shell } from '@/components/layout/AuthShell';
import { PASSWORD_MSG, PASSWORD_RULE } from '@/utils';

/** Đặt lại mật khẩu bằng mã OTP 6 số (thay cho liên kết trước đây). Tới từ trang Quên mật khẩu (email qua state)
 *  hoặc truy cập trực tiếp /reset-password?email=... */
export function ResetPassword() {
  const { auth, toast } = useApp();
  const nav = useNavigate();
  const location = useLocation();
  const [sp] = useSearchParams();
  const [email, setEmail] = useState(location.state?.email || sp.get('email') || '');
  const [code, setCode] = useState('');
  const [pw, setPw] = useState('');
  const [cf, setCf] = useState('');
  const [err, setErr] = useState('');
  const [fe, setFe] = useState({}); // lỗi theo ô mật khẩu
  const [busy, setBusy] = useState(false);
  const cooldown = useResendCooldown(60);

  const submit = async (e) => {
    e.preventDefault();
    if (!email.trim()) { setErr('Vui lòng nhập email đã đăng ký.'); return; }
    if (code.length !== 6) { setErr('Vui lòng nhập đủ 6 số mã xác thực.'); return; }
    // Kiểm tra mật khẩu trước khi gửi: lỗi ở đây không tốn một lần thử mã (backend cũng kiểm tra mật khẩu trước mã)
    const e2 = {};
    if (!PASSWORD_RULE(pw)) e2.pw = PASSWORD_MSG;
    if (pw !== cf) e2.cf = 'Mật khẩu xác nhận không khớp.';
    setFe(e2);
    if (Object.keys(e2).length) { setErr(''); return; }
    setBusy(true);
    const m = await auth.resetPassword(email, code, pw, cf);
    setBusy(false);
    if (m) { setErr(m); return; }
    toast('Đổi mật khẩu thành công');
    nav('/login');
  };

  const resend = async () => {
    if (!email.trim()) { setErr('Vui lòng nhập email đã đăng ký.'); return; }
    const m = await auth.requestReset(email.trim());
    if (m) { setErr(m); return; }
    setErr(''); setCode(''); cooldown.start(60); toast('Đã gửi lại mã');
  };

  return (
    <Shell title="Đặt lại mật khẩu" sub="Nhập mã xác thực đã gửi tới email của bạn và mật khẩu mới.">
      <form className="stack" onSubmit={submit}>
        <Field label="Email đã đăng ký"><input className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Field label="Mã xác thực"><OtpInput value={code} onChange={setCode} disabled={busy} /></Field>
        {/* new-password: trình duyệt gợi ý tạo mật khẩu mới thay vì tự điền mật khẩu đã lưu */}
        <Field label="Mật khẩu mới" hint="8–64 ký tự, gồm chữ và số, không dấu, không dấu cách" error={fe.pw}><PasswordInput autoComplete="new-password" value={pw} onChange={(e) => setPw(e.target.value)} /></Field>
        <Field label="Xác nhận mật khẩu" error={fe.cf}><PasswordInput autoComplete="new-password" value={cf} onChange={(e) => setCf(e.target.value)} /></Field>
        {err && <div className="alert alert-danger">{err}</div>}
        <button className="btn btn-primary btn-lg" disabled={busy}>{busy ? 'Đang cập nhật…' : 'Cập nhật mật khẩu'}</button>
        <button type="button" className="btn btn-soft" disabled={cooldown.left > 0} onClick={resend}>
          {cooldown.left > 0 ? `Gửi lại mã sau ${cooldown.left}s` : 'Gửi lại mã'}
        </button>
      </form>
    </Shell>
  );
}
