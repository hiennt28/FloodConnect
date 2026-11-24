import { useState } from 'react';
import { Link } from 'react-router-dom';
import { CalendarPlus, HandHeart, Pencil, StopCircle } from 'lucide-react';
import { useApp } from '@/hooks';
import { Confirm, Empty, Tag } from '@/components/common';
import { ExtendCampaignModal } from './ExtendCampaignModal';
import { CAMPAIGN_STATUS, campaignStatus, ENDED_REASON, fmtDate, money } from '@/utils';

/* ================= ORG: bảng quản lý chiến dịch ================= */
export function OrgCampaignTable({ list, onEdit }) {
  const { campaigns, toast, orgLocked } = useApp();
  const [ending, setEnding] = useState(null);
  const [extending, setExtending] = useState(null);
  if (!list.length) return <Empty icon={<HandHeart size={34} />} title="Tổ chức chưa có chiến dịch nào">Tạo chiến dịch đầu tiên để kêu gọi ủng hộ.</Empty>;
  return (
    <div className="card table-wrap">
      <table className="table">
        <thead><tr><th>Chiến dịch</th><th>Thời gian</th><th>Đã nhận / Mục tiêu</th><th>Trạng thái</th><th /></tr></thead>
        <tbody>
          {list.map((c) => {
            const st = campaignStatus(c);
            const endedByTime = st === 'ENDED' && !c.endedReason;
            return (
              <tr key={c.id}>
                <td><Link to={`/campaigns/${c.id}`}><b>{c.name}</b></Link></td>
                <td className="nowrap small">{fmtDate(c.start)} – {fmtDate(c.end)}</td>
                <td className="nowrap">{money(c.raised)}<div className="muted small">/ {money(c.target)}</div></td>
                <td><Tag tone={CAMPAIGN_STATUS[st].tone}>{CAMPAIGN_STATUS[st].label}</Tag>{st === 'ENDED' && <div className="muted small">{ENDED_REASON[c.endedReason || 'TIME_UP']}</div>}</td>
                <td className="nowrap right">
                  {st !== 'ENDED' && !orgLocked && <button className="btn btn-ghost btn-sm" onClick={() => onEdit(c)}><Pencil size={14} /> Chỉnh sửa</button>}
                  {st !== 'ENDED' && !orgLocked && <button className="btn btn-ghost btn-sm danger-text" onClick={() => setEnding(c)}><StopCircle size={14} /> Kết thúc</button>}
                  {endedByTime && !orgLocked && <button className="btn btn-soft btn-sm" onClick={() => setExtending(c)}><CalendarPlus size={14} /> Gia hạn</button>}
                  {st === 'ENDED' && !endedByTime && <span className="muted small">Không thể gia hạn</span>}
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
      {ending && <Confirm title="Kết thúc chiến dịch" text={`Kết thúc "${ending.name}" trước thời hạn? Chiến dịch sẽ ngừng nhận ủng hộ và không thể mở lại.`} okText="Kết thúc" danger onClose={() => setEnding(null)} onOk={async () => { const r = await campaigns.end(ending.id); toast(r || 'Đã kết thúc chiến dịch', r ? 'err' : 'ok'); }} />}
      {extending && <ExtendCampaignModal c={extending} onClose={() => setExtending(null)} />}
    </div>
  );
}
