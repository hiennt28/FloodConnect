import { Navigate, useNavigate } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useApp } from '@/hooks';
import { CampaignForm } from '@/components/campaign/CampaignForm';

export function CampaignCreate() {
  const nav = useNavigate();
  const { orgLocked } = useApp();
  if (orgLocked) return <Navigate to="/campaigns?tab=mine" replace />;
  return (
    <div className="narrow stack">
      <button className="link row gap6" onClick={() => nav('/campaigns?tab=mine')}><ArrowLeft size={16} /> Quay lại chiến dịch của chúng tôi</button>
      <div className="card pad">
        <div className="page-head"><div><h1 className="page-title">Tạo chiến dịch mới</h1><p className="muted">Cung cấp đầy đủ thông tin để cộng đồng biết chiến dịch của bạn.</p></div></div>
        <CampaignForm campaign={null} onClose={() => nav('/campaigns?tab=mine')} />
      </div>
    </div>
  );
}
