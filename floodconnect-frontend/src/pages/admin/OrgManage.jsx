import { useNavigate, useSearchParams } from 'react-router-dom';
import { useApp, useAsync } from '@/hooks';
import { ts } from '@/services/api/mappers';
import { SkeletonRows, Tabs, Tag } from '@/components/common';
import { fmtDate, fmtDateTime, ORG_STATUS, ORG_TYPES } from '@/utils';
import { orgStatusTag } from '@/components/admin/orgStatusTag';

/* Trạng thái hoạt động backend -> khóa của ORG_STATUS */
const OP_KEY = { AVAILABLE: 'READY', BUSY: 'BUSY', INACTIVE: 'INACTIVE' };

/* ================= ORGANIZATIONS ================= */
export function OrgManage() {
  const { admin } = useApp();
  const nav = useNavigate();
  const [sp, setSp] = useSearchParams();
  const tab = ['new', 'active', 'rejected', 'violating', 'locked'].includes(sp.get('tab')) ? sp.get('tab') : 'new';
  const [lists] = useAsync(async () => {
    const [n, a, r, v, l] = await Promise.all([admin.orgs('NEW'), admin.orgs('ACTIVE'), admin.orgs('REJECTED'), admin.orgs('VIOLATING'), admin.orgs('LOCKED')]);
    return { new: n || [], active: a || [], rejected: r || [], violating: v || [], locked: l || [] };
  }, []);
  const open = (id) => nav(`/admin/orgs/${id}?from=${tab}`);
  const rows = lists?.[tab] || [];

  return (
    <div className="stack">
      <h1 className="page-title">Quản lý tổ chức</h1>
      <Tabs value={tab} onChange={(v) => setSp({ tab: v })} tabs={[
        { value: 'new', label: 'Tổ chức mới đăng ký', count: lists?.new.length || 0 },
        { value: 'active', label: 'Đang hoạt động', count: lists?.active.length || 0 },
        { value: 'rejected', label: 'Bị từ chối', count: lists?.rejected.length || 0 },
        { value: 'violating', label: 'Vi phạm chưa khóa', count: lists?.violating.length || 0 },
        { value: 'locked', label: 'Bị khóa tài khoản', count: lists?.locked.length || 0 },
      ]} />
      <div className="card table-wrap">
        <table className="table">
          {tab === 'active' ? (
            /* Mọi tổ chức đã duyệt chưa bị khóa (kể cả không có vi phạm): khu vực, trạng thái hoạt động, tổng vi phạm */
            <><thead><tr><th>Tên tổ chức</th><th>Người đại diện</th><th>Khu vực</th><th>Loại hình</th><th>Hoạt động</th><th>Tổng vi phạm</th><th /></tr></thead>
              <tbody>{!lists ? <SkeletonRows cols={7} /> : rows.length === 0 && <tr><td colSpan={7} className="center muted">Chưa có tổ chức nào đang hoạt động.</td></tr>}
                {rows.map((r) => {
                  const op = ORG_STATUS[OP_KEY[r.operatingStatus]] || ORG_STATUS.INACTIVE;
                  return (
                    <tr key={r.orgId}><td><b>{r.name}</b></td><td>{r.representative}<div className="muted small">{r.phone}</div></td><td className="small">{r.area || '—'}</td>
                      <td className="small">{ORG_TYPES[r.orgType]?.label}</td><td><Tag tone={op.tone}>{op.label}</Tag></td><td>{r.totalViolations}</td>
                      <td className="right"><button className="btn btn-ghost btn-sm" onClick={() => open(r.orgId)}>Xem</button></td></tr>
                  );
                })}</tbody></>
          ) : tab === 'new' || tab === 'rejected' ? (
            <><thead><tr><th>Tên tổ chức</th><th>Người đại diện</th><th>Liên hệ</th><th>Loại hình</th><th>Gửi lúc</th><th>Trạng thái</th><th /></tr></thead>
              <tbody>{!lists ? <SkeletonRows cols={7} /> : rows.length === 0 && <tr><td colSpan={7} className="center muted">Không có hồ sơ nào.</td></tr>}
                {rows.map((r) => (
                  <tr key={r.orgId}><td><b>{r.name}</b></td><td>{r.representative}</td><td className="small">{r.phone}<div className="muted">{r.email}</div></td><td className="small">{ORG_TYPES[r.orgType]?.label}</td><td className="small nowrap">{fmtDateTime(ts(r.createdAt))}</td>
                    <td>{orgStatusTag(r.status)}</td>
                    <td className="right"><button className={`btn btn-sm ${tab === 'new' ? 'btn-primary' : 'btn-ghost'}`} onClick={() => open(r.orgId)}>{tab === 'new' ? 'Xem xét' : 'Xem'}</button></td></tr>
                ))}</tbody></>
          ) : (
            <><thead><tr><th>Tên tổ chức</th><th>Người đại diện</th><th>Bài bị xóa</th><th>Chiến dịch bị xử lý</th><th>Tổng vi phạm</th><th>Trạng thái</th>{tab === 'locked' && <th>Thời điểm khóa</th>}<th /></tr></thead>
              <tbody>{!lists ? <SkeletonRows cols={8} /> : rows.length === 0 && <tr><td colSpan={8} className="center muted">Danh sách trống.</td></tr>}
                {rows.map((r) => (
                  <tr key={r.orgId}><td><b>{r.name}</b></td><td>{r.representative}</td><td>{r.removedPosts}</td><td>{r.stoppedCampaigns}</td><td><b>{r.totalViolations}</b></td>
                    <td>{orgStatusTag(r.status)}</td>
                    {tab === 'locked' && <td className="small nowrap">{r.lockedAt ? fmtDate(ts(r.lockedAt)) : '—'}</td>}
                    <td className="right"><button className="btn btn-ghost btn-sm" onClick={() => open(r.orgId)}>Xem</button></td></tr>
                ))}</tbody></>
          )}
        </table>
      </div>
    </div>
  );
}
