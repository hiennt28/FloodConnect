import { Fragment, useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { MapPin, Newspaper, PhoneCall, ShieldCheck } from 'lucide-react';
import { useApp } from '@/hooks';
import { PostComposer } from '@/components/post/PostComposer';
import PostCard from '@/components/post/PostCard';
import { Empty, SkeletonPost, Tabs, Tag } from '@/components/common';
import { ACTIVE_SOS, EMERGENCY_NUMBERS, haversine, HOTLINE } from '@/utils';

/* ================= NEWS FEED ================= */
export function Feed() {
  const { state, me, load } = useApp();
  const [tab, setTab] = useState('all');
  const [page, setPage] = useState(0);
  const [last, setLast] = useState(true);
  const [followIds, setFollowIds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [readyOrgs, setReadyOrgs] = useState(0);

  const fetchPage = async (t, p) => {
    setLoading(true);
    const r = await load.feed(t, p);
    if (t === 'following') setFollowIds((x) => (p === 0 ? r.ids : [...x, ...r.ids]));
    setPage(p);
    setLast(r.last);
    setLoading(false);
  };
  useEffect(() => { fetchPage(tab, 0); }, [tab, me?.id]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load.readyOrgCount().then(setReadyOrgs); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const visible = state.posts.filter((p) => p.status === 'VISIBLE');
  const posts = tab === 'all'
    ? [...visible].sort((a, b) => b.createdAt - a.createdAt)
    : followIds.map((id) => visible.find((p) => p.id === id)).filter(Boolean);

  /* Chưa có vị trí (chưa bật hoặc đã tắt chia sẻ): không đếm quanh một tâm mặc định, vì con số đó không phải "quanh bạn" */
  const hasGps = me?.lat != null;
  const nearSos = hasGps ? state.sos.filter((s) => ACTIVE_SOS.includes(s.status) && s.severity !== 'KHONG' && haversine(me, s) < 5000) : [];

  return (
    <div className="feed-layout">
      <div className="feed-main">
        {me ? <PostComposer /> : <div className="card pad row between"><span>Đăng nhập để đăng bài, bình luận và gửi SOS.</span><Link to="/login" className="btn btn-primary btn-sm">Đăng nhập</Link></div>}
        {me && <Tabs value={tab} onChange={(v) => setTab(v)} tabs={[{ value: 'all', label: 'Tất cả' }, { value: 'following', label: 'Đang theo dõi' }]} />}
        {loading && posts.length === 0 && <><SkeletonPost /><SkeletonPost label="" /></>}
        {!loading && posts.length === 0 && <Empty icon={<Newspaper size={34} />} title="Chưa có bài đăng nào">{tab === 'following' ? 'Theo dõi người dân hoặc tổ chức để xem bài của họ ở đây.' : 'Hãy là người đầu tiên chia sẻ tình hình khu vực.'}</Empty>}
        {posts.map((p) => <PostCard key={p.id} post={p} />)}
        {!last && <button className="btn btn-ghost block" disabled={loading} onClick={() => fetchPage(tab, page + 1)}>{loading ? 'Đang tải…' : 'Tải thêm bài viết'}</button>}
      </div>
      <aside className="feed-side">
        <div className="card pad">
          <div className="row between"><b>Tình hình bán kính 5 km</b><Tag tone="blue">GPS</Tag></div>
          <div className="mini-stats">
            <div className="mini-stat red"><span>Sự cố đang mở</span><b>{hasGps ? nearSos.length : '—'}</b></div>
            <div className="mini-stat blue"><span>Đội sẵn sàng</span><b>{readyOrgs}</b></div>
          </div>
          {!hasGps && <span className="muted small">{me ? <><Link to="/profile">Bật chia sẻ vị trí</Link> để xem số sự cố quanh bạn.</> : 'Đăng nhập và bật chia sẻ vị trí để xem số sự cố quanh bạn.'}</span>}
          <Link to="/map" className="btn btn-soft block btn-sm"><MapPin size={15} /> Mở bản đồ sự cố</Link>
        </div>
        <div className="card hot-card">
          <div className="hot-head"><PhoneCall size={18} /> Số khẩn cấp quốc gia</div>
          <a className="hot-big" href={`tel:${HOTLINE.number}`}>{HOTLINE.number}</a>
          <div className="hot-list">
            {EMERGENCY_NUMBERS.map((h) => <Fragment key={h.number}><span>{h.label}</span><a href={`tel:${h.number}`}>{h.number}</a></Fragment>)}
          </div>
        </div>
        <div className="card pad">
          <b className="row gap6"><ShieldCheck size={17} /> Kỹ năng sinh tồn mùa lũ</b>
          <ul className="tips">
            <li>Ngắt cầu dao điện khi nước bắt đầu vào nhà.</li>
            <li>Dự trữ 3 ngày nước uống, đèn pin và sạc dự phòng.</li>
            <li>Không lội qua đập tràn, ngầm khi nước chảy xiết.</li>
          </ul>
        </div>
      </aside>
    </div>
  );
}
