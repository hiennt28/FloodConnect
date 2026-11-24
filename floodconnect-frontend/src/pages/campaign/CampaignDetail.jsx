import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CalendarPlus, Download, Flag, HandHeart } from 'lucide-react';
import { useApp } from '@/hooks';
import { Empty, Field, Modal, Photo, Progress, SkeletonDetail, Tag } from '@/components/common';
import { ExtendCampaignModal } from '@/components/campaign/ExtendCampaignModal';
import { CAMPAIGN_REPORT_REASONS, CAMPAIGN_STATUS, campaignStatus, coverOf, ENDED_REASON, fmtDate, fmtDateTime, money } from '@/utils';

/* ================= DETAIL ================= */
export function CampaignDetail() {
  const { id } = useParams();
  const { state, me, campaigns, toast, load, orgLocked } = useApp();
  const nav = useNavigate();
  const [report, setReport] = useState(null);
  const [extending, setExtending] = useState(false);
  const [page, setPage] = useState(1);
  const [stm, setStm] = useState({ items: [], total: 0 });
  const [loading, setLoading] = useState(true);
  const pageSize = 8;
  useEffect(() => { load.campaign(id).then(() => setLoading(false)); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { load.statement(id, page - 1, pageSize).then(setStm); }, [id, page, state.campaigns.find((x) => x.id === id)?.raised]); // eslint-disable-line react-hooks/exhaustive-deps
  const c = state.campaigns.find((x) => x.id === id);
  if (!c) return loading ? <SkeletonDetail label="Đang tải chiến dịch…" /> : <Empty title="Chiến dịch không tồn tại" />;
  const st = campaignStatus(c);
  const txs = stm.items;
  const reported = !!(me && c.reportedByMe);
  const canDonate = st === 'ACTIVE' && c.raised < c.target;
  /* Tổ chức chủ chiến dịch: chiến dịch kết thúc vì hết thời gian thì gia hạn ngay tại đây (thông báo "Chiến dịch hết hạn" dẫn tới trang này) */
  const canExtend = !!me && me.orgId === c.orgId && st === 'ENDED' && !c.endedReason && !orgLocked;

  const exportCsv = async () => {
    const all = await load.statement(c.id, 0, 1000);
    const rows = [['Mã giao dịch', 'Thời gian', 'Người đóng góp', 'Số tiền', 'Lời nhắn'], ...all.items.map((d) => [d.txCode, fmtDateTime(d.createdAt), d.anonymous ? 'Người gửi ẩn danh' : d.donorName, d.amount, d.message])];
    const blob = new Blob([`\ufeff${rows.map((r) => r.map((x) => `"${String(x ?? '').replace(/"/g, '""')}"`).join(',')).join('\n')}`], { type: 'text/csv;charset=utf-8' });
    const a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = `sao-ke-${c.id}.csv`;
    a.click();
  };

  return (
    <div className="stack">
      <button className="link row gap6" onClick={() => nav('/campaigns')}><ArrowLeft size={16} /> Quay lại danh sách chiến dịch</button>
      <div className="card camp-hero">
        <Photo img={coverOf(c)} className="camp-hero-img" />
        <div className="stack-sm grow">
          <div className="row gap6 wrap"><Tag tone={CAMPAIGN_STATUS[st].tone}>{CAMPAIGN_STATUS[st].label}</Tag>{st === 'ENDED' && <Tag tone="gray">{ENDED_REASON[c.endedReason || 'TIME_UP']}</Tag>}</div>
          <h1 className="camp-h1">{c.name}</h1>
          <div className="raised"><b>{money(c.raised)}</b><span>/ mục tiêu {money(c.target)}</span></div>
          <Progress value={c.raised} max={c.target} />
          <div className="row gap16 wrap small muted"><span>{stm.total} lượt ủng hộ</span><span>{fmtDate(c.start)} – {fmtDate(c.end)}</span></div>
          <div className="row gap8 wrap">
            {canDonate ? <Link to={`/campaigns/${c.id}/donate`} className="btn btn-primary btn-lg"><HandHeart size={18} /> Ủng hộ chiến dịch này</Link> : <button className="btn btn-primary btn-lg" disabled>{st === 'UPCOMING' ? 'Chưa mở nhận ủng hộ' : 'Đã ngừng nhận ủng hộ'}</button>}
            {canExtend && <button className="btn btn-soft" onClick={() => setExtending(true)}><CalendarPlus size={16} /> Gia hạn chiến dịch</button>}
            {me && me.role !== 'ADMIN' && me.orgId !== c.orgId && <button className="btn btn-ghost" disabled={st === 'ENDED' || reported} onClick={() => setReport({ reason: 'PROFITEERING', desc: '' })}><Flag size={16} /> {reported ? 'Đã báo cáo' : 'Báo cáo chiến dịch'}</button>}
          </div>
        </div>
      </div>

      <div className="card info-strip">
        <div className="info-block"><span className="muted small">Mục đích chiến dịch</span><p>{c.purpose}</p></div>
        <div className="info-block"><span className="muted small">Khu vực & đối tượng hỗ trợ</span><p>{c.area}</p><p className="muted">{c.beneficiaries}</p></div>
        <div className="info-block"><span className="muted small">Tổ chức thực hiện</span><p><b>{c.orgName}</b></p><p className="muted">{c.orgInfo}</p>
          <div className="acc-box"><span>Ngân hàng: <b>{c.bank || 'Chưa cập nhật'}</b></span><span>Số tài khoản: <b>{c.accountNo}</b></span><span>Chủ tài khoản: <b>{c.accountName}</b></span></div>
        </div>
      </div>
      {c.gallery?.length > 0 && <div className="gallery">{c.gallery.map((g, i) => <Photo key={g.id || i} img={g} />)}</div>}

      <div className="card">
        <div className="pad row between"><h3>Bảng sao kê</h3><button className="btn btn-ghost btn-sm" onClick={exportCsv} disabled={!txs.length}><Download size={15} /> Xuất sao kê (CSV)</button></div>
        <div className="table-wrap">
          <table className="table">
            <thead><tr><th>Mã giao dịch</th><th>Thời gian</th><th>Người đóng góp</th><th className="right">Số tiền</th><th>Lời nhắn</th></tr></thead>
            <tbody>
              {txs.length === 0 && <tr><td colSpan={5} className="muted center">Chưa có giao dịch nào.</td></tr>}
              {txs.map((d) => (
                <tr key={d.id}><td><b className="mono-ish">#{d.txCode}</b></td><td className="nowrap small">{fmtDateTime(d.createdAt)}</td><td>{d.anonymous ? 'Người gửi ẩn danh' : d.donorName}</td><td className="right money">+{money(d.amount)}</td><td className="muted small">{d.message}</td></tr>
              ))}
            </tbody>
          </table>
        </div>
        {stm.total > pageSize && <div className="pad row gap6 center">{Array.from({ length: Math.ceil(stm.total / pageSize) }, (_, i) => <button key={i} className={`page-btn ${page === i + 1 ? 'on' : ''}`} onClick={() => setPage(i + 1)}>{i + 1}</button>)}</div>}
      </div>

      {report && (
        <Modal title="Báo cáo chiến dịch" onClose={() => setReport(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setReport(null)}>Hủy</button>
          <button className="btn btn-danger" onClick={async () => { const r = await campaigns.report(c.id, report.reason, report.desc); if (r) toast(r, 'err'); else { toast('Đã gửi báo cáo'); setReport(null); } }}>Gửi</button>
        </>}>
          <div className="stack">
            <Field label="Lý do"><select className="input" value={report.reason} onChange={(e) => setReport({ ...report, reason: e.target.value })}>{Object.entries(CAMPAIGN_REPORT_REASONS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}</select></Field>
            <Field label="Mô tả thêm" required={report.reason === 'OTHER'}><textarea className="input" rows={3} value={report.desc} onChange={(e) => setReport({ ...report, desc: e.target.value })} /></Field>
          </div>
        </Modal>
      )}
      {extending && <ExtendCampaignModal c={c} onClose={() => setExtending(false)} />}
    </div>
  );
}
