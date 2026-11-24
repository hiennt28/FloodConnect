import { useState } from 'react';
import { Link, useLocation, useSearchParams } from 'react-router-dom';
import { MailCheck } from 'lucide-react';
import { useApp, useResendCooldown } from '@/hooks';
import { Field, OtpInput } from '@/components/common';
import { Shell } from '@/components/layout/AuthShell';

/** Màn hình nhập mã OTP 6 số gửi qua email (thay cho bấm liên kết trước đây). Tới từ: form Đăng ký, hoặc nút
 *  "Nhập mã xác thực" trên trang Đăng nhập (truyền email qua location.state), hoặc truy cập trực tiếp /verify-email?email=... */
export function VerifyEmail() {
  const { auth, toast } = useApp();
  const location = useLocation();
  const [sp] = useSearchParams();
  const [email, setEmail] = useState(location.state?.email || sp.get('email') || '');
  const [code, setCode] = useState('');
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);
  const [done, setDone] = useState(false);
  const cooldown = useResendCooldown(60);

  const submit = async (c) => {
    const otp = c ?? code;
    if (!email.trim()) { setErr('Vui lòng nhập email đã đăng ký.'); return; }
    if (otp.length !== 6) { setErr('Vui lòng nhập đủ 6 số.'); return; }
    setBusy(true);
    const m = await auth.verifyEmail(email, otp);
    setBusy(false);
    if (m) { setErr(m); setCode(''); return; }
    setErr(''); setDone(true);
  };

  const resend = async () => {
    if (!email.trim()) { setErr('Vui lòng nhập email đã đăng ký.'); return; }
    const m = await auth.resendVerification(email.trim());
    if (m) { setErr(m); return; }
    setErr(''); setCode(''); cooldown.start(60); toast('Đã gửi mã xác thực mới');
  };

  if (done) {
    return (
      <Shell title="Xác thực thành công">
        <div className="done-box">
          <MailCheck size={40} />
          <p>Email đã được xác thực. Bạn có thể đăng nhập.</p>
          <Link to="/login" className="btn btn-primary">Đến trang đăng nhập</Link>
        </div>
      </Shell>
    );
  }

  return (
    <Shell title="Nhập mã xác thực" sub="Chúng tôi đã gửi mã gồm 6 số tới email của bạn. Mã có hiệu lực 10 phút.">
      <div className="stack">
        <Field label="Email đã đăng ký"><input className="input" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} /></Field>
        <Field label="Mã xác thực"><OtpInput value={code} onChange={setCode} disabled={busy} onComplete={submit} /></Field>
        {err && <div className="alert alert-danger">{err}</div>}
        <button className="btn btn-primary btn-lg" disabled={busy} onClick={() => submit()}>{busy ? 'Đang xác thực…' : 'Xác thực'}</button>
        <button type="button" className="btn btn-soft" disabled={cooldown.left > 0} onClick={resend}>
          {cooldown.left > 0 ? `Gửi lại mã sau ${cooldown.left}s` : 'Gửi lại mã'}
        </button>
        <Link to="/login" className="small">Quay lại đăng nhập</Link>
      </div>
    </Shell>
  );
}
