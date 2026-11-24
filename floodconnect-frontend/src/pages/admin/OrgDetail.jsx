import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft, Lock, Unlock } from 'lucide-react';
import { DocButton } from '@/components/org/DocButton';
import { useApp, useAsync } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { Confirm, Empty, Field, Modal, SkeletonDetail } from '@/components/common';
import { docSlotLabel, docSlotsOf, fmtDate, fmtDateTime, ORG_TYPES, requiredDocSlots } from '@/utils';
import { LockModal } from '@/components/admin/LockModal';
import MapView from '@/components/map/MapView';
import { orgStatusTag } from '@/components/admin/orgStatusTag';

/* Trang chi tiết tổ chức: duyệt hồ sơ (chưa duyệt) hoặc xem vi phạm, khóa/khôi phục (đã duyệt) */
export function OrgDetail() {
  const { id } = useParams();
  const [sp] = useSearchParams();
  const nav = useNavigate();
  const { admin, toast } = useApp();
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState('');
  const [locking, setLocking] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const [v, reload] = useAsync(() => admin.orgDetail(id), [id]);
  const from = sp.get('from');
  const back = `/admin/orgs${from ? `?tab=${from}` : ''}`;
  if (v === undefined) return <SkeletonDetail label="Đang tải hồ sơ tổ chức…" />;
  if (!v) return <Empty title="Không tìm thấy tổ chức"><Link to="/admin/orgs">Về Quản lý tổ chức</Link></Empty>;

  const o = v.organization;
  const rep = v.representative;
  const reviewing = ['PENDING', 'REJECTED'].includes(o.status);
  const locked = o.status === 'LOCKED';
  const actions = reviewing
    ? (o.status === 'PENDING' && <>
      <button className="btn btn-ghost danger-text" onClick={() => { setRejecting(true); setReason(''); }}>Từ chối</button>
      <button className="btn btn-primary" onClick={async () => { const e = await admin.approveOrg(o.id); if (e) toast(e, 'err'); else { toast('Đã duyệt hồ sơ tổ chức'); nav(back); } }}>Duyệt hồ sơ</button>
    </>)
    : (locked
      ? <button className="btn btn-primary" onClick={() => setUnlocking(true)}><Unlock size={16} /> Khôi phục tổ chức</button>
      : <button className="btn btn-danger" onClick={() => setLocking(true)}><Lock size={16} /> Khóa tổ chức</button>);
  const type = ORG_TYPES[o.orgType];
  /* Tệp nộp trước khi tách ô (slot rỗng): coi là giấy tờ chính, riêng nhóm thiện nguyện hiển thị riêng (khớp backend) */
  const slotOfDoc = (d) => d.slot || (o.orgType === 'VOLUNTEER' ? null : 'MAIN');
  const oldDocs = o.documents.filter((d) => !slotOfDoc(d));

  return (
    <div className="stack detail-page">
      <Link to={back} className="back-link"><ArrowLeft size={16} /> Quay lại Quản lý tổ chức</Link>
      <div className="page-head">
        <div>
          <div className="row gap8 wrap"><h1 className="page-title">{o.name}</h1>{orgStatusTag(o.status)}</div>
          <p className="muted">{type?.label}</p>
        </div>
        {actions && <div className="row gap8 page-actions">{actions}</div>}
      </div>

      <div className="detail-grid">
        <section className="card pad stack-sm">
          <h3>Thông tin tổ chức</h3>
          <div className="kv">
            <span>Loại hình</span><b>{type?.label}</b>
            <span>Địa chỉ</span><b>{[o.streetAddress, o.ward, o.province].filter(Boolean).join(', ')}</b>
            <span>Mô tả</span><b>{o.description || '—'}</b>
            <span>Số ca xử lý cùng lúc</span><b>{o.maxConcurrentCases ?? '—'}</b>
            <span>Bán kính hoạt động</span><b>{o.serviceRadiusKm != null ? `${o.serviceRadiusKm} km` : '—'}</b>
            <span>Trang web / fanpage</span><b>{o.websiteUrl ? <a href={o.websiteUrl} target="_blank" rel="noopener noreferrer nofollow">{o.websiteUrl}</a> : '—'}</b>
            <span>Người đại diện</span><b>{rep.fullName}</b>
            <span>Số điện thoại</span><b>{rep.phone}</b>
            <span>Email</span><b>{rep.email}</b>
            {o.rejectionReason && <><span>Lý do từ chối trước</span><b>{o.rejectionReason}</b></>}
            {locked && rep.lockedUntil && <><span>Khóa đến</span><b>{fmtDate(ts(rep.lockedUntil))}</b></>}
          </div>
          {/* Ghim trụ sở trên bản đồ: khi duyệt, kiểm tra ghim có nằm đúng địa chỉ khai báo không (AI Matching tính khoảng cách từ đây) */}
          <h3>Trụ sở trên bản đồ</h3>
          {o.latitude != null && o.longitude != null ? (
            <>
              <MapView layerSwitch center={[o.latitude, o.longitude]} zoom={16} height={240}
                markers={[{ id: 'hq', lat: o.latitude, lng: o.longitude, kind: 'org', color: '#0e7490', label: 'Trụ sở' }]} />
              <span className="muted small">Tọa độ: {o.latitude}, {o.longitude}</span>
            </>
          ) : <span className="muted small">Chưa có vị trí trụ sở.</span>}
        </section>
        <section className="card pad stack-sm">
          <h3>Hồ sơ xác minh</h3>
          {docSlotsOf(o.orgType).map((slot) => {
            const files = o.documents.filter((d) => slotOfDoc(d) === slot);
            const required = requiredDocSlots(o.orgType).includes(slot);
            return (
              <div key={slot} className="stack-sm">
                <b className="small">{docSlotLabel(o.orgType, slot)}{required ? '' : ' (không bắt buộc)'}</b>
                {files.length === 0
                  ? <span className={`small ${required ? 'danger-text' : 'muted'}`}>{required ? 'Thiếu tệp bắt buộc.' : 'Không có.'}</span>
                  : files.map((d, i) => <DocButton key={d.id} docId={d.id}>Tệp {i + 1}{d.fileType ? ` (${d.fileType})` : ''}</DocButton>)}
              </div>
            );
          })}
          {oldDocs.length > 0 && (
            <div className="stack-sm">
              <b className="small">Hồ sơ nộp trước khi tách mục</b>
              {oldDocs.map((d) => <DocButton key={d.id} docId={d.id}>{d.docLabel || 'Tệp hồ sơ'}</DocButton>)}
            </div>
          )}
        </section>
      </div>

      {v.reviews.length > 0 && (
        <section className="card pad stack-sm">
          <h3>Lịch sử xét duyệt</h3>
          {v.reviews.map((r) => <div key={r.id} className="viol-row"><b>{r.decision === 'APPROVED' ? 'Đã duyệt' : 'Từ chối'} • {r.adminName}</b><span className="muted small">{fmtDateTime(ts(r.createdAt))}{r.reason ? ` • ${r.reason}` : ''}</span></div>)}
        </section>
      )}

      {!reviewing && (
        <div className="detail-grid">
          <section className="card pad stack-sm">
            <h3>Bài đăng vi phạm ({v.violatedPosts.length})</h3>
            {v.violatedPosts.length === 0 ? <span className="muted small">Không có.</span> : v.violatedPosts.map((p) => <div key={p.id} className="viol-row"><p>{p.title}</p><span className="muted small">{fmtDateTime(ts(p.handledAt))} • {p.reason}</span></div>)}
          </section>
          <section className="card pad stack-sm">
            <h3>Chiến dịch bị xử lý ({v.violatedCampaigns.length})</h3>
            {v.violatedCampaigns.length === 0 ? <span className="muted small">Không có.</span> : v.violatedCampaigns.map((c) => <div key={c.id} className="viol-row"><b>{c.title}</b><span className="muted small">{c.extra} • Xử lý {fmtDateTime(ts(c.handledAt))} • {c.reason}</span></div>)}
          </section>
        </div>
      )}

      {rejecting && (
        <Modal title="Từ chối hồ sơ" onClose={() => setRejecting(false)} footer={<>
          <button className="btn btn-ghost" onClick={() => setRejecting(false)}>Hủy</button>
          <button className="btn btn-danger" onClick={async () => { const r = await admin.rejectOrg(o.id, reason); if (r) toast(r, 'err'); else { toast('Đã từ chối và thông báo cho tổ chức'); setRejecting(false); nav(back); } }}>Gửi từ chối</button>
        </>}>
          <Field label="Lý do từ chối" required hint="Nêu rõ thông tin cần bổ sung để tổ chức điều chỉnh."><textarea className="input" rows={4} value={reason} onChange={(e) => setReason(e.target.value)} /></Field>
        </Modal>
      )}
      {locking && <LockModal name={o.name} onClose={() => setLocking(false)} onLock={async (m, r) => { const e = await admin.lockOrg(o.id, m, r); if (!e) { toast('Đã khóa tổ chức, các chiến dịch đang chạy đã kết thúc'); setLocking(false); reload(); } return e; }} />}
      {unlocking && <Confirm title="Khôi phục tổ chức" text={`Khôi phục hoạt động cho ${o.name}?`} onClose={() => setUnlocking(false)} onOk={async () => { const e = await admin.unlockOrg(o.id); toast(e || 'Đã khôi phục tổ chức', e ? 'err' : 'ok'); reload(); }} />}
    </div>
  );
}
