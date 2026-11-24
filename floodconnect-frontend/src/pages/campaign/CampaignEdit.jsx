import { useEffect, useState } from 'react';
import { Link, Navigate, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useApp } from '@/hooks';
import { Empty, SkeletonDetail } from '@/components/common';
import { CAMPAIGN_STATUS, campaignStatus } from '@/utils';
import { CampaignForm } from '@/components/campaign/CampaignForm';

export function CampaignEdit() {
  const { id } = useParams();
  const nav = useNavigate();
  const { state, myOrg, orgLocked, load } = useApp();
  const [loading, setLoading] = useState(true);
  useEffect(() => { load.campaign(id).then(() => setLoading(false)); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  const c = state.campaigns.find((x) => x.id === id);
  if (loading && !c) return <SkeletonDetail label="Đang tải chiến dịch…" />;
  if (!c || c.orgId !== myOrg?.id) return <Empty title="Không tìm thấy chiến dịch của tổ chức bạn"><Link to="/campaigns?tab=mine">Về chiến dịch của chúng tôi</Link></Empty>;
  const st = campaignStatus(c);
  if (orgLocked || st === 'ENDED') return <Navigate to="/campaigns?tab=mine" replace />;
  return (
    <div className="narrow stack">
      <button className="link row gap6" onClick={() => nav('/campaigns?tab=mine')}><ArrowLeft size={16} /> Quay lại chiến dịch của chúng tôi</button>
      <div className="card pad">
        <div className="page-head"><div><h1 className="page-title">Chỉnh sửa chiến dịch</h1><p className="muted">{c.name} • {CAMPAIGN_STATUS[st].label}</p></div></div>
        <CampaignForm key={c.id} campaign={c} onClose={() => nav('/campaigns?tab=mine')} />
      </div>
    </div>
  );
}
