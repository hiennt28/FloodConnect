import { useEffect, useState } from 'react';
import { useApp } from '@/hooks';
import { MapPinned } from 'lucide-react';
import { Empty, Modal, SkeletonLines, Tag } from '@/components/common';
import { fmtDist, MATCH_WEIGHTS, ORG_STATUS, orgTypeLabel } from '@/utils';

/* Huy hiệu "đội đang có ca gần đây": trung tính khi đội còn sức (có thể ghé luôn), màu cam khi nhận thêm sẽ gần kín ca */
function NearbyBadge({ nearby, busy }) {
  if (!nearby?.length) return null;
  const list = nearby.slice(0, 2).map((n) => `${n.code} (cách ${fmtDist(n.dist)})`).join(', ') + (nearby.length > 2 ? ` và ${nearby.length - 2} ca khác` : '');
  return (
    <span className={`nearby-badge ${busy ? 'busy' : ''}`}>
      <MapPinned size={13} />
      {busy
        ? <>Lực lượng đang tập trung tại khu vực này: {nearby.length} ca gần đây ({list}), nhận thêm sẽ gần kín ca</>
        : <>Đang xử lý {nearby.length} ca gần đây: {list}. Đội còn sức, có thể ghé luôn</>}
    </span>
  );
}

export function AssignModal({ s, onClose, onAssign }) {
  const { sos } = useApp();
  const [pick, setPick] = useState('');
  const [cands, setCands] = useState(null);
  useEffect(() => { sos.candidates(s.id).then(setCands); }, [s.id]); // eslint-disable-line react-hooks/exhaustive-deps
  return (
    <Modal title={`Chỉ định tổ chức cho ${s.code}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-primary" disabled={!pick} onClick={() => onAssign(pick)}>Xác nhận chỉ định</button>
    </>}>
      {cands === null ? <SkeletonLines lines={3} height={56} label="Đang tải gợi ý tổ chức…" /> : cands.length === 0 ? <Empty title="Không có tổ chức phù hợp">Ca SOS tiếp tục ở trạng thái Chờ xử lý để Admin theo dõi.</Empty> : (
        <div className="stack-sm">
          {cands.some((c) => c.m?.byAi)
            ? <span className="muted small">AI Matching (dịch vụ AI): điểm = trọng số × vị trí (L), độ tương đồng mô tả (S), năng lực đáp ứng nhu cầu (C), tình trạng tiếp nhận (W). Trọng số đổi theo mức đầy đủ của tin SOS (tin ít thông tin dựa nhiều hơn vào vị trí và tải). Tổ chức không đủ điều kiện có điểm 0 kèm lý do. Chỉ là gợi ý, Admin quyết định.</span>
            : <span className="muted small">AI Matching: điểm = {MATCH_WEIGHTS.L}% vị trí + {MATCH_WEIGHTS.S}% độ tương đồng mô tả + {MATCH_WEIGHTS.C}% năng lực đáp ứng nhu cầu + {MATCH_WEIGHTS.W}% tình trạng tiếp nhận. Chỉ là gợi ý, Admin quyết định.</span>}
          {cands.map(({ org: o, m, canTake, nearby, busy, cap }) => (
            <label key={o.id} className={`pick-row ${pick === o.id ? 'on' : ''} ${!canTake ? 'dim' : ''}`}>
              <input type="radio" name="org" disabled={!canTake} checked={pick === o.id} onChange={() => setPick(o.id)} />
              <div className="grow"><b>{o.name} <span className="match-pill">{m.score}/100</span></b><span className="muted small">{orgTypeLabel(o)} • cách {fmtDist(m.dist)} • đang nhận {m.active}{cap ? `/${cap}` : ''} ca</span><span className="muted small">L {m.L} • S {m.S} • C {m.C ?? '—'} • W {m.W}{m.byAi && m.score > 0 ? ` (trọng số ${m.weights.L}/${m.weights.S}/${m.weights.C}/${m.weights.W}%)` : ''} — {m.reason}</span><NearbyBadge nearby={nearby} busy={busy} /></div>
              <Tag tone={ORG_STATUS[o.opStatus].tone}>{ORG_STATUS[o.opStatus].label}</Tag>
            </label>
          ))}
        </div>
      )}
    </Modal>
  );
}
