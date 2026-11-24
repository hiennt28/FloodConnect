import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Flag, Heart, MapPin, MessageCircle, MoreHorizontal, Pencil, Share2, Trash2 } from 'lucide-react';
import { useApp } from '@/hooks';
import { Avatar, Confirm, Field, Modal, Photo } from '@/components/common';
import { ago, POST_REPORT_REASONS } from '@/utils';
import { AuthorName } from './AuthorName';
import { PostComposer } from './PostComposer';
import { CommentItem } from './CommentItem';

/* ---------- Thẻ bài đăng ---------- */
/* focus = { value: mã bình luận, key }: mở từ thông báo, cuộn tới và làm nổi bật bình luận đó */
export default function PostCard({ post, expanded, focus }) {
  const { state, me, social, toast, load } = useApp();
  const nav = useNavigate();
  const author = state.users.find((u) => u.id === post.authorId);
  const liked = !!(me && post.likedByMe);
  const allComments = state.comments.filter((c) => c.postId === post.id);
  const roots = allComments.filter((c) => !c.parentId).sort((a, b) => a.createdAt - b.createdAt);
  const visibleCount = post.commentCount;
  const reported = !!(me && post.reportedByMe);

  const [showComments, setShowComments] = useState(!!expanded);
  const [text, setText] = useState('');
  const [cErr, setCErr] = useState('');
  const [menu, setMenu] = useState(false);
  const [editing, setEditing] = useState(false);
  const [confirmDel, setConfirmDel] = useState(false);
  const [report, setReport] = useState(null);
  const [commentsLoaded, setCommentsLoaded] = useState(false);
  /* Bình luận gốc theo trang: 20 mới nhất trước, "Xem bình luận cũ hơn" tải trang tiếp theo */
  const [cPage, setCPage] = useState({ next: 0, last: false, loading: false });
  const loadMoreComments = async (page) => {
    setCPage((s) => ({ ...s, loading: true }));
    const r = await load.comments(post.id, page);
    setCPage({ next: page + 1, last: r ? r.last : true, loading: false });
    return r;
  };

  useEffect(() => { if (showComments) loadMoreComments(0).then(() => setCommentsLoaded(true)); }, [showComments, post.id]); // eslint-disable-line react-hooks/exhaustive-deps
  /* Mở từ thông báo (?comment=): bình luận đích có thể nằm ở trang cũ hơn, hoặc là phản hồi chưa tải. Khi đó tải riêng bình luận
     đó (và bình luận gốc của nó nếu là phản hồi) để hiện ngay; CommentItem tự mở phần phản hồi, nên chờ vài khung hình */
  useEffect(() => {
    if (!focus || !commentsLoaded) return undefined;
    let alive = true;
    let frame = 0;
    (async () => {
      const target = state.comments.find((c) => c.id === focus.value) || await load.comment(focus.value);
      if (target?.parentId && !state.comments.some((c) => c.id === target.parentId)) await load.comment(target.parentId);
      if (!alive) return;
      if (!target || target.status === 'DELETED') { toast('Bình luận này không còn hiển thị (có thể đã bị xóa).', 'err'); return; }
      let tries = 0;
      const tryScroll = () => {
        const el = document.getElementById(`comment-${focus.value}`);
        if (el) el.scrollIntoView?.({ behavior: 'smooth', block: 'center' });
        else if (tries++ < 30) frame = requestAnimationFrame(tryScroll);
      };
      frame = requestAnimationFrame(tryScroll);
    })();
    return () => { alive = false; cancelAnimationFrame(frame); };
  }, [focus?.key, commentsLoaded]); // eslint-disable-line react-hooks/exhaustive-deps

  const needLogin = () => { nav('/login'); };
  const share = async () => {
    const url = `${window.location.origin}/posts/${post.id}`;
    if (navigator.share) { try { await navigator.share({ title: 'FloodConnect', url }); return; } catch { /* hủy */ } }
    try { await navigator.clipboard.writeText(url); toast('Đã sao chép liên kết bài viết'); } catch { toast(url); }
  };

  return (
    <article className="card post">
      <div className="post-head">
        <Avatar user={author} />
        <div className="grow">
          <AuthorName user={author} />
          <div className="muted small row gap6">
            <Link to={`/posts/${post.id}`} className="muted">{ago(post.createdAt)}</Link>
            {post.updatedAt && <span>• Đã chỉnh sửa</span>}
            {post.location && <a className="row gap4 loc-link" href={`https://www.google.com/maps/search/?api=1&query=${post.location.lat},${post.location.lng}`} target="_blank" rel="noopener noreferrer" title="Mở vị trí này trên Google Maps"><MapPin size={13} />{post.location.label || `${post.location.lat}, ${post.location.lng}`}</a>}
          </div>
        </div>
        {me && (
          <div className="pop-anchor">
            <button className="icon-btn" aria-label="Tùy chọn bài viết" onClick={() => setMenu(!menu)}><MoreHorizontal size={18} /></button>
            {menu && (
              <div className="popover menu-pop right" onMouseLeave={() => setMenu(false)}>
                {me.id === post.authorId ? (
                  <>
                    <button onClick={() => { setEditing(true); setMenu(false); }}><Pencil size={15} /> Chỉnh sửa</button>
                    <button className="danger" onClick={() => { setConfirmDel(true); setMenu(false); }}><Trash2 size={15} /> Xóa bài</button>
                  </>
                ) : (
                  <button disabled={reported} onClick={() => { setReport({ reason: POST_REPORT_REASONS[0], desc: '' }); setMenu(false); }}><Flag size={15} /> {reported ? 'Đã báo cáo' : 'Báo cáo bài viết'}</button>
                )}
              </div>
            )}
          </div>
        )}
      </div>

      {editing ? <PostComposer initial={post} onDone={() => setEditing(false)} onCancel={() => setEditing(false)} /> : (
        <>
          <p className="post-body">{post.content}</p>
          {post.images.length > 0 && (
            <div className={`post-media n${Math.min(post.images.length, 3)}`}>
              {post.images.slice(0, 3).map((im, i) => (
                <Photo key={i} img={im}>{i === 2 && post.images.length > 3 ? <span>+{post.images.length - 3}</span> : null}</Photo>
              ))}
            </div>
          )}
        </>
      )}

      <div className="post-stats muted small">
        <span>{post.likeCount > 0 && <><Heart size={13} className="heart-mini" /> {post.likeCount} lượt thích</>}</span>
        <button className="link muted" onClick={() => setShowComments(!showComments)}>{visibleCount} bình luận</button>
      </div>
      <div className="post-actions">
        <button className={`act ${liked ? 'liked' : ''}`} onClick={async () => { if (!me) return needLogin(); const r = await social.toggleLike(post.id); if (r) toast(r, 'err'); }} aria-pressed={!!liked}>
          <Heart size={18} fill={liked ? 'currentColor' : 'none'} /> Thích
        </button>
        <button className="act" onClick={() => setShowComments(true)}><MessageCircle size={18} /> Bình luận</button>
        <button className="act" onClick={share}><Share2 size={18} /> Chia sẻ</button>
      </div>

      {showComments && (
        <div className="comments">
          {/* Bình luận hiện cũ trước, mới sau; trang đầu là 20 bình luận mới nhất nên nút xem bình luận cũ hơn nằm trên cùng */}
          {commentsLoaded && !cPage.last && (
            <button className="link small" disabled={cPage.loading} onClick={() => loadMoreComments(cPage.next)}>{cPage.loading ? 'Đang tải…' : 'Xem bình luận cũ hơn'}</button>
          )}
          {roots.map((c) => {
            const replies = allComments.filter((r) => r.parentId === c.id && r.status === 'VISIBLE').sort((a, b) => a.createdAt - b.createdAt);
            /* Phản hồi chỉ tải khi mở: bình luận đã xóa vẫn hiện nếu máy chủ báo còn phản hồi (replyCount) */
            if (c.status === 'DELETED' && replies.length === 0 && !c.replyCount) return null;
            return <CommentItem key={c.id} c={c} post={post} replies={replies} focus={focus} />;
          })}
          {me ? (
            <div className="row gap10 top">
              <Avatar user={me} size={32} />
              <div className="grow">
                <div className="comment-input">
                  <input className="input" value={text} placeholder="Viết bình luận hoặc thông tin cập nhật..." onChange={(e) => setText(e.target.value)}
                    onKeyDown={async (e) => { if (e.key === 'Enter') { const r = await social.addComment(post.id, null, text); if (r) setCErr(r); else { setText(''); setCErr(''); } } }} />
                  <button className="btn btn-primary btn-sm" onClick={async () => { const r = await social.addComment(post.id, null, text); if (r) setCErr(r); else { setText(''); setCErr(''); } }}>Gửi</button>
                </div>
                {cErr && <span className="field-error">{cErr}</span>}
              </div>
            </div>
          ) : <button className="btn btn-soft btn-sm" onClick={needLogin}>Đăng nhập để bình luận</button>}
        </div>
      )}

      {confirmDel && <Confirm title="Xóa bài viết" text="Bạn có chắc muốn xóa bài viết này không? Sau khi xóa sẽ không thể khôi phục." okText="Xóa" danger onClose={() => setConfirmDel(false)}
        onOk={async () => { const r = await social.deletePost(post.id); toast(r || 'Đã xóa bài viết', r ? 'err' : 'ok'); if (!r && expanded) nav('/feed'); }} />}
      {report && (
        <Modal title="Báo cáo bài viết" onClose={() => setReport(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setReport(null)}>Hủy</button>
          <button className="btn btn-danger" onClick={async () => { const r = await social.reportPost(post.id, report.reason, report.desc); toast(r || 'Đã ghi nhận báo cáo của bạn', r ? 'err' : 'ok'); setReport(null); }}>Gửi báo cáo</button>
        </>}>
          <div className="stack">
            <div className="stack-sm">
              {POST_REPORT_REASONS.map((r) => (
                <label key={r} className="radio-line"><input type="radio" checked={report.reason === r} onChange={() => setReport({ ...report, reason: r })} /> {r}</label>
              ))}
            </div>
            <Field label="Mô tả thêm (không bắt buộc)"><textarea className="input" rows={3} value={report.desc} onChange={(e) => setReport({ ...report, desc: e.target.value })} /></Field>
          </div>
        </Modal>
      )}
    </article>
  );
}
