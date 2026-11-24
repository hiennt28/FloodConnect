import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { HeartHandshake, Landmark, MailCheck, Truck, Users } from 'lucide-react';
import { useApp } from '@/hooks';
import { Field, PasswordInput } from '@/components/common';
import { DocSlotsInput } from '@/components/org/DocSlotsInput';
import { emptyDocs, ORG_DESCRIPTION_MAX, ORG_TYPES } from '@/utils';
import AddressPicker from '@/components/common/AddressPicker';
import PinPicker from '@/components/map/PinPicker';
import { Shell } from '@/components/layout/AuthShell';
import { OrgCapacityFields } from '@/components/org/OrgCapacityFields';

const TYPE_ICONS = [Landmark, HeartHandshake, Truck, Users]; // theo thứ tự 4 loại trong ORG_TYPES

function SecHead({ n, title, sub }) {
  return (
    <div className="sec-head">
      <span className="sec-num">{n}</span>
      <div><h4>{title}</h4>{sub && <span className="muted small">{sub}</span>}</div>
    </div>
  );
}

const emptyAcc = { name: '', email: '', phone: '', password: '', confirm: '' };
const emptyOrg = { name: '', type: '', provinceCode: '', wardCode: '', street: '', point: null, description: '', cases: '', radiusKm: '', website: '', docs: emptyDocs() };

export function Register() {
  const { auth } = useApp();
  const nav = useNavigate();
  const [sp] = useSearchParams();
  const [kind, setKind] = useState(sp.get('type') === 'org' ? 'org' : 'citizen');
  const [acc, setAcc] = useState(emptyAcc);
  const [org, setOrg] = useState(emptyOrg);
  const [errors, setErrors] = useState({});
  const [done, setDone] = useState(null);

  const a = (k) => ({ value: acc[k], onChange: (e) => setAcc({ ...acc, [k]: e.target.value }) });
  const o = (k) => ({ value: org[k], onChange: (e) => setOrg({ ...org, [k]: e.target.value }) });
  const t = ORG_TYPES[org.type];

  const [busy, setBusy] = useState(false);
  const submit = async (e) => {
    e.preventDefault();
    setBusy(true);
    const r = kind === 'citizen' ? await auth.registerCitizen(acc) : await auth.registerOrg(acc, org);
    setBusy(false);
    if (r.errors) { setErrors(r.errors); return; }
    setDone({ email: acc.email.trim(), kind });
  };

  if (done) {
    return (
      <Shell title="Kiểm tra email của bạn">
        <div className="done-box">
          <MailCheck size={40} />
          {done.kind === 'org' ? (
            <p>Hồ sơ đã gửi, Admin sẽ xét duyệt sau khi bạn xác thực email. Chúng tôi đã gửi mã OTP 6 số tới <b>{done.email}</b>.</p>
          ) : (
            <p>Chúng tôi đã gửi mã xác thực gồm 6 số tới <b>{done.email}</b>. Mã có hiệu lực 10 phút.</p>
          )}
          <button className="btn btn-primary" onClick={() => nav('/verify-email', { state: { email: done.email } })}>Nhập mã xác thực</button>
          <Link to="/login" className="small">Đến trang đăng nhập</Link>
        </div>
      </Shell>
    );
  }


  return (
    <Shell title="Tạo tài khoản" sub="Chọn loại tài khoản phù hợp." wide={kind === 'org'}>
      <div className="seg seg-full">
        <button className={kind === 'citizen' ? 'on' : ''} onClick={() => { setKind('citizen'); setErrors({}); }}>Người dân</button>
        <button className={kind === 'org' ? 'on' : ''} onClick={() => { setKind('org'); setErrors({}); }}>Tổ chức cứu trợ</button>
      </div>
      <form onSubmit={submit} className="reg-form">
        <section className="reg-sec">
          <SecHead n={1} title={kind === 'org' ? 'Người đại diện' : 'Thông tin tài khoản'} sub={kind === 'org' ? 'Tài khoản đăng nhập của người đại diện tổ chức' : null} />
          <div className="grid-2">
            <Field label="Họ và tên" required error={errors.name}><input className="input" maxLength={255} {...a('name')} /></Field>
            <Field label="Số điện thoại" required error={errors.phone}><input className="input" inputMode="numeric" {...a('phone')} onChange={(e) => setAcc({ ...acc, phone: e.target.value.replace(/\D/g, '') })} /></Field>
          </div>
          <Field label="Email" required error={errors.email}><input className="input" type="email" autoComplete="email" maxLength={255} {...a('email')} /></Field>
          <div className="grid-2">
            <Field label="Mật khẩu" required error={errors.password} hint="8–64 ký tự, gồm chữ và số, không dấu, không dấu cách"><PasswordInput autoComplete="new-password" {...a('password')} /></Field>
            <Field label="Xác nhận mật khẩu" required error={errors.confirm}><PasswordInput autoComplete="new-password" {...a('confirm')} /></Field>
          </div>
        </section>

        {kind === 'org' && (
          <>
            <section className="reg-sec">
              <SecHead n={2} title="Thông tin tổ chức" sub="Thông tin này hiển thị trên hồ sơ công khai của tổ chức" />
              <Field label="Tên tổ chức" required error={errors.orgName}><input className="input" maxLength={255} {...o('name')} /></Field>
              <Field label="Loại tổ chức" required error={errors.type}>
                <div className="radio-cards">
                  {Object.entries(ORG_TYPES).map(([k, v], i) => {
                    const Icon = TYPE_ICONS[i] || Users;
                    return (
                      <label key={k} className={`radio-card ${org.type === k ? 'on' : ''}`}>
                        <input type="radio" name="otype" checked={org.type === k} onChange={() => setOrg({ ...org, type: k, docs: emptyDocs() })} />
                        <span className="rc-ic"><Icon size={20} /></span>
                        <span className="rc-tx"><b>{v.label}</b><span className="muted small">{v.hint}</span></span>
                      </label>
                    );
                  })}
                </div>
              </Field>
              <Field group label="Địa chỉ trụ sở" required error={errors.address}>
                <AddressPicker provinceCode={org.provinceCode} wardCode={org.wardCode} onChange={(v) => setOrg((x) => ({ ...x, ...v }))} />
                <input className="input mt8" placeholder="Thôn/xóm, số nhà, đường" maxLength={255} {...o('street')} />
              </Field>
              {/* Không bắt nhập tọa độ: đặt ghim trên bản đồ. Tọa độ chính xác để AI Matching tính đúng khoảng cách tới ca SOS */}
              <Field group label="Vị trí trụ sở trên bản đồ" required error={errors.location} hint="Dùng để AI gợi ý các ca SOS gần tổ chức của bạn, nên đặt ghim đúng chỗ.">
                <PinPicker label="Trụ sở" wardCode={org.wardCode} point={org.point}
                  onChange={(point) => setOrg((x) => ({ ...x, point }))} onPickWard={(v) => setOrg((x) => ({ ...x, ...v }))} />
              </Field>
              <Field label="Mô tả tổ chức" required error={errors.description}
                hint={`Nêu rõ tổ chức làm được gì (cứu hộ bằng xuồng/ca-nô, sơ cứu y tế, tiếp tế lương thực, vận chuyển…) và phương tiện đang có. AI dùng mô tả này để gợi ý ca phù hợp. ${(org.description || '').length}/${ORG_DESCRIPTION_MAX}`}>
                <textarea className="input" rows={4} maxLength={ORG_DESCRIPTION_MAX} {...o('description')}
                  placeholder="VD: Đội 15 người, có 3 ca-nô, áo phao, 2 y tá sơ cứu. Hoạt động ở các phường ven sông." />
              </Field>
              <OrgCapacityFields value={org} onChange={(v) => setOrg((x) => ({ ...x, ...v }))} errors={errors} />
              <Field label="Trang web / fanpage" error={errors.website} hint="Không bắt buộc. Hiển thị công khai trên hồ sơ tổ chức để người dân kiểm tra, ví dụ https://facebook.com/ten-nhom.">
                <input className="input" inputMode="url" placeholder="https://" {...o('website')} />
              </Field>
            </section>

            {t && (
              <section className="reg-sec">
                <SecHead n={3} title="Hồ sơ xác minh" sub="Admin sẽ xem xét hồ sơ trước khi cấp quyền hoạt động" />
                <DocSlotsInput type={org.type} value={org.docs} onChange={(docs) => setOrg({ ...org, docs })} errors={errors} />
              </section>
            )}
          </>
        )}

        <div className="reg-actions">
          <button className="btn btn-primary btn-lg">{kind === 'org' ? 'Gửi hồ sơ đăng ký' : 'Đăng ký'}</button>
          <span className="small">Đã có tài khoản? <Link to="/login">Đăng nhập</Link></span>
        </div>
      </form>
    </Shell>
  );
}
