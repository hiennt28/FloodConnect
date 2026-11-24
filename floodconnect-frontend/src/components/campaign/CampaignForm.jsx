import { useState } from 'react';
import { useApp } from '@/hooks';
import { Field, MoneyInput, Photo } from '@/components/common';
import { BANKS, campaignStatus, OTHER_BANK, previewOf, toInputDate } from '@/utils';

/* ================= ORG: form tạo / sửa ================= */
export function CampaignForm({ campaign, onClose }) {
  const { campaigns, myOrg, toast } = useApp();
  const st = campaign ? campaignStatus(campaign) : 'NEW';
  const lockedField = st === 'ACTIVE';
  /* Ngân hàng khóa cùng tài khoản khi chiến dịch đang diễn ra; riêng chiến dịch cũ chưa có ngân hàng thì cho bổ sung một lần */
  const lockedBank = lockedField && !!campaign?.bank;
  const [f, setF] = useState(() => {
    const bank = campaign?.bank || '';
    const bankChoice = !bank ? '' : BANKS.includes(bank) ? bank : OTHER_BANK;
    const bankOther = bankChoice === OTHER_BANK ? bank : '';
    return campaign
      ? { ...campaign, bankChoice, bankOther, startStr: toInputDate(campaign.start), endStr: toInputDate(campaign.end), coverFile: null, galleryNew: [], galleryRemove: [] }
      : { name: '', purpose: '', area: '', beneficiaries: '', orgInfo: myOrg.name, target: '', bankChoice, bankOther, accountName: '', accountNo: '', startStr: toInputDate(Date.now()), endStr: '', cover: null, coverFile: null, gallery: [], galleryNew: [], galleryRemove: [] };
  });
  const [busy, setBusy] = useState(false);
  const [err, setErr] = useState('');
  const set = (k) => ({ value: f[k] ?? '', onChange: (e) => setF({ ...f, [k]: e.target.value }) });

  const save = async () => {
    const payload = {
      ...f,
      bank: f.bankChoice === OTHER_BANK ? f.bankOther : f.bankChoice,
      start: new Date(`${f.startStr}T00:00:00`).getTime(),
      end: f.endStr ? new Date(`${f.endStr}T23:59:59`).getTime() : null,
    };
    if (!campaign && payload.start < new Date(new Date().toDateString()).getTime()) { setErr('Thời gian bắt đầu không được ở quá khứ.'); return; }
    delete payload.startStr; delete payload.endStr; delete payload.bankChoice; delete payload.bankOther;
    setBusy(true);
    const r = campaign ? await campaigns.edit(campaign.id, payload) : await campaigns.create(payload);
    setBusy(false);
    if (r) { setErr(r); return; }
    toast(campaign ? 'Đã lưu thay đổi chiến dịch' : 'Tạo chiến dịch thành công');
    onClose();
  };

  return (
    <div className="stack">
      {lockedField && <div className="alert alert-info">Chiến dịch đang diễn ra: không thể đổi số tiền mục tiêu, tài khoản ngân hàng nhận tiền, thời gian bắt đầu. Thời gian kết thúc chỉ được kéo dài thêm.</div>}
      <Field label="Tên chiến dịch" required hint={`${(f.name || '').length}/200`}><input className="input" maxLength={200} {...set('name')} /></Field>
      <div className="grid-2">
        <Field label="Ảnh đại diện" required={!campaign}><input className="input" type="file" accept="image/jpeg,image/png,image/webp" onChange={(e) => { const file = e.target.files[0]; if (file) setF({ ...f, coverFile: file, cover: previewOf(file).url }); }} /></Field>
        <Field label="Hình ảnh minh họa"><input className="input" type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={(e) => setF({ ...f, galleryNew: [...e.target.files].slice(0, 10) })} /></Field>
      </div>
      {(f.cover || (f.gallery || []).length > 0 || f.galleryNew.length > 0) && (
        <div className="thumbs">
          {f.cover && <div className="thumb"><Photo img={{ url: f.cover }} /></div>}
          {(f.gallery || []).filter((g) => !f.galleryRemove.includes(g.id)).map((g) => (
            <div key={g.id} className="thumb"><Photo img={g} /><button type="button" className="thumb-x" aria-label="Bỏ ảnh" onClick={() => setF({ ...f, galleryRemove: [...f.galleryRemove, g.id] })}>×</button></div>
          ))}
          {f.galleryNew.length > 0 && <span className="muted small">+ {f.galleryNew.length} ảnh mới sẽ được tải lên</span>}
        </div>
      )}
      <Field label="Mục đích chiến dịch" required><textarea className="input" rows={2} {...set('purpose')} /></Field>
      <div className="grid-2">
        <Field label="Khu vực được hỗ trợ" required><textarea className="input" rows={2} {...set('area')} /></Field>
        <Field label="Đối tượng được hỗ trợ" required><textarea className="input" rows={2} {...set('beneficiaries')} /></Field>
      </div>
      <Field label="Thông tin tổ chức thực hiện" hint="Để trống thì dùng tên tổ chức."><textarea className="input" rows={2} {...set('orgInfo')} /></Field>
      <div className="grid-3">
        <Field label="Số tiền mục tiêu (VNĐ)" required><MoneyInput disabled={lockedField} value={f.target} onChange={(v) => setF({ ...f, target: v })} /></Field>
        <Field label="Ngân hàng nhận tiền" required>
          <select className="input" disabled={lockedBank} {...set('bankChoice')}>
            <option value="" disabled>Chọn ngân hàng</option>
            {BANKS.map((b) => <option key={b} value={b}>{b}</option>)}
            <option value={OTHER_BANK}>Ngân hàng khác…</option>
          </select>
          {f.bankChoice === OTHER_BANK && <input className="input" maxLength={100} placeholder="Nhập tên ngân hàng" disabled={lockedBank} {...set('bankOther')} />}
        </Field>
        <Field label="Số tài khoản" required><input className="input" inputMode="numeric" disabled={lockedField} value={f.accountNo} onChange={(e) => setF({ ...f, accountNo: e.target.value.replace(/\D/g, '') })} /></Field>
      </div>
      <div className="grid-3">
        <Field label="Tên chủ tài khoản" required><input className="input" maxLength={255} disabled={lockedField} {...set('accountName')} /></Field>
        <Field label="Ngày bắt đầu" required><input className="input" type="date" disabled={lockedField} {...set('startStr')} /></Field>
        <Field label="Ngày kết thúc" required><input className="input" type="date" {...set('endStr')} /></Field>
      </div>
      <span className="muted small">Người ủng hộ thanh toán qua VNPay vào tài khoản của hệ thống FloodConnect; tài khoản ngân hàng trên đây được công khai trên trang chiến dịch là nơi tổ chức nhận tiền.</span>
      {err && <div className="alert alert-danger">{err}</div>}
      <div className="row gap8 end"><button className="btn btn-ghost" onClick={onClose}>Hủy</button><button className="btn btn-primary" disabled={busy} onClick={save}>{campaign ? 'Lưu thay đổi' : 'Tạo mới'}</button></div>
    </div>
  );
}
