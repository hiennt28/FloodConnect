import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApp, useAsync } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { SkeletonRows, Tabs, Tag } from '@/components/common';
import { Pager } from '@/components/admin/Pager';
import { ago, CAMPAIGN_REASON_LABEL } from '@/utils';

const PAGE_SIZE = 20; // khớp size mặc định của adminApi.reportedCampaigns / campaignReports

/* ================= CAMPAIGN REPORTS ================= */
export function CampaignReports() {
  const { admin } = useApp();
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') === 'reports' ? 'reports' : 'campaigns';
  const page = Math.max(0, Number(sp.get('page')) || 0); // trang đang xem nằm trên địa chỉ: mở chi tiết rồi quay lại vẫn đúng trang
  const setPage = (p) => setSp({ tab, page: String(p) });
  /* Tab "Chiến dịch bị báo cáo" luôn tải (số trên tab là tổng số từ máy chủ); tab "Báo cáo của người dân" chỉ tải khi mở */
  const [rows] = useAsync(() => admin.reportedCampaigns(tab === 'campaigns' ? page : 0), [tab, page]);
  const [reports] = useAsync(() => (tab === 'reports' ? admin.campaignReports(page) : Promise.resolve(null)), [tab, page]);
  const open = (id) => nav(`/admin/campaign-reports/${id}?from=${tab}&page=${page}`);
  const data = tab === 'campaigns' ? rows : reports;

  return (
    <div className="stack">
      <h1 className="page-title">Xử lý báo cáo chiến dịch</h1>
      <Tabs value={tab} onChange={(v) => setSp({ tab: v })} tabs={[{ value: 'campaigns', label: 'Chiến dịch bị báo cáo', count: rows?.total || 0 }, { value: 'reports', label: 'Báo cáo của người dân' }]} />
      <div className="card table-wrap">
        <table className="table">
          {tab === 'campaigns' ? (
            <><thead><tr><th>Tên chiến dịch</th><th>Tổ chức thực hiện</th><th>Báo cáo chờ xem xét</th><th /></tr></thead>
              <tbody>{rows === undefined ? <SkeletonRows cols={4} /> : !rows?.items.length && <tr><td colSpan={4} className="center muted">Không có chiến dịch bị báo cáo.</td></tr>}
                {(rows?.items || []).map(({ campaign: c, pendingReports }) => <tr key={c.id}><td><b>{c.name}</b></td><td>{c.orgName}</td><td><Tag tone="red">{pendingReports}</Tag></td><td className="right"><button className="btn btn-primary btn-sm" onClick={() => open(c.id)}>Xem</button></td></tr>)}
              </tbody></>
          ) : (
            <><thead><tr><th>Người báo cáo</th><th>Chiến dịch</th><th>Lý do</th><th>Mô tả</th><th>Thời gian</th><th>Trạng thái</th><th /></tr></thead>
              <tbody>{reports === undefined ? <SkeletonRows cols={7} /> : !reports?.items.length && <tr><td colSpan={7} className="center muted">Chưa có báo cáo.</td></tr>}
                {(reports?.items || []).map((r) => (
                  <tr key={r.id}><td>{r.reporter?.fullName}</td><td className="clamp-cell">{r.campaignName}</td><td>{CAMPAIGN_REASON_LABEL[r.reason] || r.reason}</td><td className="small muted">{r.description || '—'}</td><td className="small nowrap">{ago(ts(r.createdAt))}</td><td><Tag tone={r.status === 'PENDING' ? 'yellow' : 'green'}>{r.status === 'PENDING' ? 'Chờ xem xét' : 'Hoàn thành'}</Tag></td><td className="right"><button className="btn btn-ghost btn-sm" onClick={() => open(r.campaignId)}>Xem</button></td></tr>
                ))}</tbody></>
          )}
        </table>
      </div>
      {data && <Pager page={page} size={PAGE_SIZE} total={data.total} last={data.last} unit={tab === 'campaigns' ? 'chiến dịch' : 'báo cáo'} onChange={setPage} />}
    </div>
  );
}
