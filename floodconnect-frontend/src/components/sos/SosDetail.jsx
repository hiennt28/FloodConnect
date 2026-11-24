import { useState } from 'react';
import { CheckCircle2, Clock, MapPin, Pencil, ShieldCheck, XCircle } from 'lucide-react';
import { useApp, useNow } from '@/hooks';
import MapView from '@/components/map/MapView';
import { Confirm, Sev, Tag } from '@/components/common';
import { ACTIVE_SOS, fmtDateTime, fmtDist, haversine, HOTLINE, orgTypeLabel, PENDING_TIMEOUT_MIN, SOS_STATUS, waitLeft } from '@/utils';
import { ContactButtons } from './ContactButtons';
import { AiNote } from './AiNote';
import { EditSosModal } from './EditSosModal';
import { MarkSafeModal } from './MarkSafeModal';
import { DoneNote } from './DoneNote';
import { VerifyNote } from './VerifyNote';

/* Thời gian chờ còn lại dạng "14 phút 28 giây"; dưới 1 phút chỉ hiện "45 giây" */
const fmtLeft = (ms) => {
  const s = Math.ceil(ms / 1000);
  const m = Math.floor(s / 60);
  return m > 0 ? `${m} phút ${s % 60} giây` : `${s} giây`;
};

const STEPS = [
  { key: 'sent', label: 'Đã gửi' },
  { key: 'assign', label: 'Phân công' },
  { key: 'go', label: 'Đã tiếp nhận' },
  { key: 'done', label: 'An toàn' },
];
const stepIndex = (s) => ({ PENDING: 0, ASSIGNED: 1, ACCEPTED: 2, DONE: 4, CANCELLED: 0, INVALID: 0 }[s.status]);

/* Khung bản đồ chứa cả vị trí người gửi và trụ sở tổ chức, chừa lề để không cắt marker */
const boundsOf = (a, b) => {
  const dLat = Math.max(0.01, Math.abs(a.lat - b.lat) * 0.3);
  const dLng = Math.max(0.01, Math.abs(a.lng - b.lng) * 0.3);
  return [[Math.min(a.lat, b.lat) - dLat, Math.min(a.lng, b.lng) - dLng], [Math.max(a.lat, b.lat) + dLat, Math.max(a.lng, b.lng) + dLng]];
};

/* Ca SOS của chính người dùng: đủ chi tiết, sửa/hủy. Ca của người khác dùng SosPublicCard (chỉ thông tin công khai) */
export function SosDetail({ s }) {
  const { sos, toast } = useApp();
  useNow(1000); // vẽ lại mỗi giây để đồng hồ chờ tổ chức nhận ca đếm ngược từng giây
  const [confirm, setConfirm] = useState(false);
  const [editing, setEditing] = useState(false);
  const [markingSafe, setMarkingSafe] = useState(false);
  const org = s.org;
  const idx = stepIndex(s);
  const hasHq = org?.lat != null && org?.lng != null;
  const hqDist = hasHq ? haversine(org, s) : null;
  const left = s.status === 'PENDING' ? waitLeft(s.createdAt, PENDING_TIMEOUT_MIN) : 0;

  return (
    <div className={`card sos-card ${ACTIVE_SOS.includes(s.status) ? 'active' : ''}`}>
      <div className="row between wrap gap8">
        <div className="row gap8 wrap">
          <Tag tone="blue">Mã: {s.code}</Tag>
          <Sev level={s.severity} />
          <span className="muted small">Gửi lúc {fmtDateTime(s.createdAt)}{s.updatedAt && ` • Cập nhật ${fmtDateTime(s.updatedAt)}`}</span>
        </div>
        <Tag tone={SOS_STATUS[s.status].tone} dot>{SOS_STATUS[s.status].label}</Tag>
      </div>
      <div className="sos-desc">
        <div className="row between"><span className="muted small">Nội dung yêu cầu</span><span className="small"><MapPin size={13} /> {s.address}</span></div>
        <p>“{s.desc}”</p>
      </div>
      <VerifyNote s={s} self />
      <AiNote s={s} />
      {s.adminReview && s.status === 'PENDING' && <div className="alert alert-info"><Clock size={15} /> Ca của bạn đã được ghi nhận, Admin sẽ liên hệ xác minh rồi chuyển cho tổ chức cứu hộ gần nhất. Vui lòng giữ máy.</div>}

      <DoneNote s={s} self />

      {s.status === 'INVALID' && (
        <div className="alert alert-danger"><XCircle size={15} /> Admin đã xác minh: ca này không được xác nhận là yêu cầu cứu hộ.{s.invalidReason && ` Lý do: ${s.invalidReason}.`} Nếu bạn vẫn cần cứu hộ, hãy gửi yêu cầu mới hoặc gọi 112.</div>
      )}

      {!['CANCELLED', 'INVALID'].includes(s.status) && (
        <div className="steps">
          {STEPS.map((st, i) => (
            <div key={st.key} className={`step ${i < idx ? 'done' : i === idx ? 'now' : ''}`}>
              <span className="step-dot">{i < idx ? <CheckCircle2 size={18} /> : i + 1}</span>
              <span className="step-label">{st.label}</span>
            </div>
          ))}
        </div>
      )}
      {s.status === 'PENDING' && !s.adminReview && <div className="alert alert-info"><Clock size={15} /> Đang tìm tổ chức phù hợp. {left > 0 ? `Nếu chưa có tổ chức nhận sau ${fmtLeft(left)}, hệ thống sẽ báo Admin điều phối.` : 'Đã quá thời gian chờ, Admin đang điều phối cho ca của bạn.'}</div>}

      {org && (
        <div className="org-strip">
          <div className="grow"><b>{org.name}</b><span className="muted small">{orgTypeLabel(org)}{hqDist != null && ` • Trụ sở cách bạn khoảng ${fmtDist(hqDist)}`}</span></div>
          {org.phone && <ContactButtons phone={org.phone} label="Gọi tổ chức" />}
        </div>
      )}
      {hasHq && s.status === 'ACCEPTED' && (
        <>
          <MapView height={280} center={[s.lat, s.lng]} bounds={boundsOf(s, org)}
            markers={[{ id: 'sos', lat: s.lat, lng: s.lng, kind: 'me', color: '#c81e1e', label: 'Vị trí của bạn' }, { id: 'hq', lat: org.lat, lng: org.lng, kind: 'org', color: '#0e7490', label: `Trụ sở ${org.name}` }]} />
          <span className="muted small">Bản đồ hiển thị trụ sở của tổ chức, không phải vị trí đội cứu hộ theo thời gian thực.</span>
        </>
      )}
      {/* Chờ xử lý: sửa hoặc hủy. Đã phân công: nội dung giữ nguyên để tổ chức quyết định đúng trên mô tả đã dùng khi ghép, chỉ hủy được */}
      {['PENDING', 'ASSIGNED'].includes(s.status) && (
        <div className="sos-actions">
          {s.status === 'PENDING' && <button className="btn btn-sm sos-btn sos-btn-edit" onClick={() => setEditing(true)}><Pencil size={18} /> Sửa yêu cầu</button>}
          <button className="btn btn-sm sos-btn sos-btn-cancel" onClick={() => setConfirm(true)}><XCircle size={18} /> Hủy yêu cầu</button>
        </div>
      )}
      {s.status === 'ASSIGNED' && (
        <span className="muted small">Ca đã được giao cho tổ chức nên không sửa được nội dung. Cần báo thêm tình hình khẩn, hãy gọi <a href={`tel:${HOTLINE.number}`}>{HOTLINE.number}</a>.</span>
      )}
      {/* Ca Đã tiếp nhận không hủy được (tổ chức có thể đang trên đường tới); người dân đã an toàn thì báo để tổ chức dừng lại */}
      {s.status === 'ACCEPTED' && (
        <>
          <div className="sos-actions">
            <button className="btn btn-sm sos-btn sos-btn-safe" onClick={() => setMarkingSafe(true)}><ShieldCheck size={18} /> Tôi đã an toàn</button>
          </div>
          <span className="muted small">Nếu bạn đã được đơn vị khác cứu hoặc nước đã rút, hãy bấm “Tôi đã an toàn” để tổ chức cứu hộ dừng lại và đi cứu người khác.</span>
        </>
      )}
      {confirm && <Confirm title="Hủy yêu cầu SOS" text="Bạn chắc chắn muốn hủy yêu cầu cứu hộ này?" okText="Hủy yêu cầu" danger onClose={() => setConfirm(false)} onOk={async () => { const r = await sos.cancel(s.id); toast(r || 'Đã hủy yêu cầu SOS', r ? 'err' : 'ok'); }} />}
      {markingSafe && <MarkSafeModal s={s} onClose={() => setMarkingSafe(false)} onSave={async (note) => {
        const r = await sos.markSafe(s.id, note);
        if (r) return r;
        toast('Đã báo bạn đã an toàn. Tổ chức cứu hộ đã được thông báo.');
        setMarkingSafe(false);
        return null;
      }} />}
      {editing && <EditSosModal s={s} onClose={() => setEditing(false)} onSave={async (changes) => {
        const r = await sos.edit(s.id, changes);
        if (r) return r;
        toast('Đã cập nhật yêu cầu SOS');
        setEditing(false);
        return null;
      }} />}
    </div>
  );
}
