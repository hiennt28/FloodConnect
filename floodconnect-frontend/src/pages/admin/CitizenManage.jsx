import { useEffect, useState } from 'react';
import { Lock, Unlock } from 'lucide-react';
import { useApp, useAsync, useOnceParam } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { Avatar, Confirm, Modal, SkeletonRows, Tabs, Tag } from '@/components/common';
import { fmtDate, fmtDateTime } from '@/utils';
import { LockModal } from '@/components/admin/LockModal';
import { Pager } from '@/components/admin/Pager';

/* Số người mỗi trang ở tab "Tất cả người dân" */
const PAGE_SIZE = 20;

/* ================= CITIZENS ================= */
export function CitizenManage() {
  const { admin, toast } = useApp();
  const [tab, setTab] = useState('VIOLATING');
  const [view, setView] = useState(null);
  const [locking, setLocking] = useState(false);
  const [unlocking, setUnlocking] = useState(false);
  const opened = useOnceParam('user'); // mở từ thông báo "Tài khoản tự động mở khóa"
  useEffect(() => { if (opened) setView(opened.value); }, [opened]);
  const [list, reloadList] = useAsync(() => (tab === 'ALL' ? Promise.resolve([]) : admin.citizens(tab).then((r) => r || [])), [tab]);
  /* Tab "Tất cả người dân": tìm theo tên (không dấu), email hoặc SĐT, gõ xong 0,3 giây mới tìm; mỗi trang PAGE_SIZE người */
  const [q, setQ] = useState('');
  const [query, setQuery] = useState('');
  const [page, setPage] = useState(0);
  useEffect(() => { const t = setTimeout(() => { setQuery(q.trim()); setPage(0); }, 300); return () => clearTimeout(t); }, [q]);
  const [all, reloadAll] = useAsync(() => (tab === 'ALL' ? admin.citizenSearch(query, page, PAGE_SIZE) : Promise.resolve(null)), [tab, query, page]);
  const [detail, reloadDetail] = useAsync(() => (view ? admin.citizenDetail(view) : Promise.resolve(null)), [view]);
  const refreshAll = () => { reloadList(); reloadAll(); reloadDetail(); };
  const lockedNow = detail?.user?.locked;

  return (
    <div className="stack">
      <h1 className="page-title">Quản lý người dân</h1>
      <Tabs value={tab} onChange={setTab} tabs={[{ value: 'ALL', label: 'Tất cả người dân' }, { value: 'VIOLATING', label: 'Vi phạm chưa khóa' }, { value: 'LOCKED', label: 'Bị khóa tài khoản' }]} />
      {tab === 'ALL' && (
        <>
          <input className="input" type="search" placeholder="Tìm theo tên (có dấu hay không dấu đều được), email hoặc số điện thoại" value={q} maxLength={100}
            onChange={(e) => setQ(e.target.value)} aria-label="Tìm người dân" />
          <div className="card table-wrap">
            <table className="table">
              <thead><tr><th>Người dùng</th><th>Số điện thoại</th><th>Số bài bị xóa</th><th>Trạng thái</th><th /></tr></thead>
              <tbody>
                {all === undefined ? <SkeletonRows cols={5} /> : (!all || all.items.length === 0) && <tr><td colSpan={5} className="center muted">{query ? 'Không tìm thấy người dân phù hợp.' : 'Chưa có người dân nào.'}</td></tr>}
                {(all?.items || []).map((u) => (
                  <tr key={u.userId}>
                    <td><div className="row gap8"><Avatar user={{ id: String(u.userId), name: u.fullName }} size={32} /><div><b>{u.fullName}</b><div className="muted small">{u.email}</div></div></div></td>
                    <td className="small">{u.phone}</td>
                    <td>{u.removedPosts}</td>
                    <td>{u.status === 'LOCKED' ? <Tag tone="red">Bị khóa</Tag> : !u.emailVerified ? <Tag tone="gray">Chưa xác thực email</Tag> : <Tag tone="green">Hoạt động</Tag>}</td>
                    <td className="right"><button className="btn btn-ghost btn-sm" onClick={() => setView(u.userId)}>Xem</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {all && <Pager page={page} size={PAGE_SIZE} total={all.total} last={all.last} unit="người" onChange={setPage} />}
        </>
      )}
      {tab !== 'ALL' && <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>Người dùng</th><th>Số bài bị xóa</th><th>Trạng thái</th><th>{tab === 'LOCKED' ? 'Khóa đến' : 'Lần vi phạm gần nhất'}</th><th /></tr></thead>
          <tbody>
            {list === undefined ? <SkeletonRows cols={5} /> : list.length === 0 && <tr><td colSpan={5} className="center muted">Danh sách trống.</td></tr>}
            {(list || []).map((u) => (
              <tr key={u.userId}>
                <td><div className="row gap8"><Avatar user={{ id: String(u.userId), name: u.fullName }} size={32} /><b>{u.fullName}</b></div></td>
                <td>{u.removedPosts}</td>
                <td><Tag tone={u.status === 'LOCKED' ? 'red' : 'green'}>{u.status === 'LOCKED' ? 'Bị khóa' : 'Hoạt động'}</Tag></td>
                <td className="small">{tab === 'LOCKED' ? (u.lockedUntil ? fmtDate(ts(u.lockedUntil)) : '—') : (u.lastViolationAt ? fmtDateTime(ts(u.lastViolationAt)) : '—')}</td>
                <td className="right"><button className="btn btn-ghost btn-sm" onClick={() => setView(u.userId)}>Xem</button></td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>}
      {view && detail && (
        <Modal wide title={detail.user.fullName} onClose={() => setView(null)} footer={lockedNow
          ? <button className="btn btn-primary" onClick={() => setUnlocking(true)}><Unlock size={16} /> Khôi phục tài khoản</button>
          : <button className="btn btn-danger" onClick={() => setLocking(true)}><Lock size={16} /> Khóa tài khoản</button>}>
          <div className="kv">
            <span>Email</span><b>{detail.user.email}</b><span>Số điện thoại</span><b>{detail.user.phone}</b>
            <span>Trạng thái</span><b>{lockedNow ? `Bị khóa đến ${fmtDate(ts(detail.user.lockedUntil))}` : 'Hoạt động'}</b>
            <span>Số bài bị xóa</span><b>{detail.removedPosts}</b>
          </div>
          <h4 className="mt16">Lịch sử bài đăng bị xử lý</h4>
          {detail.violations.length === 0 ? <span className="muted small">Không có.</span> : detail.violations.map((p) => <div key={p.id} className="viol-row"><p>{p.title}</p><span className="muted small">{fmtDateTime(ts(p.handledAt))} • {p.reason}</span></div>)}
        </Modal>
      )}
      {locking && detail && <LockModal name={detail.user.fullName} onClose={() => setLocking(false)} onLock={async (m, r) => { const e = await admin.lockCitizen(view, m, r); if (!e) { toast('Đã khóa tài khoản'); setLocking(false); refreshAll(); } return e; }} />}
      {unlocking && detail && <Confirm title="Khôi phục tài khoản" text={`Khôi phục tài khoản ${detail.user.fullName}?`} onClose={() => setUnlocking(false)} onOk={async () => { const e = await admin.unlockCitizen(view); toast(e || 'Đã khôi phục tài khoản', e ? 'err' : 'ok'); refreshAll(); }} />}
    </div>
  );
}
