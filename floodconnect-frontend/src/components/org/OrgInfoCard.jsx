import { useState } from 'react';
import { MapPin, Pencil } from 'lucide-react';
import { useApp } from '@/hooks';
import { Field } from '@/components/common';
import AddressPicker from '@/components/common/AddressPicker';
import MapView from '@/components/map/MapView';
import PinPicker from '@/components/map/PinPicker';
import { OrgCapacityFields } from './OrgCapacityFields';
import { ORG_DESCRIPTION_MAX, ORG_TYPES, orgAddress } from '@/utils';

const HQ_HINT = 'Vị trí này cùng bán kính hoạt động bạn khai báo dùng để gợi ý ca SOS và gửi thông báo ca mới trong vùng bạn phục vụ. '
  + 'Người ngoài chỉ thấy Phường/Xã, Tỉnh của trụ sở.';

/**
 * Thông tin tổ chức trong hồ sơ cá nhân của tài khoản tổ chức: tên, loại hình, địa chỉ, trụ sở trên bản đồ, mô tả, trang web.
 * Tổ chức đã duyệt (không bị khóa) bấm "Sửa thông tin tổ chức" để sửa mô tả, trang web, địa chỉ và kéo ghim trụ sở.
 * Tên và loại tổ chức gắn với hồ sơ Admin đã duyệt nên không tự đổi được. Hồ sơ bị từ chối sửa ở khung "Gửi lại hồ sơ" riêng.
 */
export function OrgInfoCard({ org, locked }) {
  const { profile, toast } = useApp();
  const [form, setForm] = useState(null); // null: đang xem; object: đang sửa
  const [err, setErr] = useState('');
  const [saving, setSaving] = useState(false);
  const approved = org.approval === 'APPROVED';
  const hasHq = org.lat != null && org.lng != null;

  const startEdit = () => {
    setErr('');
    setForm({
      description: org.description || '', website: org.website || '', street: org.street || '',
      cases: org.cases ?? '', radiusKm: org.radiusKm ?? '',
      provinceCode: org.provinceCode || '', wardCode: org.wardCode || '', point: hasHq ? { lat: org.lat, lng: org.lng } : null,
    });
  };
  const save = async () => {
    setSaving(true);
    const r = await profile.updateOrg(form);
    setSaving(false);
    if (r) { setErr(r); return; }
    setForm(null);
    toast('Đã cập nhật thông tin tổ chức');
  };

  /* Giá trị chỉ để xem: chữ thường (không phải ô nhập bị khóa); thiếu dữ liệu thì chữ nhạt */
  const value = (v, empty) => (v ? <dd>{v}</dd> : <dd className="is-empty">{empty}</dd>);
  const canEdit = approved && !locked;

  if (!form) {
    return (
      <>
        <div className="sec-head">
          <h3>Thông tin tổ chức</h3>
          {canEdit && <button className="btn btn-soft btn-sm" onClick={startEdit}><Pencil size={14} /> Sửa</button>}
        </div>
        <dl className="info-kv">
          <dt>Tên tổ chức</dt><dd>{org.name}</dd>
          <dt>Loại hình</dt>{value(ORG_TYPES[org.type]?.label, 'Chưa rõ')}
          <dt>Địa chỉ hoạt động</dt>{value(orgAddress(org), 'Chưa có địa chỉ')}
          <dt>Trang web / fanpage</dt>
          {org.website ? <dd><a href={org.website} target="_blank" rel="noopener noreferrer">{org.website}</a></dd> : <dd className="is-empty">Chưa có</dd>}
        </dl>
        {approved && <span className="lock-hint">Tên và loại tổ chức gắn với hồ sơ đã được duyệt nên không tự đổi được.</span>}
        {approved && locked && <span className="lock-hint">Tổ chức đang bị khóa nên chưa sửa được thông tin tổ chức.</span>}
        <div className="stat-tiles">
          <div className="stat-tile"><b>{org.cases ?? '—'}</b><span>Số ca xử lý cùng lúc</span></div>
          <div className="stat-tile"><b>{org.radiusKm != null ? `${org.radiusKm} km` : '—'}</b><span>Bán kính hoạt động</span></div>
        </div>
        <div className="stack-sm">
          <span className="field-label">Mô tả năng lực</span>
          {org.description
            ? <p className="info-desc">{org.description}</p>
            : <p className="info-desc is-empty">Chưa có mô tả.{canEdit ? ' Hãy bấm Sửa để nêu năng lực, phương tiện: AI dùng mô tả này để gợi ý ca phù hợp.' : ''}</p>}
        </div>
        <Field group label="Trụ sở trên bản đồ" hint={HQ_HINT}>
          {hasHq ? (
            <MapView center={[org.lat, org.lng]} zoom={16} height={220}
              markers={[{ id: 'hq', lat: org.lat, lng: org.lng, kind: 'org', color: '#0e7490', label: 'Trụ sở' }]} />
          ) : <span className="muted small"><MapPin size={14} /> Chưa có vị trí trụ sở.</span>}
        </Field>
      </>
    );
  }

  return (
    <>
      <div className="sec-head"><h3>Sửa thông tin tổ chức</h3></div>
      {approved && <span className="lock-hint">Tên ({org.name}) và loại tổ chức gắn với hồ sơ đã được duyệt nên không tự đổi được.</span>}
          <Field group label="Địa chỉ">
            <AddressPicker provinceCode={form.provinceCode} wardCode={form.wardCode} onChange={(v) => setForm((x) => ({ ...x, ...v }))} />
            <input className="input mt8" placeholder="Thôn/xóm, số nhà, đường" maxLength={255} value={form.street}
              onChange={(e) => setForm({ ...form, street: e.target.value })} />
          </Field>
          <Field group label="Vị trí trụ sở trên bản đồ" hint={`Kéo ghim tới đúng vị trí trụ sở. ${HQ_HINT}`}>
            <PinPicker label="Trụ sở" wardCode={form.wardCode} point={form.point}
              onChange={(point) => setForm((x) => ({ ...x, point }))} onPickWard={(v) => setForm((x) => ({ ...x, ...v }))} />
          </Field>
          <Field label="Mô tả" required hint={`Nêu năng lực và phương tiện (xuồng, y tế, tiếp tế, vận chuyển…): AI dùng để gợi ý ca phù hợp. ${form.description.length}/${ORG_DESCRIPTION_MAX}`}>
            <textarea className="input" rows={4} maxLength={ORG_DESCRIPTION_MAX} value={form.description} onChange={(e) => setForm({ ...form, description: e.target.value })} />
          </Field>
          <OrgCapacityFields value={form} onChange={(v) => setForm((x) => ({ ...x, ...v }))} />
          <Field label="Trang web / fanpage" hint="Không bắt buộc, hiển thị công khai. Để trống là xóa.">
            <input className="input" inputMode="url" placeholder="https://" maxLength={500} value={form.website}
              onChange={(e) => setForm({ ...form, website: e.target.value })} />
          </Field>
          {err && <div className="field-error">{err}</div>}
          <div className="row gap6 card-actions">
            <button className="btn btn-ghost" disabled={saving} onClick={() => { setForm(null); setErr(''); }}>Hủy</button>
            <button className="btn btn-primary" disabled={saving} onClick={save}>{saving ? 'Đang lưu…' : 'Lưu thông tin tổ chức'}</button>
          </div>
    </>
  );
}
