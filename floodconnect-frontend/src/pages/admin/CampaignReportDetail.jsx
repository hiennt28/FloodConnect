import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useApp, useAsync } from '@/hooks';
import { mCampaign, ts } from '@/services/api/mappers';
import { Empty, Field, Modal, SkeletonDetail, Tag } from '@/components/common';
import { ago, CAMPAIGN_REASON_LABEL, CAMPAIGN_REPORT_REASONS, CAMPAIGN_STATUS, campaignStatus, ENDED_REASON, fmtDate, money } from '@/utils';

export function CampaignReportDetail() {
  const { id } = useParams();
  const [sp] = useSearchParams();
  const nav = useNavigate();
  const { admin, toast } = useApp();
  const [stopping, setStopping] = useState(null);
  const [d] = useAsync(() => admin.campaignReportDetail(id), [id]);
  const back = `/admin/campaign-reports${sp.get('from') ? `?tab=${sp.get('from')}${sp.get('page') ? `&page=${sp.get('page')}` : ''}` : ''}`; // quay lại đúng tab và trang
  if (d === undefined) return <SkeletonDetail label="Đang tải chiến dịch bị báo cáo…" />;
  if (!d) return <Empty title="Không tìm thấy chiến dịch"><Link to="/admin/campaign-reports">Về danh sách báo cáo</Link></Empty>;
  const vc = mCampaign(d.campaign);
  const vReports = d.reports;
  const st = campaignStatus(vc);
  const resolve = async (decision, reason, desc, okMsg) => {
    const e = await admin.resolveCampaign(vc.id, decision, reason, desc);
    if (e) { toast(e, 'err'); return; }
    toast(okMsg);
    nav(back);
  };

  return (
    <div className="stack detail-page">
      <Link to={back} className="back-link"><ArrowLeft size={16} /> Quay lại Báo cáo chiến dịch</Link>
      <div className="page-head">
        <div>
          <h1 className="page-title">{vc.name}</h1>
          <div className="row gap6 wrap mt8"><Tag tone={CAMPAIGN_STATUS[st].tone}>{CAMPAIGN_STATUS[st].label}</Tag>{st === 'ENDED' && <Tag tone="gray">{ENDED_REASON[vc.endedReason || 'TIME_UP']}</Tag>}<Tag tone={vReports.length ? 'yellow' : 'green'}>{vReports.length ? `${vReports.length} báo cáo chờ xem xét` : 'Không còn báo cáo chờ'}</Tag></div>
        </div>
        {vReports.length > 0 && <div className="row gap8 page-actions">
          <button className="btn btn-ghost" onClick={() => resolve('KEEP', null, null, 'Đã giữ chiến dịch')}>Giữ chiến dịch</button>
          <button className="btn btn-danger" disabled={st === 'ENDED'} title={st === 'ENDED' ? 'Chiến dịch đã kết thúc' : undefined} onClick={() => setStopping({ reason: 'PROFITEERING', desc: '' })}>Dừng chiến dịch</button>
        </div>}
      </div>

      <div className="detail-grid">
        <section className="card pad stack-sm">
          <h3>Thông tin chiến dịch</h3>
          <div className="kv">
            <span>Tổ chức</span><b>{vc.orgName}</b>
            <span>Mục đích</span><b>{vc.purpose}</b>
            <span>Khu vực</span><b>{vc.area}</b>
            <span>Đối tượng</span><b>{vc.beneficiaries}</b>
            <span>Đã nhận</span><b>{money(vc.raised)} / {money(vc.target)}</b>
            <span>Thời gian</span><b>{fmtDate(vc.start)} – {fmtDate(vc.end)}</b>
            <span>Tài khoản nhận</span><b>{vc.bank || 'Chưa cập nhật ngân hàng'} • {vc.accountNo} • {vc.accountName}</b>
          </div>
          <Link to={`/campaigns/${vc.id}`} className="small">Mở trang chiến dịch</Link>
        </section>
        <section className="card pad stack-sm">
          <h3>Báo cáo chờ xem xét ({vReports.length})</h3>
          {vReports.length === 0 ? <span className="muted small">Không còn báo cáo chờ xử lý.</span> : vReports.map((r) => <div key={r.id} className="viol-row"><b>{r.reporter?.fullName} – {CAMPAIGN_REASON_LABEL[r.reason] || r.reason}</b><span className="muted small">{r.description || 'Không có mô tả'} • {ago(ts(r.createdAt))}</span></div>)}
        </section>
      </div>

      {stopping && (
        <Modal title="Dừng chiến dịch" onClose={() => setStopping(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setStopping(null)}>Hủy</button>
          <button className="btn btn-danger" onClick={() => resolve('REMOVE', CAMPAIGN_REPORT_REASONS[stopping.reason], stopping.desc, 'Đã dừng chiến dịch và thông báo các bên')}>Xác nhận dừng</button>
        </>}>
          <div className="stack">
            <Field label="Lý do"><select className="input" value={stopping.reason} onChange={(e) => setStopping({ ...stopping, reason: e.target.value })}>{Object.entries(CAMPAIGN_REPORT_REASONS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</select></Field>
            <Field label="Mô tả (nếu có)"><textarea className="input" rows={3} value={stopping.desc} onChange={(e) => setStopping({ ...stopping, desc: e.target.value })} /></Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
