import { Link } from 'react-router-dom';
import { HandHeart, MapPin } from 'lucide-react';
import { Photo, Progress, Tag } from '@/components/common';
import { CAMPAIGN_STATUS, campaignStatus, coverOf, fmtDate, money } from '@/utils';

export function CampaignCard({ c }) {
  const st = campaignStatus(c);
  const left = Math.ceil((c.end - Date.now()) / 86400000);
  return (
    <article className="card camp-card">
      <Photo img={coverOf(c)} className="camp-cover">
        {st === 'ACTIVE' ? `Còn ${left} ngày` : CAMPAIGN_STATUS[st].label}
      </Photo>
      <div className="camp-body">
        <span className="muted small"><MapPin size={12} /> {c.area}</span>
        <Link to={`/campaigns/${c.id}`} className="camp-title">{c.name}</Link>
        <span className="small">Tổ chức: <b>{c.orgName}</b></span>
        <p className="muted small clamp2">{c.purpose}</p>
        <div className="row between"><b className="money">{money(c.raised)}</b><span className="muted small">Mục tiêu {money(c.target)}</span></div>
        <Progress value={c.raised} max={c.target} />
        <div className="row between small muted"><span>{fmtDate(c.start)} – {fmtDate(c.end)}</span><Tag tone={CAMPAIGN_STATUS[st].tone}>{CAMPAIGN_STATUS[st].label}</Tag></div>
        <div className="row gap8">
          <Link to={`/campaigns/${c.id}`} className="btn btn-ghost btn-sm grow">Xem chi tiết</Link>
          {st === 'ACTIVE' && c.raised < c.target ? <Link to={`/campaigns/${c.id}/donate`} className="btn btn-primary btn-sm grow"><HandHeart size={15} /> Ủng hộ</Link>
            : <button className="btn btn-primary btn-sm grow" disabled>Ủng hộ</button>}
        </div>
      </div>
    </article>
  );
}
