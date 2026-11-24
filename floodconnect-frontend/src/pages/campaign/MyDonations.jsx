import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { useApp, useOnceParam } from '@/hooks';
import { Tag } from '@/components/common';
import { DONATION_STATUS, fmtDateTime, money } from '@/utils';

/* ================= MY DONATIONS ================= */
export function MyDonations() {
  const { state, me } = useApp();
  const [status, setStatus] = useState('');
  const focus = useOnceParam('donation'); // mở từ thông báo "Ủng hộ thành công"
  useEffect(() => {
    if (!focus) return undefined;
    setStatus('');
    const t = requestAnimationFrame(() => document.getElementById(`donation-${focus.value}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }));
    return () => cancelAnimationFrame(t);
  }, [focus]);
  const list = state.donations.filter((d) => !status || d.status === status).sort((a, b) => b.createdAt - a.createdAt);
  const total = state.donations.filter((d) => d.status === 'SUCCESS').reduce((sum, d) => sum + d.amount, 0);
  return (
    <div className="stack">
      <div className="page-head"><h1 className="page-title">Lịch sử đóng góp của tôi</h1>
        <select className="input sm" value={status} onChange={(e) => setStatus(e.target.value)}><option value="">Tất cả trạng thái</option>{Object.entries(DONATION_STATUS).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
      </div>
      <div className="card table-wrap">
        <table className="table">
          <thead><tr><th>Chiến dịch</th><th className="right">Số tiền</th><th>Lời nhắn</th><th>Phương thức</th><th>Mã giao dịch</th><th>Thời gian</th><th>Trạng thái</th></tr></thead>
          <tbody>
            {list.length === 0 && <tr><td colSpan={7} className="center muted">Bạn chưa có giao dịch nào.</td></tr>}
            {list.map((d) => {
              return (
                <tr key={focus?.value === d.id ? `${d.id}-${focus.key}` : d.id} id={`donation-${d.id}`} className={focus?.value === d.id ? 'row-focus' : ''}>
                  <td><Link to={`/campaigns/${d.campaignId}`}><b>{d.campaignName}</b></Link></td>
                  <td className="right money nowrap">{money(d.amount)}</td>
                  <td className="muted small">{d.message || '—'}</td>
                  <td>{d.method === 'MOMO' ? 'MoMo' : 'VNPay'}</td>
                  <td>{d.txCode ? `#${d.txCode}` : '—'}</td>
                  <td className="nowrap small">{fmtDateTime(d.createdAt)}</td>
                  <td><Tag tone={DONATION_STATUS[d.status].tone}>{DONATION_STATUS[d.status].label}</Tag></td>
                </tr>
              );
            })}
          </tbody>
          <tfoot><tr><td><b>Tổng cộng (giao dịch thành công)</b></td><td className="right money nowrap"><b>{money(total)}</b></td><td colSpan={5} /></tr></tfoot>
        </table>
      </div>
    </div>
  );
}
