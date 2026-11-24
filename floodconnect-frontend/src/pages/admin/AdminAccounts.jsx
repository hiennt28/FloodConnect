import { useState } from 'react';
import { Navigate } from 'react-router-dom';
import { Plus } from 'lucide-react';
import { useApp, useAsync } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { Avatar, SkeletonRows, Tag } from '@/components/common';
import { CreateAdminModal } from '@/components/admin/CreateAdminModal';
import { DisableAdminModal } from '@/components/admin/DisableAdminModal';
import { fmtDate, fmtDateTime } from '@/utils';

/* Trạng thái tài khoản Admin do backend tính: ACTIVE | INVITED (chưa đặt mật khẩu lần đầu) | DISABLED (bị Admin gốc vô hiệu hóa) */
const STATE = {
  ACTIVE: { tone: 'green', label: 'Đang hoạt động' },
  INVITED: { tone: 'orange', label: 'Chờ đặt mật khẩu' },
  DISABLED: { tone: 'red', label: 'Bị vô hiệu hóa' },
};

/* ================= QUẢN LÝ ADMIN (chỉ Admin gốc) ================= */
export function AdminAccounts() {
  const { me, admin, toast } = useApp();
  const root = !!me?.rootAdmin;
  const [list, reload] = useAsync(() => (root ? admin.admins() : Promise.resolve(null)), [root]);
  const [creating, setCreating] = useState(false);
  const [disabling, setDisabling] = useState(null); // dòng Admin đang mở hộp vô hiệu hóa
  const [busyId, setBusyId] = useState(null);       // dòng đang gửi lại mã / mở lại: khóa nút, tránh bấm hai lần
  /* Admin thường không có trang này (backend cũng trả 403): đưa về trang tổng quan */
  if (!root) return <Navigate to="/admin" replace />;

  const act = async (a, fn, okText) => {
    setBusyId(a.id);
    const err = await fn(a.id);
    setBusyId(null);
    toast(err || okText, err ? 'err' : 'ok');
    if (!err) reload();
  };

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1 className="page-title">Quản lý Admin</h1>
          <p className="muted small">Chỉ Admin gốc dùng được trang này. Admin mới tự đặt mật khẩu bằng mã gửi tới email của họ; Admin gốc không biết mật khẩu của người khác.</p>
        </div>
        <button className="btn btn-primary" onClick={() => setCreating(true)}><Plus size={16} /> Tạo Admin</button>
      </div>

      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>Quản trị viên</th><th>Số điện thoại</th><th>Trạng thái</th><th>Ngày tạo</th><th><span className="sr-only">Thao tác</span></th></tr></thead>
          <tbody>
            {list === undefined && <SkeletonRows cols={5} />}
            {list === null && <tr><td colSpan={5} className="center muted">Không tải được danh sách Admin. Hãy tải lại trang.</td></tr>}
            {(list || []).map((a) => {
              const st = STATE[a.state] || STATE.ACTIVE;
              const busy = busyId === a.id;
              return (
                <tr key={a.id}>
                  <td><div className="row gap8"><Avatar user={{ id: String(a.id), name: a.fullName }} size={32} /><div><b>{a.fullName}</b><div className="muted small">{a.email}</div></div></div></td>
                  <td className="small">{a.phone}</td>
                  <td>
                    <div className="row gap6 wrap">{a.rootAdmin && <Tag tone="blue">Admin gốc</Tag>}<Tag tone={st.tone}>{st.label}</Tag></div>
                    {a.state === 'DISABLED' && <div className="muted small">{fmtDateTime(ts(a.disabledAt))} • {a.disabledReason}</div>}
                    {a.state === 'INVITED' && <div className="muted small">Đã gửi mã tới email, chưa đặt mật khẩu</div>}
                  </td>
                  <td className="small">{fmtDate(ts(a.createdAt))}</td>
                  {/* Tối đa một nút mỗi dòng; Admin gốc không có nút (không ai vô hiệu hóa được Admin gốc) */}
                  <td className="right">
                    {!a.rootAdmin && a.state === 'INVITED' && <button className="btn btn-soft btn-sm" disabled={busy}
                      onClick={() => act(a, admin.resendAdminInvite, `Đã gửi lại mã đặt mật khẩu tới ${a.email}`)}>Gửi lại mã</button>}
                    {!a.rootAdmin && a.state === 'DISABLED' && <button className="btn btn-soft btn-sm" disabled={busy}
                      onClick={() => act(a, admin.enableAdmin, `Đã mở lại tài khoản ${a.fullName}`)}>Mở lại</button>}
                    {!a.rootAdmin && a.state === 'ACTIVE' && <button className="btn btn-danger-soft btn-sm" onClick={() => setDisabling(a)}>Vô hiệu hóa</button>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>

      {creating && <CreateAdminModal onClose={() => setCreating(false)} onSave={async (f) => {
        const r = await admin.createAdmin(f);
        if (r.ok) { toast(`Đã tạo tài khoản và gửi mã đặt mật khẩu tới ${f.email.trim()}`); setCreating(false); reload(); }
        return r;
      }} />}
      {disabling && <DisableAdminModal name={disabling.fullName} onClose={() => setDisabling(null)} onSave={async (reason) => {
        const err = await admin.disableAdmin(disabling.id, reason);
        if (err) return err;
        toast(`Đã vô hiệu hóa tài khoản ${disabling.fullName}`);
        setDisabling(null);
        reload();
        return null;
      }} />}
    </div>
  );
}
