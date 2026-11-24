import { useState } from 'react';
import { Link, useNavigate, useParams, useSearchParams } from 'react-router-dom';
import { ArrowLeft } from 'lucide-react';
import { useApp, useAsync } from '@/hooks';
import { mPost, ts } from '@/services/api/mappers';
import { Avatar, Empty, Field, Modal, Photo, SkeletonDetail, Tag } from '@/components/common';
import { ago, fmtDateTime, POST_REASON_LABEL, POST_REPORT_REASONS } from '@/utils';

export function PostReportDetail() {
  const { id } = useParams();
  const [sp] = useSearchParams();
  const nav = useNavigate();
  const { admin, toast } = useApp();
  const [deleting, setDeleting] = useState(null);
  const [d] = useAsync(() => admin.postReportDetail(id), [id]);
  const back = `/admin/post-reports${sp.get('from') ? `?tab=${sp.get('from')}${sp.get('page') ? `&page=${sp.get('page')}` : ''}` : ''}`; // quay lại đúng tab và trang
  if (d === undefined) return <SkeletonDetail label="Đang tải bài đăng bị báo cáo…" />;
  if (!d) return <Empty title="Không tìm thấy bài đăng"><Link to="/admin/post-reports">Về danh sách báo cáo</Link></Empty>;
  const vp = mPost(d.post);
  const author = d.post.author;
  const vReports = d.reports;
  const canResolve = vp.status === 'VISIBLE' && vReports.length > 0;
  const resolve = async (decision, reason, desc, okMsg) => {
    const e = await admin.resolvePost(vp.id, decision, reason, desc);
    if (e) { toast(e, 'err'); return; }
    toast(okMsg);
    nav(back);
  };

  return (
    <div className="stack detail-page">
      <Link to={back} className="back-link"><ArrowLeft size={16} /> Quay lại Báo cáo bài đăng</Link>
      <div className="page-head">
        <div>
          <div className="row gap8 wrap"><h1 className="page-title">Bài đăng bị báo cáo</h1>{vp.status === 'DELETED' ? <Tag tone="gray">Đã xóa</Tag> : <Tag tone={vReports.length ? 'yellow' : 'green'}>{vReports.length ? `${vReports.length} báo cáo chờ xem xét` : 'Đã xử lý'}</Tag>}</div>
        </div>
        {canResolve && <div className="row gap8 page-actions">
          <button className="btn btn-ghost" onClick={() => resolve('KEEP', null, null, 'Đã giữ bài đăng')}>Giữ bài đăng</button>
          <button className="btn btn-danger" onClick={() => setDeleting({ reason: POST_REPORT_REASONS[0], desc: '' })}>Xóa bài đăng</button>
        </div>}
      </div>

      <div className="detail-grid">
        <section className="card pad stack">
          <div className="row gap8"><Avatar user={{ id: String(author.id), name: author.fullName, avatar: author.avatarUrl }} size={36} /><div><b>{author.fullName}</b><div className="muted small">{fmtDateTime(vp.createdAt)}</div></div></div>
          <p className="detail-text">{vp.content}</p>
          {vp.images.length > 0 && <div className={`post-media n${Math.min(vp.images.length, 2)}`}>{vp.images.slice(0, 2).map((im, i) => <Photo key={i} img={im} />)}</div>}
          {vp.status === 'VISIBLE' && <Link to={`/posts/${vp.id}`} className="small">Mở bài đăng trên bảng tin</Link>}
        </section>
        <section className="card pad stack-sm">
          <h3>Báo cáo chờ xem xét ({vReports.length})</h3>
          {vReports.length > 0 && <div className="row gap6 wrap">{Object.entries(d.countByReason || {}).map(([k, n]) => <Tag key={k} tone="red">{POST_REASON_LABEL[k] || k}: {n}</Tag>)}</div>}
          {vReports.length === 0 ? <span className="muted small">Không còn báo cáo chờ xử lý.</span> : vReports.map((r) => <div key={r.id} className="viol-row"><b>{r.reporter?.fullName} – {POST_REASON_LABEL[r.reason] || r.reason}</b><span className="muted small">{r.description || 'Không có mô tả'} • {ago(ts(r.createdAt))}</span></div>)}
        </section>
      </div>

      {deleting && (
        <Modal title="Xóa bài đăng vi phạm" onClose={() => setDeleting(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setDeleting(null)}>Hủy</button>
          <button className="btn btn-danger" onClick={() => resolve('REMOVE', deleting.reason, deleting.desc, 'Đã xóa bài đăng và thông báo cho các bên')}>Xác nhận xóa</button>
        </>}>
          <div className="stack">
            <Field label="Lý do"><select className="input" value={deleting.reason} onChange={(e) => setDeleting({ ...deleting, reason: e.target.value })}>{POST_REPORT_REASONS.map((r) => <option key={r}>{r}</option>)}</select></Field>
            <Field label="Mô tả (nếu có)"><textarea className="input" rows={3} value={deleting.desc} onChange={(e) => setDeleting({ ...deleting, desc: e.target.value })} /></Field>
          </div>
        </Modal>
      )}
    </div>
  );
}
