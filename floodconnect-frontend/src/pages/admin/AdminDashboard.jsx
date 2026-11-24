import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { Building2, HandHeart, Siren, Users } from 'lucide-react';
import { useApp, useTheme } from '@/hooks';
import { SkeletonChart } from '@/components/common';
import { SOS_STATUS_TO_API } from '@/services/api/mappers';
import { cssVar, fmtMinutes, money, ORG_STATUS, SEVERITY, SOS_STATUS } from '@/utils';

const mil = (v) => (v >= 1e6 ? `${+(v / 1e6).toFixed(1)} tr` : v.toLocaleString('vi-VN'));
const shortName = (s) => (s.length > 16 ? `${s.slice(0, 15)}…` : s);
/* Ngày từ backend dạng "2026-11-10" (LocalDate) → "10/11" trên trục, "10/11/2026" trong tooltip */
const dayParts = (d) => String(d).split('-');
const dayShort = (d) => { const [, m, dd] = dayParts(d); return `${dd}/${m}`; };
const dayFull = (d) => { const [y, m, dd] = dayParts(d); return `${dd}/${m}/${y}`; };

/* Ba chỉ số thời gian; count = số ca/lần dùng để tính, để người xem biết con số dựa trên bao nhiêu dữ liệu */
function RespStat({ label, hint, stat, unit = 'ca' }) {
  return (
    <div className="resp-stat" title={hint}>
      <span>{label}</span>
      <b>{fmtMinutes(stat?.avgMinutes)}</b>
      <small className="muted">{stat?.count ? `trung bình trên ${stat.count} ${unit}` : 'chưa có dữ liệu'}</small>
    </div>
  );
}

export function AdminDashboard() {
  const { admin } = useApp();
  useTheme(); // vẽ lại biểu đồ với màu mới khi đổi sáng/tối (màu đọc từ biến CSS bằng cssVar)
  const [range, setRange] = useState('month');
  const [data, setData] = useState(null);
  const [days, setDays] = useState(30);
  const [timeline, setTimeline] = useState(undefined); // undefined: đang tải lần đầu, null: lỗi

  useEffect(() => { admin.stats(range).then(setData); }, [range]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { admin.timeline(days).then(setTimeline); }, [days]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Màu lấy từ biến CSS của theme (đổi theo sáng/tối); tham số thứ hai là màu dự phòng */
  const c = { river: cssVar('--river', '#0e5868'), red: cssVar('--red', '#b42318'), green: cssVar('--green', '#2e7d4f'), yellow: cssVar('--yellow', '#8f6108'), orange: cssVar('--orange', '#c2410c'), faint: cssVar('--faint', '#8a979b'), muted: cssVar('--muted', '#5b6a70') };
  const STATUS_COLORS = { PENDING: c.yellow, ASSIGNED: c.orange, ACCEPTED: c.river, DONE: c.green, CANCELLED: c.faint, INVALID: c.muted };
  const citizenData = [
    { name: 'Đang hoạt động', value: data?.citizens?.ACTIVE || 0, color: c.river },
    { name: 'Đang bị khóa', value: data?.citizens?.LOCKED || 0, color: c.red },
  ];
  const sosStatusData = Object.entries(SOS_STATUS).map(([k, v]) => ({ name: v.label, value: data?.sosStatus?.[SOS_STATUS_TO_API[k] || k] || 0, color: STATUS_COLORS[k] }));
  const sosData = Object.entries(SEVERITY).map(([k, v]) => ({ name: v.label, value: data?.sos?.[k] || 0, color: cssVar(`--sev-${k}`, v.color) }));
  const o = data?.orgs || {};
  const orgData = [
    { name: 'Chờ duyệt', value: o.PENDING || 0, color: c.yellow },
    { name: 'Đã duyệt', value: o.ACTIVE || 0, color: c.green },
    { name: 'Bị khóa', value: o.LOCKED || 0, color: c.red },
    { name: 'Bị từ chối', value: o.REJECTED || 0, color: c.faint },
  ];
  const opMap = o.activeByOperatingStatus || {};
  const topOrgs = (data?.topOrgs || []).map((x) => ({ name: shortName(x.name), value: x.totalAmount }));
  const topUsers = (data?.topDonors || []).map((x) => ({ name: shortName(x.name), value: x.totalAmount }));
  const ov = data?.overview;
  /* Số bài + chiến dịch đang có báo cáo chờ xử lý, máy chủ đếm trên toàn bộ dữ liệu (không đếm danh sách đã tải) */
  const reports = ov ? ov.pendingReportedPosts + ov.pendingReportedCampaigns : null;
  const lineNew = cssVar('--rescue', '#d9480f');
  const lineDone = cssVar('--green', '#2e7d4f');
  const tip = (v) => money(v);

  return (
    <div className="stack">
      <h1 className="page-title">Tổng quan hệ thống</h1>
      <div className="kpis">
        <Link to="/admin/sos" className="kpi red"><Siren /><span>Ca SOS đang mở</span><b>{ov ? ov.openSos : '—'}</b></Link>
        <Link to="/admin/orgs" className="kpi amber"><Building2 /><span>Hồ sơ tổ chức chờ duyệt</span><b>{data ? o.PENDING || 0 : '—'}</b></Link>
        <Link to="/admin/post-reports" className="kpi blue"><Users /><span>Báo cáo chờ xử lý</span><b>{reports ?? '—'}</b></Link>
        <div className="kpi green"><HandHeart /><span>Tổng tiền ủng hộ</span><b>{ov ? money(ov.totalRaised) : '—'}</b></div>
      </div>
      <div className="chart-grid">
        <div className="card pad chart-wide">
          <div className="row between wrap"><h3>Ca SOS theo ngày</h3>
            <select className="input sm" aria-label="Khoảng thời gian" value={days} onChange={(e) => setDays(Number(e.target.value))}><option value={7}>7 ngày qua</option><option value={30}>30 ngày qua</option><option value={90}>90 ngày qua</option></select></div>
          {timeline === undefined ? <SkeletonChart height={300} /> : timeline === null ? <div className="empty"><strong>Không tải được thống kê theo ngày</strong></div> : (
            <>
              <div className="resp-stats">
                <RespStat label="Chờ đến khi có tổ chức phụ trách" hint="Từ lúc gửi SOS đến lần phân công đầu tiên (Admin chỉ định hoặc tổ chức tự nhận)" stat={timeline.untilAssigned} />
                <RespStat label="Tổ chức phản hồi chỉ định" hint="Từ lúc Admin chỉ định đến khi tổ chức nhận hoặc từ chối; không tính lần hết hạn không phản hồi" stat={timeline.orgResponse} unit="lần chỉ định" />
                <RespStat label="Gửi SOS đến hoàn thành" hint="Chỉ tính các ca đã hoàn thành" stat={timeline.untilCompleted} />
              </div>
              <ResponsiveContainer width="100%" height={300}>
                <LineChart data={timeline.points} margin={{ top: 8, right: 12, left: -8, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" vertical={false} />
                  <XAxis dataKey="date" tickFormatter={dayShort} minTickGap={18} />
                  <YAxis allowDecimals={false} />
                  <Tooltip labelFormatter={dayFull} />
                  <Legend />
                  <Line type="monotone" dataKey="created" name="Ca mới gửi" stroke={lineNew} strokeWidth={2} dot={days <= 30 ? { r: 2.5 } : false} />
                  <Line type="monotone" dataKey="completed" name="Ca hoàn thành" stroke={lineDone} strokeWidth={2} dot={days <= 30 ? { r: 2.5 } : false} />
                </LineChart>
              </ResponsiveContainer>
              <span className="muted small">Giờ Việt Nam, khoảng {timeline.days} ngày tính cả hôm nay. Ca hoàn thành được đếm theo ngày hoàn thành; ba chỉ số thời gian tính trên các ca gửi trong cùng khoảng.</span>
            </>
          )}
        </div>
        <div className="card pad"><h3>Người dân theo trạng thái tài khoản</h3>
          {!data ? <SkeletonChart /> : <ResponsiveContainer width="100%" height={260}><PieChart><Pie data={citizenData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} label>{citizenData.map((d) => <Cell key={d.name} fill={d.color} />)}</Pie><Tooltip /><Legend /></PieChart></ResponsiveContainer>}
        </div>
        <div className="card pad"><div className="row between"><h3>Ca SOS theo mức độ nguy hiểm</h3>
          <select className="input sm" value={range} onChange={(e) => setRange(e.target.value)}><option value="day">Hôm nay</option><option value="month">Tháng này</option><option value="year">Năm nay</option></select></div>
          {!data ? <SkeletonChart /> : <ResponsiveContainer width="100%" height={260}><BarChart data={sosData}><CartesianGrid strokeDasharray="3 3" vertical={false} /><XAxis dataKey="name" /><YAxis allowDecimals={false} /><Tooltip /><Bar dataKey="value" name="Số ca" radius={[6, 6, 0, 0]}>{sosData.map((d) => <Cell key={d.name} fill={d.color} />)}</Bar></BarChart></ResponsiveContainer>}
        </div>
        <div className="card pad"><h3>Tổ chức cứu hộ theo trạng thái tài khoản</h3>
          {!data ? <SkeletonChart height={240} /> : <ResponsiveContainer width="100%" height={240}><PieChart><Pie data={orgData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} label>{orgData.map((d) => <Cell key={d.name} fill={d.color} />)}</Pie><Tooltip /><Legend /></PieChart></ResponsiveContainer>}
          <span className="muted small">Trong nhóm hoạt động: {Object.entries(ORG_STATUS).map(([k, v]) => `${v.label} ${opMap[{ READY: 'AVAILABLE', BUSY: 'BUSY', INACTIVE: 'INACTIVE' }[k]] || 0}`).join(' • ')}</span>
        </div>
        <div className="card pad"><div className="row between"><h3>Ca SOS theo trạng thái</h3><span className="muted small">theo bộ lọc thời gian bên cạnh biểu đồ mức độ</span></div>
          <ResponsiveContainer width="100%" height={260}><PieChart><Pie data={sosStatusData} dataKey="value" nameKey="name" innerRadius={55} outerRadius={90} label>{sosStatusData.map((d) => <Cell key={d.name} fill={d.color} />)}</Pie><Tooltip /><Legend /></PieChart></ResponsiveContainer>
        </div>
        <div className="card pad"><h3>Top 10 tổ chức nhận ủng hộ nhiều nhất</h3>
          {!data ? <SkeletonChart /> : <ResponsiveContainer width="100%" height={260}><BarChart data={topOrgs} layout="vertical" margin={{ left: 20 }}><CartesianGrid strokeDasharray="3 3" horizontal={false} /><XAxis type="number" tickFormatter={mil} /><YAxis type="category" dataKey="name" width={120} /><Tooltip formatter={tip} /><Bar dataKey="value" name="Số tiền" fill={c.river} radius={[0, 6, 6, 0]} /></BarChart></ResponsiveContainer>}
        </div>
        <div className="card pad"><h3>Top 10 người dân đóng góp nhiều nhất</h3>
          {!data ? <SkeletonChart /> : <ResponsiveContainer width="100%" height={260}><BarChart data={topUsers}><CartesianGrid strokeDasharray="3 3" vertical={false} /><XAxis dataKey="name" /><YAxis tickFormatter={mil} /><Tooltip formatter={tip} /><Bar dataKey="value" name="Số tiền" fill={c.muted} radius={[6, 6, 0, 0]} /></BarChart></ResponsiveContainer>}
        </div>
      </div>
    </div>
  );
}
