import { useEffect, useMemo, useRef, useState } from 'react';
import { ArrowLeft, Clock, ShieldQuestion } from 'lucide-react';
import { ContactButtons } from '@/components/sos/ContactButtons';
import { AiNote } from '@/components/sos/AiNote';
import { useApp, useCaseParam, useNow } from '@/hooks';
import { Confirm, Modal, Sev, SkeletonLines, Tag } from '@/components/common';
import { ACTIVE_SOS, ago, ASSIGN_RELEASE_MIN, ASSIGN_TIMEOUT_MIN, COMPLETED_BY, DUP_RADIUS_M, fmtCountdown, fmtDateTime, fmtDist, PENDING_TIMEOUT_MIN, SEVERITY, SOS_STATUS, waitLeft } from '@/utils';
import { AdjustSeverityModal } from '@/components/admin/AdjustSeverityModal';
import { VerifyNote } from '@/components/sos/VerifyNote';
import { AssignModal } from '@/components/admin/AssignModal';
import { MarkInvalidModal } from '@/components/admin/MarkInvalidModal';
import { AdminCompleteModal } from '@/components/admin/AdminCompleteModal';
import { UnassignModal } from '@/components/admin/UnassignModal';
import { Pager } from '@/components/admin/Pager';

/* Kết quả lần phân công gần nhất khiến ca cần điều phối lại: nhãn ngắn trong bảng và câu giải thích trong chi tiết */
/* CANCELLED = phân công bị hủy không do lỗi của tổ chức: Admin hủy phân công, tổ chức bị khóa / chuyển Không hoạt động, người dân hủy ca */
const REASSIGN_LABEL = { REJECTED: 'Tổ chức đã từ chối', TIMEOUT: 'Tổ chức không phản hồi', CANCELLED: 'Phân công đã bị hủy' };
/* Câu giải thích trong chi tiết: kèm lý do thật đã lưu ở lần phân công đó (backend chỉ gửi lý do cho Admin) */
const REASSIGN_NOTE = {
  REJECTED: (note) => (note ? `Tổ chức đã từ chối: ${note}` : 'Tổ chức đã từ chối ca này.'),
  TIMEOUT: (note) => `Tổ chức không phản hồi sau ${ASSIGN_TIMEOUT_MIN} phút.${note ? ` ${note}` : ''}`,
  CANCELLED: (note) => note || 'Phân công đã bị hủy.',
};
/* CANCELLED của ca đã bị người dân hủy thì không cần giải thích (trạng thái ca đã là Đã hủy) */
const lastAssignmentNote = (s) => (s.assignmentStatus === 'CANCELLED' && s.status !== 'PENDING' ? null : REASSIGN_NOTE[s.assignmentStatus]?.(s.assignmentNote) ?? null);
/* Ca lân cận gửi trước/sau ca đang xem bao lâu: hai người báo cách nhau ít phút thường là cùng một sự cố */
const gapText = (t, ref) => {
  const m = Math.round(Math.abs(t - ref) / 60000);
  const span = m < 1 ? 'chưa tới 1 phút' : m < 60 ? `${m} phút` : m < 1440 ? `${Math.floor(m / 60)} giờ` : `${Math.floor(m / 1440)} ngày`;
  return `Gửi ${t < ref ? 'trước' : 'sau'} ca này ${span}`;
};

/* Bộ lọc xem ca đã kết thúc: không đồng bộ định kỳ, máy chủ lọc và chia trang (20 ca mỗi trang) */
const ENDED_FILTERS = { ENDED: 'Tất cả ca đã kết thúc', DONE: SOS_STATUS.DONE.label, CANCELLED: SOS_STATUS.CANCELLED.label, INVALID: SOS_STATUS.INVALID.label };
const PAGE_SIZE = 20;
const apiStatus = (s) => (s === 'DONE' ? 'COMPLETED' : s);

/* ================= SOS COORDINATION ================= */
export function AdminSos() {
  const { state, sos, toast } = useApp();
  useNow(1000);
  /* Mặc định "Đang hoạt động": mọi ca chưa xong (luôn đủ, kể cả ca gửi đã lâu). Ca đã kết thúc xem theo trang */
  const [status, setStatus] = useState('ACTIVE');
  const [sev, setSev] = useState('');
  const [sort, setSort] = useState('old');
  const ended = status in ENDED_FILTERS;
  const [page, setPage] = useState(0);
  const [endedPage, setEndedPage] = useState(null); // { items, total, last } của trang đang xem; null = đang tải
  const [extra, setExtra] = useState({});           // ca không có trong danh sách đang hoạt động nhưng mở xem được (trang lịch sử, ca lân cận)
  const [view, setView] = useState(null);
  const [assigning, setAssigning] = useState(null);
  const [adjusting, setAdjusting] = useState(null);
  const [invalidating, setInvalidating] = useState(null);
  const [completing, setCompleting] = useState(null); // ca Đã tiếp nhận Admin sắp xác nhận hoàn thành (người dân đã an toàn)
  const [unassigning, setUnassigning] = useState(null); // ca Đã phân công Admin sắp hủy phân công (tổ chức không đi được)
  const [cands, setCands] = useState([]);
  const [nearby, setNearby] = useState(); // undefined: đang tải, null: lỗi, mảng: ca lân cận
  const [trail, setTrail] = useState([]); // các ca đã xem trước khi mở ca lân cận, để quay lại
  const [confirmingVerify, setConfirmingVerify] = useState(null); // ca "nên xác minh" Admin sắp bấm "Đã xác minh, giữ mức"
  const [focusActions, setFocusActions] = useState(false);        // mở chi tiết bằng nút "Xác minh": cuộn tới khu vực kết luận
  const actionsRef = useRef(null);

  const needAction = (s) => !s.invalid && ((s.status === 'PENDING' && !s.adminReview && (s.needsReassign || waitLeft(s.createdAt, PENDING_TIMEOUT_MIN) === 0)) || (s.status === 'ASSIGNED' && waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN) === 0));
  /* Ca Đã tiếp nhận quá lâu chưa hoàn thành: backend đã nhắc tổ chức và Admin (ngưỡng giờ cấu hình ở backend), Admin cần liên hệ tổ chức */
  const longRescue = (s) => s.status === 'ACCEPTED' && s.needsAttention;
  /* Chỉ định được: ca Chờ xử lý đã xác minh, hoặc ca Đã phân công mà tổ chức quá 5 phút chưa phản hồi (backend chặn điều phối lại sớm hơn) */
  const canAssignOf = (s) => !s.invalid && ((s.status === 'PENDING' && !s.adminReview) || (s.status === 'ASSIGNED' && waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN) === 0));
  /* Ca Đã phân công đã quá hạn phản hồi: nếu không ai điều phối lại, backend tự trả ca về Chờ xử lý sau ASSIGN_RELEASE_MIN phút nữa
     (tác vụ chạy mỗi phút nên có thể trễ tới 1 phút so với đồng hồ này) */
  const releaseLeft = (s) => waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN + ASSIGN_RELEASE_MIN);
  const releaseText = (s) => (releaseLeft(s) ? `Quá hạn phản hồi, tự trả về sau ${fmtCountdown(releaseLeft(s))}` : 'Quá hạn phản hồi, đang trả về Chờ xử lý');
  const assignLabel = (s) => (s.status === 'ASSIGNED' || s.needsReassign ? 'Điều phối lại' : 'Chỉ định tổ chức');
  /* Ca đang hoạt động trong state (đồng bộ 30 giây/lần); ca đã kết thúc được ghim (mở từ thông báo) không vào bảng này */
  const activeSos = useMemo(() => state.sos.filter((s) => ACTIVE_SOS.includes(s.status)), [state.sos]);
  const list = useMemo(() => (ended ? endedPage?.items || [] : activeSos
    .filter((s) => (status === 'NEED' ? needAction(s) : status === 'REVIEW' ? s.adminReview && s.status === 'PENDING' : status === 'LONG' ? longRescue(s) : status === 'ACTIVE' || s.status === status) && (!sev || s.severity === sev))
    .sort((a, b) => {
      if (sort === 'severity') return SEVERITY[b.severity].w - SEVERITY[a.severity].w || a.createdAt - b.createdAt;
      return sort === 'new' ? b.createdAt - a.createdAt : a.createdAt - b.createdAt;
    })), [activeSos, ended, endedPage, status, sev, sort]);
  const needCount = activeSos.filter(needAction).length;
  const reviewCount = activeSos.filter((s) => s.adminReview && s.status === 'PENDING').length;
  const longCount = activeSos.filter(longRescue).length;
  const viewing = view && (state.sos.find((s) => s.id === view) || extra[view]);
  const remember = (cases) => setExtra((m) => ({ ...m, ...Object.fromEntries(cases.map((c) => [c.id, c])) }));

  /* Ca đã kết thúc: máy chủ lọc trạng thái / mức, sắp xếp và chia trang. Đổi bộ lọc thì về trang đầu */
  useEffect(() => { setPage(0); }, [status, sev, sort]);
  useEffect(() => {
    if (!ended) return undefined;
    let alive = true;
    setEndedPage(null);
    sos.casesPage('admin', { status: apiStatus(status), level: sev, sortBy: sort === 'severity' ? 'level' : 'time', dir: sort === 'new' ? 'desc' : 'asc', page, size: PAGE_SIZE })
      .then((r) => { if (!alive) return; setEndedPage(r || { items: [], total: 0, last: true }); if (r) remember(r.items); });
    return () => { alive = false; };
  }, [ended, status, sev, sort, page]); // eslint-disable-line react-hooks/exhaustive-deps
  /* Xem lịch sử thì mới nhất trước; quay lại ca đang hoạt động thì chờ lâu nhất trước */
  const changeStatus = (v) => { if ((v in ENDED_FILTERS) !== ended) setSort(v in ENDED_FILTERS ? 'new' : 'old'); setStatus(v); };
  const truncated = state.sosActive && state.sosActive.total > activeSos.length;
  useEffect(() => {
    setCands([]);
    setNearby(undefined);
    if (!viewing) return undefined;
    let alive = true; // chuyển sang ca khác trước khi kết quả về: bỏ kết quả của ca cũ
    if (['PENDING', 'ASSIGNED'].includes(viewing.status) && !viewing.adminReview) sos.candidates(viewing.id).then((r) => { if (alive) setCands(r); });
    sos.nearby(viewing.id).then((r) => { if (!alive) return; setNearby(r); if (r) remember(r); }); // ca lân cận nào cũng mở xem được
    return () => { alive = false; };
  }, [view]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!focusActions || !viewing) return undefined;
    const t = requestAnimationFrame(() => { actionsRef.current?.scrollIntoView({ behavior: 'smooth', block: 'center' }); setFocusActions(false); });
    return () => cancelAnimationFrame(t);
  }, [focusActions, view]); // eslint-disable-line react-hooks/exhaustive-deps
  const openCase = (id, focus = false) => { setTrail([]); setView(id); setFocusActions(focus); };
  const openNearby = (id) => { setTrail((t) => [...t, view]); setView(id); };
  const back = () => { setView(trail[trail.length - 1]); setTrail((t) => t.slice(0, -1)); };
  const closeView = () => { setView(null); setTrail([]); };
  /* Mở từ thông báo (?case=): mở hộp chi tiết ca; ca chờ xác minh / cần điều phối / cứu hộ kéo dài thì cuộn tới khu vực nút thao tác */
  useCaseParam((id) => {
    const s = state.sos.find((x) => x.id === id);
    if (!s) return false;
    openCase(id, (s.adminReview && s.status === 'PENDING') || needAction(s) || longRescue(s)); // cần Admin làm gì: cuộn thẳng tới các nút
    return true;
  }, () => toast('Không tìm thấy ca SOS này.', 'err'));

  return (
    <div className="stack">
      <div className="page-head"><h1 className="page-title">Điều phối ca SOS</h1><div className="row gap8">{reviewCount > 0 && <button className="btn btn-soft btn-sm" onClick={() => setStatus('REVIEW')}><ShieldQuestion size={15} /> {reviewCount} ca cần xác minh</button>}{longCount > 0 && <button className="btn btn-soft btn-sm" onClick={() => setStatus('LONG')}><Clock size={15} /> {longCount} ca cứu hộ kéo dài</button>}{needCount > 0 && <button className="btn btn-danger btn-sm" onClick={() => setStatus('NEED')}>{needCount} ca cần Admin điều phối</button>}</div></div>
      <div className="filter-bar">
        <select className="input sm" value={status} onChange={(e) => changeStatus(e.target.value)} aria-label="Trạng thái">
          <optgroup label="Đang hoạt động">
            <option value="ACTIVE">Đang hoạt động (tất cả)</option><option value="REVIEW">Cần xác minh (chờ Admin)</option><option value="NEED">Cần điều phối</option><option value="LONG">Cứu hộ kéo dài</option>
            {ACTIVE_SOS.map((k) => <option key={k} value={k}>{SOS_STATUS[k].label}</option>)}
          </optgroup>
          <optgroup label="Đã kết thúc (xem theo trang)">{Object.entries(ENDED_FILTERS).map(([k, l]) => <option key={k} value={k}>{l}</option>)}</optgroup>
        </select>
        <select className="input sm" value={sev} onChange={(e) => setSev(e.target.value)}><option value="">Mọi mức độ</option>{Object.entries(SEVERITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
        <select className="input sm" value={sort} onChange={(e) => setSort(e.target.value)}><option value="old">Sắp xếp: thời gian gửi lâu nhất</option><option value="new">Thời gian gửi: mới nhất</option><option value="severity">Mức độ nguy hiểm (cao → thấp)</option></select>
      </div>
      {!ended && truncated && (
        <div className="alert alert-info">
          Đang hiển thị {activeSos.length} / {state.sosActive.total} ca đang hoạt động (tối đa {state.sosActive.limit} ca mới nhất mỗi lần tải).
          Ca cũ hơn chưa hiện ở đây; hãy xử lý bớt các ca đã xong hoặc tăng giới hạn SOS_ACTIVE_MAX ở máy chủ.
        </div>
      )}
      <div className="card sos-table-wrap">
        <table className="table sos-table">
          <colgroup><col className="w-id" /><col className="w-loc" /><col className="w-sev" /><col className="w-st" /><col className="w-org" /><col className="w-act" /></colgroup>
          <thead><tr><th>Ca SOS</th><th>Vị trí</th><th>Mức độ</th><th>Trạng thái</th><th>Tổ chức phụ trách</th><th><span className="sr-only">Thao tác</span></th></tr></thead>
          <tbody>
            {ended && !endedPage && <tr><td colSpan={6}><SkeletonLines lines={3} label="Đang tải ca đã kết thúc…" /></td></tr>}
            {list.length === 0 && (!ended || endedPage) && <tr><td colSpan={6} className="center muted">Danh sách hiện đang trống.</td></tr>}
            {list.map((s) => {
              const org = s.org;
              const canAssign = canAssignOf(s);
              const reviewing = s.adminReview && s.status === 'PENDING';
              /* Bấm vào dòng (hoặc Enter khi dòng đang được chọn bằng bàn phím) để mở chi tiết, nơi có đủ thao tác của ca */
              return (
                <tr key={s.id} className={`row-click${needAction(s) || longRescue(s) ? ' row-alert' : ''}`} tabIndex={0}
                  aria-label={`Mở chi tiết ca ${s.code}`} onClick={() => openCase(s.id)}
                  onKeyDown={(e) => { if (e.key === 'Enter' && e.target === e.currentTarget) openCase(s.id); }}>
                  <td className="c-id"><b>{s.code}</b><span className="small muted c-time">{fmtDateTime(s.createdAt)}</span><span className="small muted c-time">{ago(s.createdAt)}</span>
                    {(s.adminReview || s.needsVerify) && <span className="c-flags">{s.adminReview && <Tag tone="orange">Cần xác minh</Tag>}{!s.adminReview && s.needsVerify && <Tag tone="gray">Nên xác minh</Tag>}</span>}</td>
                  <td className="small c-loc">{s.address}</td>
                  <td className="c-sev"><Sev level={s.severity} /></td>
                  <td className="c-st"><Tag tone={SOS_STATUS[s.status].tone}>{SOS_STATUS[s.status].label}</Tag>
                    {s.status === 'ASSIGNED' && <div className="small muted"><Clock size={12} /> {waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN) ? `Chờ phản hồi ${fmtCountdown(waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN))}` : releaseText(s)}</div>}
                    {s.status === 'ACCEPTED' && s.acceptedAt && <div className="small muted"><Clock size={12} /> Tiếp nhận {ago(s.acceptedAt)}{longRescue(s) ? ', chưa hoàn thành' : ''}</div>}
                    {s.status === 'DONE' && s.doneBy && s.doneBy !== 'ORG' && <div className="small muted">{COMPLETED_BY[s.doneBy]}</div>}
                    {s.status === 'PENDING' && <div className="small muted">{s.needsReassign ? REASSIGN_LABEL[s.assignmentStatus] : s.adminReview ? 'Chờ Admin xác minh' : waitLeft(s.createdAt, PENDING_TIMEOUT_MIN) ? `Chờ tổ chức nhận, còn ${fmtCountdown(waitLeft(s.createdAt, PENDING_TIMEOUT_MIN))}` : `Quá ${PENDING_TIMEOUT_MIN} phút chờ`}</div>}
                  </td>
                  <td className="c-org">{org ? org.name : <span className="muted">Chưa có</span>}</td>
                  <td className="c-act">
                    {/* Tối đa một nút cho việc cần làm ngay; thao tác khác (hủy phân công, hoàn thành, điều chỉnh mức, không hợp lệ)
                        nằm trong hộp chi tiết. Nút chặn sự kiện để không mở hộp chi tiết thay cho thao tác của nút */}
                    {reviewing
                      ? <button className="btn btn-primary btn-sm" onClick={(e) => { e.stopPropagation(); openCase(s.id, true); }}>Xác minh</button>
                      : canAssign && <button className="btn btn-primary btn-sm" onClick={(e) => { e.stopPropagation(); setAssigning(s); }}>{s.status === 'ASSIGNED' || s.needsReassign ? 'Điều phối lại' : 'Chỉ định'}</button>}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {ended && endedPage && <Pager page={page} size={PAGE_SIZE} total={endedPage.total} last={endedPage.last} unit="ca" onChange={setPage} />}

      {viewing && (() => {
        const sender = state.users.find((u) => u.id === viewing.citizenId);
        const org = viewing.org;
        const canReview = viewing.adminReview && viewing.status === 'PENDING';
        /* Ca đã gửi tổ chức nhưng AI chưa chắc chắn: Admin gọi người gửi rồi gỡ cảnh báo, kể cả khi ca đã phân công/tiếp nhận */
        const canConfirm = !viewing.adminReview && viewing.needsVerify && ACTIVE_SOS.includes(viewing.status);
        const suggestions = cands.slice(0, 3);
        const prev = trail.length > 0 && state.sos.find((s) => s.id === trail[trail.length - 1]);
        return (
          <Modal wide title={`Chi tiết ca ${viewing.code}`} onClose={closeView}>
            {prev && <button className="btn btn-ghost btn-sm detail-back" onClick={back}><ArrowLeft size={15} /> Quay lại ca {prev.code}</button>}
            <div className="detail-grid">
              <div className="detail-col">
                <section className="detail-sec">
                  <div className="row gap6 wrap">
                    <Sev level={viewing.severity} />
                    <Tag tone={SOS_STATUS[viewing.status].tone}>{SOS_STATUS[viewing.status].label}</Tag>
                  </div>
                  <p className="detail-desc">{viewing.desc}</p>
                  <VerifyNote s={viewing} />
                  {(sender?.phone || viewing.citizenPhone) && <div className="row gap6 wrap"><ContactButtons phone={sender?.phone || viewing.citizenPhone} label="Gọi người gửi" /></div>}
                </section>
                <section className="detail-sec">
                  <h4>Thông tin ca</h4>
                  <div className="kv">
                    <span>Người gửi</span><b>{sender?.name} • {sender?.phone || viewing.citizenPhone}</b>
                    <span>Gửi lúc</span><b>{fmtDateTime(viewing.createdAt)} ({ago(viewing.createdAt)})</b>
                    <span>Vị trí</span><b>{viewing.address} ({viewing.lat.toFixed(4)}, {viewing.lng.toFixed(4)})</b>
                    {org && <><span>Tổ chức phụ trách</span><b>{org.name}</b></>}
                  </div>
                </section>
                <section className="detail-sec">
                  <h4>Đánh giá của AI</h4>
                  <div className="kv">
                    <span>Mức AI dự đoán</span><b>{viewing.aiSeverity ? SEVERITY[viewing.aiSeverity].label : 'Chưa đánh giá'}</b>
                    <span>Mức hiện tại</span><b>{SEVERITY[viewing.severity].label}</b>
                    {viewing.verifiedAt && <><span>Xác minh</span><b>{viewing.verifiedBy || 'Admin'} đã liên hệ xác minh lúc {fmtDateTime(viewing.verifiedAt)}</b></>}
                    {/* Không có dữ liệu (Open-Meteo lỗi/tắt): hệ thống không đoán, thời tiết không được dùng để nâng mức */}
                    <span>Thời tiết</span><b>{viewing.weather ? `${viewing.weather.rainMm} mm • ${viewing.weather.forecast}${viewing.weather.alert ? ` • ${viewing.weather.alert}` : ''}${viewing.weather.source ? ` (nguồn: ${viewing.weather.source})` : ''}` : 'Không có dữ liệu'}</b>
                  </div>
                  <AiNote s={viewing} />
                </section>
              </div>
              <div className="detail-col">
                <section className="detail-sec">
                  <h4>Ca lân cận (trong {DUP_RADIUS_M} m)</h4>
                  {nearby === undefined && <SkeletonLines lines={2} label="Đang tải ca lân cận…" />}
                  {nearby === null && <span className="muted small">Không tải được danh sách ca lân cận.</span>}
                  {nearby?.length === 0 && <span className="muted small">Không có ca nào khác đang xử lý hoặc vừa cứu xong quanh vị trí này.</span>}
                  {nearby?.map((n) => {
                    const body = (
                      <>
                        <div className="row between gap8"><b>{n.code}</b><span className="muted small">cách {fmtDist(n.distFromCase)}</span></div>
                        <div className="row gap6 wrap"><Sev level={n.severity} /><Tag tone={SOS_STATUS[n.status].tone}>{SOS_STATUS[n.status].label}</Tag>{n.org && <span className="small">{n.org.name}</span>}</div>
                        {n.desc && <span className="small nearby-desc">{n.desc}</span>}
                        <span className="muted small">{gapText(n.createdAt, viewing.createdAt)}</span>
                      </>
                    );
                    /* Ca lân cận nào cũng mở được (đã ghi nhớ ở remember), kể cả ca đã hoàn thành không có trong danh sách đang hoạt động */
                    return <button key={n.id} type="button" className={`org-suggest nearby-case sev-${n.severity}`} onClick={() => openNearby(n.id)} title={`Mở chi tiết ca ${n.code}`}>{body}</button>;
                  })}
                  {nearby?.length > 0 && viewing.status === 'PENDING' && <span className="muted small">Ca gần nhau và gửi cách nhau ít phút có thể là cùng một sự cố. Nếu đúng vậy và ca kia đã có tổ chức xử lý, hãy đánh dấu ca này Không hợp lệ với lý do “Trùng ca …”.</span>}
                </section>
                <section className="detail-sec">
                  <h4>Gợi ý tổ chức phù hợp</h4>
                  {suggestions.length === 0 && <span className="muted small">Chưa có gợi ý (ca đang chờ xác minh hoặc không còn cần điều phối).</span>}
                  {suggestions.map(({ org: o, m }) => (
                    <div key={o.id} className="org-suggest">
                      <div className="row between gap8"><b>{o.name}</b><span className="match-pill">{m.score}/100</span></div>
                      <span className="muted small">Cách {fmtDist(m.dist)}{m.dist > 50000 ? ' (quá xa)' : ''} • đang nhận {m.active} ca</span>
                      <span className="muted small">Vị trí {m.L} • Mô tả {m.S} • Năng lực {m.C ?? '—'} • Tải {m.W}</span>
                    </div>
                  ))}
                  <span className="muted small">AI chỉ gợi ý, Admin quyết định.</span>
                </section>
                {lastAssignmentNote(viewing) && (
                  <section className="detail-sec"><h4>Lần phân công gần nhất</h4><span className="small muted">{lastAssignmentNote(viewing)}</span></section>
                )}
                {viewing.invalid && (
                  <section className="detail-sec">
                    <h4>Kết luận không hợp lệ</h4>
                    <div className="kv">
                      <span>Lý do</span><b>{viewing.invalidReason}</b>
                      {viewing.invalidAt && <><span>Thời điểm</span><b>{fmtDateTime(viewing.invalidAt)}</b></>}
                    </div>
                  </section>
                )}
                {viewing.status === 'DONE' && (
                  <section className="detail-sec">
                    <h4>Hoàn thành</h4>
                    <div className="kv">
                      <span>Người hoàn thành</span><b>{COMPLETED_BY[viewing.doneBy || 'ORG']}{viewing.doneByName ? ` (${viewing.doneByName})` : ''}</b>
                      {viewing.doneNote && <><span>{viewing.doneBy === 'ADMIN' ? 'Lý do' : 'Ghi chú'}</span><b>{viewing.doneNote}</b></>}
                      {viewing.doneAt && <><span>Thời điểm</span><b>{fmtDateTime(viewing.doneAt)}</b></>}
                    </div>
                  </section>
                )}
                {longRescue(viewing) && (
                  <div className="alert alert-danger stack-sm">
                    <span><Clock size={15} /> Ca được tiếp nhận lúc {fmtDateTime(viewing.acceptedAt)} ({ago(viewing.acceptedAt)}) nhưng chưa hoàn thành. Hệ thống đã nhắc tổ chức; hãy liên hệ tổ chức và người gửi để nắm tình hình. Nếu người dân đã an toàn, bấm “Hoàn thành”.</span>
                    {org?.phone && <ContactButtons phone={org.phone} label="Gọi tổ chức" />}
                  </div>
                )}
                <div ref={actionsRef} className="stack-sm">
                  {canReview && <div className="alert alert-info">Ca được AI xếp nhãn “Không”: liên hệ người gửi để xác minh, rồi chọn mức độ phù hợp để chuyển cho tổ chức, hoặc đánh dấu Không hợp lệ nếu không phải yêu cầu cứu hộ.</div>}
                  {canConfirm && <div className="alert alert-info">AI chưa chắc chắn về ca này (xem lý do ở phần Đánh giá của AI). Nên gọi người gửi để kiểm tra: nếu mức {SEVERITY[viewing.severity].label} là phù hợp, bấm “Đã xác minh, giữ mức”{viewing.status === 'PENDING' ? '; nếu không, điều chỉnh mức độ hoặc đánh dấu Không hợp lệ' : ''}.</div>}
                  {viewing.status === 'ASSIGNED' && !canAssignOf(viewing) && <div className="alert alert-info"><Clock size={15} /> Đang chờ {org?.name || 'tổ chức'} xác nhận, còn {fmtCountdown(waitLeft(viewing.assignedAt, ASSIGN_TIMEOUT_MIN))}. Hết thời gian mà tổ chức chưa phản hồi thì mới điều phối lại được; nếu đã biết tổ chức không đi được, bấm “Hủy phân công”.</div>}
                  {viewing.status === 'ASSIGNED' && canAssignOf(viewing) && <div className="alert alert-danger"><Clock size={15} /> {org?.name || 'Tổ chức'} chưa xác nhận sau {ASSIGN_TIMEOUT_MIN} phút. Hãy điều phối lại cho tổ chức khác; nếu không, {releaseLeft(viewing) ? `ca sẽ tự trả về Chờ xử lý sau ${fmtCountdown(releaseLeft(viewing))}` : 'ca đang được tự trả về Chờ xử lý'} và được báo cho các tổ chức gần đó.</div>}
                  <div className="row gap8 wrap">
                    {canAssignOf(viewing) && <button className="btn btn-primary" onClick={() => setAssigning(viewing)}>{assignLabel(viewing)}</button>}
                    {viewing.status === 'ASSIGNED' && <button className="btn btn-danger" onClick={() => setUnassigning(viewing)}>Hủy phân công</button>}
                    {canReview && <button className="btn btn-success" onClick={() => setAdjusting({ ...viewing, verify: true })}>Đã xác minh, chuyển cho tổ chức</button>}
                    {canConfirm && <button className="btn btn-success" onClick={() => setConfirmingVerify(viewing)}>Đã xác minh, giữ mức</button>}
                    {/* Ca chờ xác minh đã có nút "Đã xác minh" (cùng chọn mức); hai kết luận là hợp lệ / không hợp lệ */}
                    {/* Chỉ đổi mức được khi ca Chờ xử lý: ca đã giao tổ chức thì ẩn hẳn, tránh nút mờ không rõ lý do */}
                    {!canReview && viewing.status === 'PENDING' && <button className="btn btn-soft" onClick={() => setAdjusting(viewing)}>Điều chỉnh mức độ nguy hiểm</button>}
                    {viewing.status === 'PENDING' && <button className="btn btn-danger" onClick={() => setInvalidating(viewing)}>Đánh dấu không hợp lệ</button>}
                    {/* Người dân đã an toàn nhưng không tự báo được, hoặc tổ chức đã cứu xong mà quên bấm: Admin đóng ca sau khi liên hệ */}
                    {viewing.status === 'ACCEPTED' && <button className="btn btn-success" onClick={() => setCompleting(viewing)}>Hoàn thành</button>}
                  </div>
                </div>
              </div>
            </div>
          </Modal>
        );
      })()}
        {adjusting && <AdjustSeverityModal s={adjusting} onClose={() => setAdjusting(null)} onSave={async (nextSeverity, note) => {
          const r = await (adjusting.verify ? sos.verifyReview(adjusting.id, nextSeverity, note) : sos.adjustSeverity(adjusting.id, nextSeverity));
          if (r) return r;
          toast(adjusting.verify ? 'Đã xác minh và chuyển ca cho tổ chức cứu hộ' : 'Đã cập nhật mức độ nguy hiểm và thứ tự ưu tiên');
          setAdjusting(null);
          return null;
        }} />}
        {confirmingVerify && <Confirm title={`Xác minh ca ${confirmingVerify.code}`} okText="Đã xác minh, giữ mức"
          text={`Bạn đã liên hệ người gửi và thấy mức ${SEVERITY[confirmingVerify.severity].label} là phù hợp? Cảnh báo “Nên xác minh” sẽ được gỡ; mức độ và trạng thái ca giữ nguyên.`}
          onClose={() => setConfirmingVerify(null)}
          onOk={async () => { const r = await sos.confirmVerification(confirmingVerify.id); if (r) toast(r, 'err'); else toast('Đã xác minh, giữ nguyên mức độ của ca'); }} />}
        {invalidating && <MarkInvalidModal s={invalidating} onClose={() => setInvalidating(null)} onSave={async (reason) => {
          const r = await sos.markInvalid(invalidating.id, reason);
          if (r) return r;
          toast('Đã đánh dấu ca không hợp lệ và báo cho người gửi');
          setInvalidating(null);
          return null;
        }} />}
        {completing && <AdminCompleteModal s={completing} onClose={() => setCompleting(null)} onSave={async (reason) => {
          const r = await sos.adminComplete(completing.id, reason);
          if (r) return r;
          toast('Đã xác nhận hoàn thành ca, tổ chức và người dân đã được thông báo');
          setCompleting(null);
          return null;
        }} />}
        {unassigning && <UnassignModal s={unassigning} onClose={() => setUnassigning(null)} onSave={async (reason) => {
          const r = await sos.unassign(unassigning.id, reason);
          if (r) return r;
          toast('Đã hủy phân công, ca quay về Chờ xử lý');
          setUnassigning(null);
          return null;
        }} />}
        {assigning && <AssignModal s={assigning} onClose={() => setAssigning(null)} onAssign={async (orgId) => { const r = await sos.assign(assigning.id, orgId); if (r) toast(r, 'err'); else { toast('Đã chỉ định tổ chức'); setAssigning(null); } }} />}
    </div>
  );
}
