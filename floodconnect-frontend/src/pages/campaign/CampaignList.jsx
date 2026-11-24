import { useEffect, useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { HandHeart, Plus, Search, Wallet } from 'lucide-react';
import { useApp } from '@/hooks';
import { Empty, SkeletonCampaignCard, Tabs } from '@/components/common';
import { money } from '@/utils';
import { CampaignCard } from '@/components/campaign/CampaignCard';
import { OrgCampaignTable } from '@/components/campaign/OrgCampaignTable';

/* ================= LIST ================= */
export function CampaignList() {
  const { state, me, canRescue, orgLocked, myOrg, load } = useApp();
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') === 'mine' && canRescue ? 'mine' : 'all';
  const [status, setStatus] = useState('ACTIVE');
  const [q, setQ] = useState('');
  const [loading, setLoading] = useState(true);
  /* Tab "Chiến dịch cộng đồng": máy chủ lọc, sắp xếp và chia trang; "Tải thêm" nối trang kế tiếp (12 chiến dịch mỗi lần) */
  const [found, setFound] = useState({ ids: [], last: true, total: 0, next: 0 });
  const [more, setMore] = useState(false);
  const [stats, setStats] = useState(null); // { activeCampaigns, totalRaised } do máy chủ đếm trên toàn bộ dữ liệu

  useEffect(() => { load.publicStats().then(setStats); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (tab !== 'all') return undefined;
    /* alive: chỉ lượt tải mới nhất được dùng (người dùng gõ tìm kiếm liên tục) */
    let alive = true;
    setLoading(true);
    const t = setTimeout(() => load.campaigns({ status: status || 'ALL', q }).then((r) => {
      if (!alive) return;
      setFound(r ? { ...r, next: 1 } : { ids: [], last: true, total: 0, next: 0 });
      setLoading(false);
    }), 250);
    return () => { alive = false; clearTimeout(t); };
  }, [tab, status, q]); // eslint-disable-line react-hooks/exhaustive-deps
  const loadMore = async () => {
    setMore(true);
    const r = await load.campaigns({ status: status || 'ALL', q, page: found.next });
    setMore(false);
    if (r) setFound((f) => ({ ids: [...f.ids, ...r.ids.filter((id) => !f.ids.includes(id))], last: r.last, total: r.total, next: f.next + 1 }));
  };

  const list = tab === 'mine'
    ? state.campaigns.filter((c) => c.orgId === myOrg.id).filter((c) => !q || c.name.toLowerCase().includes(q.toLowerCase())).sort((a, b) => a.start - b.start)
    : found.ids.map((id) => state.campaigns.find((c) => c.id === id)).filter(Boolean);
  const running = stats?.activeCampaigns;
  const totalRaised = stats?.totalRaised;

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1 className="page-title">Chiến dịch cứu trợ & quyên góp lũ lụt</h1>
          <div className="row gap16 muted"><span><b className="text">{running ?? '—'}</b> chiến dịch đang chạy</span><span><b className="text">{totalRaised == null ? '—' : money(totalRaised)}</b> đã quyên góp</span></div>
        </div>
        {me && me.role !== 'ADMIN' && <Link to="/my-donations" className="btn btn-ghost"><Wallet size={16} /> Đóng góp của tôi</Link>}
      </div>
      {canRescue && (
        <div className="row between">
          <Tabs value={tab} onChange={(v) => setSp(v === 'mine' ? { tab: 'mine' } : {})} tabs={[{ value: 'all', label: 'Chiến dịch cộng đồng' }, { value: 'mine', label: 'Chiến dịch của chúng tôi' }]} />
          {tab === 'mine' && <button className="btn btn-primary" disabled={orgLocked} title={orgLocked ? 'Tài khoản đang bị khóa' : undefined} onClick={() => nav('/campaigns/new')}><Plus size={16} /> Tạo chiến dịch mới</button>}
        </div>
      )}
      <div className="row gap8 wrap">
        <div className="search inline"><Search size={16} /><input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm theo tên chiến dịch" aria-label="Tìm chiến dịch" /></div>
        {tab === 'all' && <Tabs value={status} onChange={setStatus} tabs={[{ value: 'ACTIVE', label: 'Đang diễn ra' }, { value: 'UPCOMING', label: 'Sắp diễn ra' }, { value: 'ENDED', label: 'Đã kết thúc' }, { value: '', label: 'Tất cả' }]} />}
      </div>

      {tab === 'mine' && orgLocked && <div className="alert alert-danger">Tài khoản tổ chức đang bị khóa: không thể tạo, chỉnh sửa hoặc gia hạn chiến dịch. Các chiến dịch đang diễn ra đã được kết thúc (Tài khoản tổ chức bị khóa) và không được mở lại.</div>}
      {tab === 'mine' ? <OrgCampaignTable list={list} onEdit={(c) => nav(`/campaigns/${c.id}/edit`)} /> : (
        list.length === 0 && loading ? <div className="camp-grid"><SkeletonCampaignCard /><SkeletonCampaignCard label="" /><SkeletonCampaignCard label="" /></div>
          : list.length === 0 ? <Empty icon={<HandHeart size={34} />} title="Không có chiến dịch phù hợp" /> : (
            <>
              <div className="camp-grid">{list.map((c) => <CampaignCard key={c.id} c={c} />)}</div>
              {!found.last && <button className="btn btn-ghost" disabled={more} onClick={loadMore}>{more ? 'Đang tải…' : `Tải thêm (còn ${found.total - list.length} chiến dịch)`}</button>}
            </>
          )
      )}
    </div>
  );
}
