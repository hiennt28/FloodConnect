import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { MoreHorizontal, Pencil, Trash2 } from 'lucide-react';
import { useApp } from '@/hooks';
import { Avatar, Confirm } from '@/components/common';
import { ago } from '@/utils';
import { AuthorName } from './AuthorName';

/* ---------- Một bình luận ---------- */
export function CommentItem({ c, post, replies, depth = 0, focus }) {
  const { state, me, social, toast, load } = useApp();
  const nav = useNavigate();
  const author = state.users.find((u) => u.id === c.authorId) || { id: c.authorId, name: 'Người dùng' };
  const [editing, setEditing] = useState(false);
  const [text, setText] = useState(c.content);
  const [replyOpen, setReplyOpen] = useState(false);
  const [reply, setReply] = useState('');
  const [showReplies, setShowReplies] = useState(false);
  /* Phản hồi chỉ tải khi mở (10 mỗi lần, cũ trước). next: trang sẽ tải tiếp; last: máy chủ đã hết phản hồi */
  const [rPage, setRPage] = useState({ next: 0, last: false, loading: false });
  const [err, setErr] = useState('');
  const [confirm, setConfirm] = useState(false);
  const [menu, setMenu] = useState(false);

  const deleted = c.status === 'DELETED';
  const focused = focus?.value === c.id;
  const replyTotal = Math.max(c.replyCount || 0, replies.length); // số phản hồi do máy chủ đếm, kể cả phản hồi chưa tải
  const loadReplies = async () => {
    setRPage((s) => ({ ...s, loading: true }));
    const r = await load.replies(c.id, rPage.next);
    setRPage((s) => ({ next: s.next + 1, last: r ? r.last : true, loading: false }));
  };
  const openReplies = () => { setShowReplies(true); if (rPage.next === 0) loadReplies(); };
  /* Mở từ thông báo phản hồi: phản hồi đích đã được PostCard tải riêng, chỉ cần mở danh sách phản hồi */
  useEffect(() => {
    if (focus && replies.some((r) => r.id === focus.value)) setShowReplies(true);
  }, [focus?.key, replies.length]); // eslint-disable-line react-hooks/exhaustive-deps
  const isOwner = me?.id === c.authorId;
  const canDelete = me && (isOwner || me.id === post.authorId || me.role === 'ADMIN');

  const startReply = () => {
    if (!me) { nav('/login'); return; }
    setReplyOpen(true);
    setReply(`@${author?.name || ''} `);
  };

  return (
    <div id={`comment-${c.id}`} key={focused ? focus.key : undefined} className={`comment ${depth ? 'reply' : ''} ${focused ? 'flash' : ''}`}>
      {deleted ? <div className="comment-deleted">Bình luận đã bị xóa</div> : (
        <div className="row gap10 top">
          <Avatar user={author} size={depth ? 28 : 34} />
          <div className="grow">
            {editing ? (
              <div className="stack-sm">
                <textarea className="input" rows={2} value={text} onChange={(e) => setText(e.target.value)} />
                {err && <span className="field-error">{err}</span>}
                <div className="row gap8">
                  <button className="btn btn-primary btn-sm" onClick={async () => { const r = await social.editComment(c.id, text); if (r) setErr(r); else { setEditing(false); setErr(''); toast('Đã lưu bình luận'); } }}>Lưu</button>
                  <button className="btn btn-ghost btn-sm" onClick={() => { setEditing(false); setText(c.content); setErr(''); }}>Hủy</button>
                </div>
              </div>
            ) : (
              <div className="bubble">
                <div className="row between"><AuthorName user={author} />
                  {(isOwner || canDelete) && (
                    <div className="pop-anchor">
                      <button className="icon-btn sm" aria-label="Tùy chọn" onClick={() => setMenu(!menu)}><MoreHorizontal size={16} /></button>
                      {menu && <div className="popover menu-pop right" onMouseLeave={() => setMenu(false)}>
                        {isOwner && <button onClick={() => { setEditing(true); setMenu(false); }}><Pencil size={14} /> Chỉnh sửa</button>}
                        {canDelete && <button className="danger" onClick={() => { setConfirm(true); setMenu(false); }}><Trash2 size={14} /> Xóa</button>}
                      </div>}
                    </div>
                  )}
                </div>
                <p>{c.content}</p>
              </div>
            )}
            <div className="comment-meta">
              <span>{ago(c.createdAt)}</span>
              {c.updatedAt && <span>Đã chỉnh sửa</span>}
              {!depth && post.status === 'VISIBLE' && <button className="link" onClick={startReply}>Trả lời</button>}
            </div>
          </div>
        </div>
      )}

      {!depth && replyTotal > 0 && (
        <div className="replies">
          {!showReplies ? <button className="link small" onClick={openReplies}>Xem {replyTotal} phản hồi</button> : (
            <>
              {replies.map((r) => <CommentItem key={r.id} c={r} post={post} replies={[]} depth={1} focus={focus} />)}
              {rPage.loading && <span className="muted small">Đang tải phản hồi…</span>}
              {!rPage.loading && !rPage.last && replies.length < replyTotal && (
                <button className="link small" onClick={loadReplies}>Xem thêm phản hồi ({replyTotal - replies.length})</button>
              )}
            </>
          )}
        </div>
      )}
      {replyOpen && (
        <div className="replies">
          <div className="row gap8">
            <input className="input grow" autoFocus value={reply} onChange={(e) => setReply(e.target.value)} placeholder="Viết phản hồi..." />
            <button className="btn btn-primary btn-sm" onClick={async () => { const r = await social.addComment(post.id, c.id, reply); if (r) setErr(r); else { setReply(''); setReplyOpen(false); openReplies(); setErr(''); } }}>Gửi</button>
            <button className="btn btn-ghost btn-sm" onClick={() => setReplyOpen(false)}>Hủy</button>
          </div>
          {err && !editing && <span className="field-error">{err}</span>}
        </div>
      )}
      {confirm && <Confirm title="Xóa bình luận" text="Bạn có chắc muốn xóa bình luận này không?" okText="Xóa" danger onClose={() => setConfirm(false)} onOk={async () => { const r = await social.deleteComment(c.id); toast(r || 'Đã xóa bình luận', r ? 'err' : 'ok'); }} />}
    </div>
  );
}
