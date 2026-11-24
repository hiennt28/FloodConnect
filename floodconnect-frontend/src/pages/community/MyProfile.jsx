import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Camera, Crosshair, MapPinOff, Pencil, Trash2 } from 'lucide-react';
import { DocButton } from '@/components/org/DocButton';
import { useApp } from '@/hooks';
import PostCard from '@/components/post/PostCard';
import { AvatarZoom, Confirm, Empty, Field, PasswordInput, Sev, Tabs, Tag } from '@/components/common';
import AddressPicker from '@/components/common/AddressPicker';
import PinPicker from '@/components/map/PinPicker';
import { DocSlotsInput } from '@/components/org/DocSlotsInput';
import { emptyDocs, fmtDate, ORG_DESCRIPTION_MAX, ORG_TYPES } from '@/utils';
import { OrgInfoCard } from '@/components/org/OrgInfoCard';
import { OrgCapacityFields } from '@/components/org/OrgCapacityFields';

/* ================= MY PROFILE ================= */
export function MyProfile() {
  const { state, me, myOrg, profile, auth, toast, isLocked } = useApp();
  const nav = useNavigate();
  const isOrg = !!myOrg;
  const { load } = useApp();
  const [f, setF] = useState({ name: me.name, phone: me.phone || '' });
  const [avatarPreview, setAvatarPreview] = useState(null); // ảnh đang tải lên, hiện ngay trước khi server trả về
  const [confirmAvatarOff, setConfirmAvatarOff] = useState(false);
  const [removingAvatar, setRemovingAvatar] = useState(false);
  const [pw, setPw] = useState({ cur: '', next: '', confirm: '' });
  const [err, setErr] = useState('');
  const [pwErr, setPwErr] = useState('');
  const [orgEdit, setOrgEdit] = useState(myOrg ? { name: myOrg.name, description: myOrg.description || '', street: myOrg.street, provinceCode: myOrg.provinceCode || '', wardCode: myOrg.wardCode || '', point: myOrg.lat != null ? { lat: myOrg.lat, lng: myOrg.lng } : null, website: myOrg.website || '', cases: myOrg.cases ?? '', radiusKm: myOrg.radiusKm ?? '', docs: emptyDocs() } : null);
  const [editing, setEditing] = useState(false);
  const mySos = state.sos.filter((s) => s.mine);
  const donated = state.donations.filter((d) => d.status === 'SUCCESS');
  const followers = state.profiles[me.id]?.followers ?? 0;
  useEffect(() => { load.profile(me.id); load.userPosts(me.id); }, [me.id]); // eslint-disable-line react-hooks/exhaustive-deps
  const posts = useMemo(() => state.posts.filter((p) => p.authorId === me.id && p.status === 'VISIBLE').sort((a, b) => b.createdAt - a.createdAt), [state.posts, me.id]);
  const [tab, setTab] = useState('info');
  /* Quyền vị trí trình duyệt đang cấp cho trang: 'granted' | 'denied' | 'prompt'; null khi trình duyệt không hỗ trợ Permissions API.
     Trang web không tự thu hồi được quyền này, chỉ dùng để phân biệt "bạn đã tắt" với "trình duyệt đang chặn" */
  const [browserGps, setBrowserGps] = useState(null);
  const [confirmGpsOff, setConfirmGpsOff] = useState(false);
  useEffect(() => {
    let status = null;
    let alive = true;
    const sync = () => { if (alive && status) setBrowserGps(status.state); };
    navigator.permissions?.query({ name: 'geolocation' }).then((s) => { status = s; sync(); s.addEventListener('change', sync); }).catch(() => {});
    return () => { alive = false; status?.removeEventListener('change', sync); };
  }, []);

  const pickAvatar = async (file) => {
    if (!file || avatarPreview) return;
    const url = URL.createObjectURL(file);
    setAvatarPreview(url);
    const r = await profile.updateAvatar(file);
    setAvatarPreview(null);
    URL.revokeObjectURL(url);
    toast(r || (isOrg ? 'Đã cập nhật logo tổ chức' : 'Đã cập nhật ảnh đại diện'), r ? 'err' : 'ok');
  };

  const removeAvatar = async () => {
    setRemovingAvatar(true);
    const r = await profile.removeAvatar();
    setRemovingAvatar(false);
    toast(r || (isOrg ? 'Đã xóa logo tổ chức' : 'Đã xóa ảnh đại diện'), r ? 'err' : 'ok');
  };
  const avatarWord = isOrg ? 'logo tổ chức' : 'ảnh đại diện';

  const gps = () => {
    if (!navigator.geolocation) { toast('Trình duyệt không hỗ trợ định vị', 'err'); return; }
    navigator.geolocation.getCurrentPosition(
      async (p) => { const r = await profile.setGps('GRANTED', +p.coords.latitude.toFixed(5), +p.coords.longitude.toFixed(5)); toast(r || 'Đã cập nhật vị trí', r ? 'err' : 'ok'); },
      async (e) => {
        // Chỉ khi bị từ chối quyền (code 1) mới ghi nhận là chặn; hết thời gian chờ / chưa xác định được vị trí thì giữ nguyên trạng thái
        if (e.code === 1) { await profile.setGps('DENIED'); toast('Trình duyệt đang chặn quyền vị trí của trang này', 'err'); }
        else toast('Không lấy được vị trí hiện tại, vui lòng thử lại', 'err');
      },
    );
  };
  /* Tắt trong ứng dụng: backend xóa tọa độ đã lưu và không dùng vị trí nữa (quyền của trình duyệt vẫn giữ nguyên) */
  const gpsOff = async () => { const r = await profile.setGps('DENIED'); toast(r || 'Đã tắt chia sẻ vị trí và xóa vị trí đã lưu', r ? 'err' : 'ok'); };
  const gpsBlocked = browserGps === 'denied';
  const gpsTag = me.gps === 'GRANTED' ? { tone: 'green', label: 'Đang bật' }
    : gpsBlocked ? { tone: 'red', label: 'Trình duyệt đang chặn' }
      : me.gps === 'DENIED' ? { tone: 'gray', label: 'Đã tắt' } : { tone: 'gray', label: 'Chưa bật' };

  return (
    /* Tổ chức: trang rộng hơn để tab Thông tin chia hai cột (bài viết vẫn ở khung hẹp bên dưới) */
    <div className={`${isOrg ? 'profile-wide' : 'narrow'} stack`}>
      <div className="card profile-hero">
        {/* Tổ chức: ảnh đại diện của tài khoản là logo tổ chức (không phải thông tin pháp lý nên đổi được cả khi đã duyệt) */}
        <div className={`avatar-edit ${avatarPreview ? 'uploading' : ''}`}>
          <AvatarZoom user={{ ...me, name: isOrg ? myOrg.name : me.name, avatar: avatarPreview || me.avatar }} size={104} title={isOrg ? 'Logo tổ chức' : 'Ảnh đại diện'} />
          <label className="avatar-cam" title={isOrg ? 'Đổi logo tổ chức' : 'Đổi ảnh đại diện'} aria-label={isOrg ? 'Đổi logo tổ chức' : 'Đổi ảnh đại diện'}>
            <Camera size={16} />
            <input hidden type="file" accept="image/jpeg,image/png" disabled={!!avatarPreview} onChange={(e) => { pickAvatar(e.target.files[0]); e.target.value = ''; }} />
          </label>
        </div>
        <div className="grow">
          <h1>{isOrg ? myOrg.name : me.name}</h1>
          <div className="muted">{isOrg ? `Tổ chức • ${ORG_TYPES[myOrg.type].label}` : `${posts.length} bài đăng • ${followers} người theo dõi • ${donated.length} lần đóng góp`}</div>
          <Link to={`/users/${me.id}`} className="small">Xem trang công khai</Link>
          <div className="muted small">{avatarPreview ? 'Đang tải ảnh lên…' : `Bấm biểu tượng máy ảnh để đổi ${avatarWord} (JPG/PNG, tối đa 5 MB).`}</div>
          {/* Chỉ hiện khi đang có ảnh; xóa xong hiện ảnh mặc định là chữ cái đầu của tên */}
          {me.avatar && !avatarPreview && (
            <button className="btn btn-ghost btn-sm avatar-remove" disabled={removingAvatar} onClick={() => setConfirmAvatarOff(true)}>
              <Trash2 size={14} /> {removingAvatar ? 'Đang xóa…' : `Xóa ${avatarWord}`}
            </button>
          )}
        </div>
      </div>
      {/* Nhật ký SOS chỉ có ở tài khoản người dân (chỉ người dân gửi được SOS) */}
      <Tabs value={tab} onChange={setTab} tabs={[{ value: 'info', label: 'Thông tin' }, { value: 'posts', label: 'Bài viết', count: posts.length },
        ...(me.role === 'CITIZEN' ? [{ value: 'sos', label: 'Nhật ký SOS', count: mySos.length }] : [])]} />

      {tab === 'info' && (
        /* Tổ chức: cột trái rộng là thẻ Thông tin tổ chức; cột phải là các thẻ Người đại diện, GPS, Đổi mật khẩu (hai cột cân hơn) */
        <div className={isOrg ? 'profile-grid' : 'grid-2 top'}>
          <div className="card pad stack">
            {isOrg ? (
              /* Tổ chức đã duyệt: xem trụ sở trên bản đồ, sửa mô tả, trang web, địa chỉ, ghim trụ sở. Hồ sơ bị từ chối sửa ở khung bên phải */
              <OrgInfoCard org={myOrg} locked={isLocked(me)} />
            ) : (
              <>
                <h3>Thông tin cá nhân</h3>
                <Field label="Họ và tên"><input className="input" maxLength={255} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} disabled={!editing} /></Field>
                <Field label="Email" hint="Email không thể tự thay đổi."><input className="input" value={me.email} disabled /></Field>
                <Field label="Số điện thoại" hint="Dùng cho liên lạc khẩn cấp"><input className="input" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value.replace(/\D/g, '') })} disabled={!editing} /></Field>
                {editing && err && <div className="field-error">{err}</div>}
                {editing ? (
                  <div className="row gap6">
                    <button className="btn btn-ghost" onClick={() => { setF({ name: me.name, phone: me.phone || '' }); setErr(''); setEditing(false); }}>Hủy</button>
                    <button className="btn btn-primary" onClick={async () => { const r = await profile.update(f); if (r) setErr(r); else { setErr(''); setEditing(false); toast('Đã lưu hồ sơ'); } }}>Lưu thay đổi</button>
                  </div>
                ) : <button className="btn btn-primary" onClick={() => setEditing(true)}>Chỉnh sửa</button>}
              </>
            )}
          </div>
          <div className="stack">
            {isOrg && (
              <div className="card pad stack">
                <div className="sec-head">
                  <h3>{editing ? 'Sửa người đại diện' : 'Người đại diện'}</h3>
                  {!editing && <button className="btn btn-soft btn-sm" onClick={() => setEditing(true)}><Pencil size={14} /> Sửa</button>}
                </div>
                {!editing ? (
                  <>
                    <dl className="info-kv">
                      <dt>Họ và tên</dt><dd>{me.name}</dd>
                      <dt>Email</dt><dd>{me.email}</dd>
                      <dt>Số điện thoại</dt>{me.phone ? <dd>{me.phone}</dd> : <dd className="is-empty">Chưa có</dd>}
                    </dl>
                    <span className="lock-hint">Email không thể tự thay đổi. Số điện thoại dùng để người dân liên hệ qua điện thoại / Zalo.</span>
                  </>
                ) : (
                  <>
                    <Field label="Họ và tên"><input className="input" maxLength={255} value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} /></Field>
                    <Field label="Số điện thoại" hint="Dùng để người dân liên hệ qua điện thoại / Zalo"><input className="input" inputMode="numeric" value={f.phone} onChange={(e) => setF({ ...f, phone: e.target.value.replace(/\D/g, '') })} /></Field>
                    <span className="lock-hint">Email {me.email} không thể tự thay đổi.</span>
                    {err && <div className="field-error">{err}</div>}
                    <div className="row gap6 card-actions">
                      <button className="btn btn-ghost" onClick={() => { setF({ name: me.name, phone: me.phone || '' }); setErr(''); setEditing(false); }}>Hủy</button>
                      <button className="btn btn-primary" onClick={async () => { const r = await profile.update(f); if (r) setErr(r); else { setErr(''); setEditing(false); toast('Đã lưu thông tin người đại diện'); } }}>Lưu thay đổi</button>
                    </div>
                  </>
                )}
              </div>
            )}
            <div className="card pad stack">
              <h3>{isOrg ? 'Vị trí hiện tại của tài khoản (GPS)' : 'Quyền vị trí (GPS)'}</h3>
              <div className="row between wrap gap8">
                <span>Trạng thái: <Tag tone={gpsTag.tone}>{gpsTag.label}</Tag></span>
                {me.gps === 'GRANTED' ? (
                  <div className="row gap6 wrap">
                    <button className="btn btn-soft btn-sm" onClick={gps}><Crosshair size={15} /> Cập nhật vị trí</button>
                    <button className="btn btn-ghost btn-sm" onClick={() => setConfirmGpsOff(true)}><MapPinOff size={15} /> Tắt chia sẻ vị trí</button>
                  </div>
                ) : <button className="btn btn-soft btn-sm" onClick={gps}><Crosshair size={15} /> Bật chia sẻ vị trí</button>}
              </div>
              {me.lat && <span className="muted small">Vị trí đã lưu: {me.lat.toFixed(4)}, {me.lng.toFixed(4)}</span>}
              <span className="muted small">{isOrg ? 'Đây là vị trí của thiết bị đang đăng nhập, dùng cho khối "Tình hình bán kính 5 km" trên bảng tin. Không thay đổi vị trí trụ sở (dùng để gợi ý ca và gửi thông báo ca mới); trụ sở xem và sửa ở mục Thông tin tổ chức.' : 'Vị trí chỉ dùng cho bản đồ, bán kính sự cố và SOS.'}</span>
              {gpsBlocked && <span className="small">Để bật lại, mở biểu tượng ổ khóa trên thanh địa chỉ trình duyệt → Vị trí → Cho phép, rồi bấm “Bật chia sẻ vị trí”.</span>}
              {!gpsBlocked && browserGps === null && me.gps === 'DENIED' && <span className="muted small">Nếu bấm “Bật chia sẻ vị trí” không có tác dụng, hãy kiểm tra quyền vị trí ở biểu tượng ổ khóa trên thanh địa chỉ trình duyệt.</span>}
            </div>
            {confirmGpsOff && <Confirm title="Tắt chia sẻ vị trí?" okText="Tắt chia sẻ" danger onClose={() => setConfirmGpsOff(false)} onOk={gpsOff}
              text="Hệ thống sẽ xóa vị trí đã lưu và không dùng vị trí của bạn cho bản đồ, khối “Tình hình bán kính 5 km” và khoảng cách tới các ca SOS nữa. Khi gửi SOS, bạn vẫn lấy được vị trí cho riêng yêu cầu đó. Quyền vị trí trình duyệt đã cấp cho trang này vẫn còn; muốn chặn hẳn, hãy đổi trong cài đặt trình duyệt." />}
            <div className="card pad stack">
              <h3>Đổi mật khẩu</h3>
              <PasswordInput placeholder="Mật khẩu hiện tại" autoComplete="current-password" value={pw.cur} onChange={(e) => setPw({ ...pw, cur: e.target.value })} />
              <PasswordInput placeholder="Mật khẩu mới" autoComplete="new-password" value={pw.next} onChange={(e) => setPw({ ...pw, next: e.target.value })} />
              <PasswordInput placeholder="Xác nhận mật khẩu mới" autoComplete="new-password" value={pw.confirm} onChange={(e) => setPw({ ...pw, confirm: e.target.value })} />
              {pwErr && <div className="field-error">{pwErr}</div>}
              <button className="btn btn-ghost" onClick={async () => { const r = await profile.changePassword(pw.cur, pw.next, pw.confirm); if (r) { setPwErr(r); return; }
                // Backend thu hồi mọi phiên đăng nhập khi đổi mật khẩu: đăng xuất luôn thay vì để phiên này hết hạn giữa chừng
                auth.logout(); nav('/login'); toast('Đã đổi mật khẩu. Vui lòng đăng nhập lại bằng mật khẩu mới.'); }}>Đổi mật khẩu</button>
            </div>
            {myOrg && myOrg.backendStatus === 'REJECTED' && orgEdit && (
              <div className="card pad stack">
                <h3>Hồ sơ tổ chức bị từ chối</h3>
                <div className="alert alert-danger">Lý do: {myOrg.rejectReason}</div>
                <Field label="Tên tổ chức"><input className="input" maxLength={255} value={orgEdit.name} onChange={(e) => setOrgEdit({ ...orgEdit, name: e.target.value })} /></Field>
                <Field group label="Địa chỉ"><AddressPicker provinceCode={orgEdit.provinceCode} wardCode={orgEdit.wardCode} onChange={(v) => setOrgEdit((x) => ({ ...x, ...v }))} /><input className="input mt8" placeholder="Thôn/xóm, số nhà, đường" maxLength={255} value={orgEdit.street} onChange={(e) => setOrgEdit({ ...orgEdit, street: e.target.value })} /></Field>
                <Field group label="Vị trí trụ sở trên bản đồ" hint="Đổi Phường/Xã thì kéo ghim tới vị trí trụ sở mới.">
                  <PinPicker label="Trụ sở" wardCode={orgEdit.wardCode} point={orgEdit.point}
                    onChange={(point) => setOrgEdit((x) => ({ ...x, point }))} onPickWard={(v) => setOrgEdit((x) => ({ ...x, ...v }))} />
                </Field>
                <Field label="Mô tả" required hint="Nêu năng lực và phương tiện (xuồng, y tế, tiếp tế, vận chuyển…): AI dùng để gợi ý ca phù hợp."><textarea className="input" rows={3} maxLength={ORG_DESCRIPTION_MAX} value={orgEdit.description} onChange={(e) => setOrgEdit({ ...orgEdit, description: e.target.value })} /></Field>
                <OrgCapacityFields value={orgEdit} onChange={(v) => setOrgEdit((x) => ({ ...x, ...v }))} />
                <Field label="Trang web / fanpage" hint="Không bắt buộc, hiển thị công khai. Để trống là xóa."><input className="input" inputMode="url" placeholder="https://" value={orgEdit.website} onChange={(e) => setOrgEdit({ ...orgEdit, website: e.target.value })} /></Field>
                {myOrg.docs?.length > 0 && (
                  <div className="stack-sm">
                    <b className="small">Tệp đã nộp</b>
                    {myOrg.docs.map((d, i) => <DocButton key={d.id} docId={d.id} size={14}>{d.label || `Tệp ${i + 1}`}</DocButton>)}
                  </div>
                )}
                <DocSlotsInput type={myOrg.type} value={orgEdit.docs} onChange={(docs) => setOrgEdit({ ...orgEdit, docs })} keepOld />
                <button className="btn btn-primary" onClick={async () => { const r = await profile.resubmitOrg(orgEdit); toast(r || 'Đã gửi lại hồ sơ xét duyệt', r ? 'err' : 'ok'); }}>Gửi lại hồ sơ</button>
              </div>
            )}
          </div>
        </div>
      )}
      {tab === 'posts' && (
        <div className="narrow stack">
          {posts.length ? posts.map((p) => <PostCard key={p.id} post={p} />) : <Empty title="Bạn chưa có bài viết nào" />}
        </div>
      )}
      {tab === 'sos' && me.role === 'CITIZEN' && (
        <div className="card list">
          {mySos.length === 0 && <div className="pad muted">Chưa có ca SOS nào.</div>}
          {mySos.map((s) => (
            <Link key={s.id} to="/sos" className="list-row">
              <Tag tone="blue">{s.code}</Tag><span className="grow">{s.desc}</span><Sev level={s.severity} /><span className="muted small">{fmtDate(s.createdAt)}</span>
            </Link>
          ))}
        </div>
      )}
      {/* Ngoài các tab: nút xóa ảnh nằm ở phần đầu trang, dùng được ở mọi tab */}
      {confirmAvatarOff && <Confirm title={`Xóa ${avatarWord}?`} okText="Xóa ảnh" danger onClose={() => setConfirmAvatarOff(false)} onOk={removeAvatar}
        text={`Trang cá nhân sẽ hiện chữ cái đầu của ${isOrg ? 'tên tổ chức' : 'tên bạn'} thay cho ảnh. Bạn có thể tải ảnh mới bất cứ lúc nào.`} />}
    </div>
  );
}
