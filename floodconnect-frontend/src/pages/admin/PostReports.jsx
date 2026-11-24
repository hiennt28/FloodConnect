import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApp, useAsync } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { SkeletonRows, Tabs, Tag } from '@/components/common';
import { Pager } from '@/components/admin/Pager';
import { ago, POST_REASON_LABEL } from '@/utils';

const PAGE_SIZE = 20; // khớp size mặc định của adminApi.reportedPosts / postReports

/* ================= POST REPORTS ================= */
export function PostReports() {
  const { admin } = useApp();
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const tab = sp.get('tab') === 'reports' ? 'reports' : 'posts';
  const page = Math.max(0, Number(sp.get('page')) || 0); // trang đang xem nằm trên địa chỉ: mở chi tiết rồi quay lại vẫn đúng trang
  const setPage = (p) => setSp({ tab, page: String(p) });
  /* Tab "Bài đăng bị báo cáo" luôn tải (số trên tab là tổng số bài từ máy chủ); tab "Báo cáo của người dân" chỉ tải khi mở */
  const [rows] = useAsync(() => admin.reportedPosts(tab === 'posts' ? page : 0), [tab, page]);
  const [reports] = useAsync(() => (tab === 'reports' ? admin.postReports(page) : Promise.resolve(null)), [tab, page]);
  const open = (id) => nav(`/admin/post-reports/${id}?from=${tab}&page=${page}`);
  const data = tab === 'posts' ? rows : reports;

  return (
    <div className="stack">
      <h1 className="page-title">Xử lý báo cáo bài đăng</h1>
      <Tabs value={tab} onChange={(v) => setSp({ tab: v })} tabs={[{ value: 'posts', label: 'Bài đăng bị báo cáo', count: rows?.total || 0 }, { value: 'reports', label: 'Báo cáo của người dân' }]} />
      <div className="card table-wrap">
        <table className="table">
          {tab === 'posts' ? (
            <><thead><tr><th>Người đăng</th><th>Nội dung</th><th>Báo cáo chờ xem xét</th><th /></tr></thead>
              <tbody>{rows === undefined ? <SkeletonRows cols={4} /> : !rows?.items.length && <tr><td colSpan={4} className="center muted">Không có bài đăng bị báo cáo.</td></tr>}
                {(rows?.items || []).map(({ post, pendingReports }) => <tr key={post.id}><td><b>{post.author?.fullName}</b></td><td className="clamp-cell">{post.content}</td><td><Tag tone="red">{pendingReports}</Tag></td><td className="right"><button className="btn btn-primary btn-sm" onClick={() => open(post.id)}>Xem</button></td></tr>)}
              </tbody></>
          ) : (
            <><thead><tr><th>Người báo cáo</th><th>Bài đăng</th><th>Lý do</th><th>Mô tả</th><th>Thời gian</th><th>Trạng thái</th><th /></tr></thead>
              <tbody>{reports === undefined ? <SkeletonRows cols={7} /> : !reports?.items.length && <tr><td colSpan={7} className="center muted">Chưa có báo cáo.</td></tr>}
                {(reports?.items || []).map((r) => (
                  <tr key={r.id}><td>{r.reporter?.fullName}</td><td className="clamp-cell">{r.postContent}</td><td>{POST_REASON_LABEL[r.reason] || r.reason}</td><td className="small muted">{r.description || '—'}</td><td className="small nowrap">{ago(ts(r.createdAt))}</td><td><Tag tone={r.status === 'PENDING' ? 'yellow' : 'green'}>{r.status === 'PENDING' ? 'Chưa xử lý' : 'Hoàn thành'}</Tag></td><td className="right"><button className="btn btn-ghost btn-sm" onClick={() => open(r.postId)}>Xem</button></td></tr>
                ))}</tbody></>
          )}
        </table>
      </div>
      {data && <Pager page={page} size={PAGE_SIZE} total={data.total} last={data.last} unit={tab === 'posts' ? 'bài' : 'báo cáo'} onChange={setPage} />}
    </div>
  );
}
