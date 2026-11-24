import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { Bell } from 'lucide-react';
import { useApp } from '@/hooks';
import { Empty } from '@/components/common';
import { ago } from '@/utils';

/* ================= NOTIFICATIONS ================= */
/* 30 thông báo mới nhất đồng bộ sẵn (dùng chung với chuông); thông báo cũ hơn tải theo trang bằng "Tải thêm" và chỉ giữ trên trang này */
export function Notifications() {
  const { state, me, social, load } = useApp();
  const nav = useNavigate();
  const [older, setOlder] = useState({ items: [], next: 1, last: state.notifications.length < 30, loading: false });
  const fresh = new Set(state.notifications.map((n) => n.id));
  const list = [...state.notifications, ...older.items.filter((n) => !fresh.has(n.id))]; // thông báo mới đến làm lệch trang: bỏ trùng
  const loadMore = async () => {
    setOlder((o) => ({ ...o, loading: true }));
    const r = await load.notifications(older.next, me?.role);
    setOlder((o) => (r ? { items: [...o.items, ...r.items], next: o.next + 1, last: r.last, loading: false } : { ...o, loading: false }));
  };
  const open = (n) => {
    social.markRead(n.id);
    setOlder((o) => ({ ...o, items: o.items.map((x) => (x.id === n.id ? { ...x, read: true } : x)) })); // thông báo cũ không nằm trong state chung
    if (n.link) nav(n.link);
  };
  const markAll = () => { social.markAllRead(); setOlder((o) => ({ ...o, items: o.items.map((x) => ({ ...x, read: true })) })); };
  return (
    <div className="narrow stack">
      <div className="row between"><h1 className="page-title">Thông báo</h1><button className="btn btn-ghost btn-sm" onClick={markAll}>Đánh dấu tất cả đã đọc</button></div>
      {list.length === 0 && <Empty icon={<Bell size={34} />} title="Chưa có thông báo nào" />}
      <div className="card list">
        {list.map((n) => (
          <button key={n.id} className={`notif big ${n.read ? '' : 'unread'}`} onClick={() => open(n)}>
            <span className="notif-text">{n.text}</span><span className="muted small">{ago(n.createdAt)}</span>
          </button>
        ))}
      </div>
      {!older.last && list.length > 0 && <button className="btn btn-ghost" disabled={older.loading} onClick={loadMore}>{older.loading ? 'Đang tải…' : 'Tải thêm thông báo cũ hơn'}</button>}
    </div>
  );
}
