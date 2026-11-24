import { useEffect, useRef, useState } from 'react';
import { CheckCircle2, Clock, CloudRain, MapPin, Navigation, Siren, X, XCircle, Zap } from 'lucide-react';
import { useApp, useCaseParam, useNow } from '@/hooks';
import MapView from '@/components/map/MapView';
import { Confirm, Empty, Field, Modal, Sev, Tabs, Tag } from '@/components/common';
import { AiNote } from '@/components/sos/AiNote';
import { ContactButtons } from '@/components/sos/ContactButtons';
import { DoneNote } from '@/components/sos/DoneNote';
import { VerifyNote } from '@/components/sos/VerifyNote';
import { OrgStatusModal } from '@/components/org/OrgStatusModal';
import { ago, ASSIGN_RELEASE_MIN, ASSIGN_TIMEOUT_MIN, fmtCountdown, fmtDist, haversine, MATCH_WEIGHTS, openGoogleMapsDirections, ORG_STATUS, SEVERITY, SOS_STATUS, waitLeft } from '@/utils';

/* Màn hình rộng hơn 860px: bảng chi tiết ca nổi trên phần bên phải bản đồ (khớp @media trong styles.css), rộng 380px cách mép 12px,
   nên dịch khung nhìn sang phải nửa phần bị che để ca đang chọn nằm giữa phần bản đồ còn thấy được */
const PANEL_OVERLAY_MQ = '(min-width: 861px)';
const PANEL_OFFSET = [(380 + 12) / 2, 0];
const HISTORY_PAGE = 10; // số ca lịch sử mỗi lần "Tải thêm"

/* Ca Admin giao: trong 5 phút đầu đếm ngược hạn xác nhận; quá hạn thì tổ chức vẫn xác nhận muộn được cho tới khi ca tự chuyển
   cho tổ chức khác (thêm ASSIGN_RELEASE_MIN phút, nếu Admin chưa điều phối lại trước đó) */
function assignCountdown(s) {
  const left = waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN);
  if (left) return `Xác nhận trong ${fmtCountdown(left)}`;
  const release = waitLeft(s.assignedAt, ASSIGN_TIMEOUT_MIN + ASSIGN_RELEASE_MIN);
  return release ? `Đã quá hạn, ca sẽ chuyển cho tổ chức khác sau ${fmtCountdown(release)}` : 'Đã quá hạn, ca đang được chuyển cho tổ chức khác';
}

export default function RescueDashboard() {
  const { state, myOrg, orgLocked, me, sos, toast } = useApp();
  useNow(1000);
  const [tab, setTab] = useState('suggested');
  const [sev, setSev] = useState('');
  const [sort, setSort] = useState('match');
  const [selected, setSelected] = useState(null);
  const [panelOpen, setPanelOpen] = useState(true);
  const [scrollKey, setScrollKey] = useState(0);
  const panelRef = useRef(null);
  const [rejecting, setRejecting] = useState(null);
  const [reason, setReason] = useState('');
  const [nextStatus, setNextStatus] = useState(null);
  const [statusTarget, setStatusTarget] = useState(null); // trạng thái tổ chức sắp chuyển sang (đang mở popup xác nhận)
  const [caseNotice, setCaseNotice] = useState(null); // mở ca từ thông báo nhưng ca không còn trong danh sách của tổ chức
  const [noticeKey, setNoticeKey] = useState(0);
  const noticeRef = useRef(null);

  /* Ca đề xuất do backend trả về (đã loại ca đã từ chối, ca chờ xác minh, ca ngoài bán kính hoạt động tổ chức tự khai) kèm điểm AI Matching */
  const suggested = state.sos.filter((s) => s.suggested && s.status === 'PENDING' && (!sev || s.severity === sev))
    .map((s) => ({ s, m: s.matching || { score: 0, L: 0, S: 0, W: 0, dist: haversine(myOrg, s), reason: '' } }))
    .sort((a, b) => {
      if (sort === 'severity') return SEVERITY[b.s.severity].w - SEVERITY[a.s.severity].w || b.m.score - a.m.score;
      if (sort === 'near') return a.m.dist - b.m.dist;
      if (sort === 'wait') return a.s.createdAt - b.s.createdAt;
      return b.m.score - a.m.score; // mặc định: điểm Matching giảm dần
    });
  const matchOf = Object.fromEntries(suggested.map((x) => [x.s.id, x.m]));
  const assigned = state.sos.filter((s) => s.status === 'ASSIGNED' && s.orgId === myOrg.id);
  const handling = state.sos.filter((s) => s.status === 'ACCEPTED' && s.orgId === myOrg.id);
  /* Lịch sử (Hoàn thành, Đã hủy) không đồng bộ định kỳ: tải theo trang, 10 ca mỗi lần "Tải thêm"; tải lại trang đầu khi mở tab.
     Gộp thêm ca đã kết thúc vừa mở từ thông báo (được ghim trong state) */
  const [hist, setHist] = useState(null); // { items, total, last }
  const [histLoading, setHistLoading] = useState(false);
  const loadHistory = async (reset) => {
    setHistLoading(true);
    const page = reset || !hist ? 0 : Math.ceil(hist.items.length / HISTORY_PAGE);
    const r = await sos.casesPage('org', { page, size: HISTORY_PAGE });
    setHistLoading(false);
    if (!r) return;
    setHist((h) => {
      const prev = reset || !h ? [] : h.items;
      const seen = new Set(prev.map((x) => x.id));
      return { items: [...prev, ...r.items.filter((x) => !seen.has(x.id))], total: r.total, last: r.last };
    });
  };
  useEffect(() => { if (tab === 'history' || tab === 'all' || !hist) loadHistory(true); }, [tab]); // eslint-disable-line react-hooks/exhaustive-deps
  const pinnedEnded = state.sos.filter((s) => ['DONE', 'CANCELLED'].includes(s.status) && s.orgId === myOrg.id);
  const history = [...pinnedEnded, ...(hist?.items || []).filter((x) => !pinnedEnded.some((p) => p.id === x.id))];

  const canTakeNew = myOrg.opStatus === 'READY' && !orgLocked; // bị khóa: không tiếp nhận ca mới
  const lists = {
    suggested: canTakeNew ? suggested.map((x) => x.s) : [],
    assigned,
    handling,
    history,
    all: [...(canTakeNew ? suggested.map((x) => x.s) : []), ...assigned, ...handling, ...history],
  };
  const modeOf = (x) => ({ PENDING: 'suggested', ASSIGNED: 'assigned', ACCEPTED: 'handling' }[x.status] || 'history');
  const current = lists[tab];
  const sel = state.sos.find((s) => s.id === selected) || history.find((s) => s.id === selected) || current[0];

  /* Chọn ca (từ danh sách hoặc bấm điểm trên bản đồ): mở bảng chi tiết và cuộn tới nếu bảng đang nằm ngoài màn hình
     (màn hình hẹp: bảng nằm dưới bản đồ; danh sách xuống dưới bản đồ khi màn hình ≤ 1100px) */
  const select = (id) => { setSelected(id); setPanelOpen(true); setScrollKey((k) => k + 1); };
  useEffect(() => { if (scrollKey) panelRef.current?.scrollIntoView?.({ block: 'nearest', behavior: 'smooth' }); }, [scrollKey]);
  /* Mở từ thông báo (?case=): chuyển tới đúng tab của ca (vd. "Được phân công" khi Admin vừa giao, cần xác nhận trong 5 phút) và chọn ca */
  useCaseParam((id) => {
    const t = ['assigned', 'handling', 'history', 'suggested'].find((k) => lists[k].some((x) => x.id === id));
    if (!t) return false;
    setTab(t); setCaseNotice(null); select(id);
    return true;
  }, (id) => { setCaseNotice(missingCaseText(state.sos.find((s) => s.id === id), myOrg, orgLocked)); setNoticeKey((k) => k + 1); });
  /* Khung giải thích nằm trên bản đồ: trang đang cuộn xuống thì phải cuộn lên mới thấy */
  useEffect(() => { if (noticeKey) noticeRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }); }, [noticeKey]);
  const overlay = !!window.matchMedia?.(PANEL_OVERLAY_MQ).matches;

  /* Bấm trạng thái khác: mở popup xác nhận kèm hệ quả và số ca thật; bấm lại trạng thái đang chọn thì không làm gì.
     Người dùng đã xác nhận trong popup nên gửi confirm = true (backend yêu cầu khi chuyển Không hoạt động lúc đang có ca) */
  const changeStatus = (st) => { if (st !== myOrg.opStatus) setStatusTarget(st); };
  const confirmStatus = async () => {
    const st = statusTarget;
    const r = await sos.setOrgStatus(st, true);
    if (r) return r === 'CONFIRM' ? 'Không chuyển được trạng thái, vui lòng thử lại.' : r;
    setStatusTarget(null);
    toast(`Trạng thái tổ chức: ${ORG_STATUS[st].label}`);
    return null;
  };

  const markers = current.map((s) => ({ id: s.id, lat: s.lat, lng: s.lng, kind: 'sos', color: SEVERITY[s.severity].color, pulse: s.id === sel?.id, label: s.id === sel?.id ? s.address.split(',')[0] : null, onClick: () => select(s.id) }));
  markers.push({ id: 'base', lat: myOrg.lat, lng: myOrg.lng, kind: 'org', color: '#1d4ed8', label: 'Trụ sở' });

  return (
    <div className="stack">
      <div className="page-head">
        <div>
          <h1 className="page-title">Điều phối cứu hộ</h1>
          <span className="muted">{myOrg.name} • gợi ý của AI Matching xếp theo điểm phù hợp (vị trí, mô tả, tình trạng tiếp nhận). Bạn quyết định tiếp nhận</span>
        </div>
        <div className="org-status-box">
          <div className="org-status-switch" role="radiogroup" aria-label="Trạng thái tổ chức">
            {Object.entries(ORG_STATUS).map(([k, v]) => <button key={k} role="radio" aria-checked={myOrg.opStatus === k} title={v.desc} className={`st-${k} ${myOrg.opStatus === k ? 'on' : ''}`} onClick={() => changeStatus(k)}>{v.label}</button>)}
          </div>
          {/* Không còn dòng mô tả cố định: ý nghĩa hiện khi rê chuột lên từng nút, hệ quả ghi rõ trong popup xác nhận khi đổi */}
        </div>
      </div>

      <div className="row between wrap gap8">
        <Tabs value={tab} onChange={(v) => { setTab(v); setSelected(null); setPanelOpen(true); }} tabs={[
          { value: 'all', label: 'Tất cả', count: lists.all.length },
          { value: 'suggested', label: 'Ca đề xuất (chưa xử lý)', count: lists.suggested.length },
          { value: 'assigned', label: 'Được phân công', count: assigned.length },
          { value: 'handling', label: 'Đang phụ trách', count: handling.length },
          { value: 'history', label: 'Đã xong / đã hủy', count: Math.max(hist?.total ?? 0, history.length) },
        ]} />
        {tab === 'suggested' && <div className="row gap8">
          <select className="input sm" value={sort} onChange={(e) => setSort(e.target.value)} aria-label="Sắp xếp"><option value="match">Sắp xếp: Điểm Matching (cao → thấp)</option><option value="severity">Mức độ nguy hiểm</option><option value="near">Khoảng cách</option><option value="wait">Thời gian chờ</option></select>
          <select className="input sm" value={sev} onChange={(e) => setSev(e.target.value)}><option value="">Mức độ: Tất cả</option>{Object.entries(SEVERITY).filter(([k]) => k !== 'KHONG').map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
        </div>}
      </div>

      {caseNotice && <div ref={noticeRef} key={noticeKey} className="alert alert-info row between gap8 flash"><span>{caseNotice}</span><button className="icon-btn sm" onClick={() => setCaseNotice(null)} aria-label="Đóng" title="Đóng"><X size={16} /></button></div>}
      {orgLocked && <div className="alert alert-danger">Tài khoản tổ chức đang bị khóa đến {new Date(me.lock.until).toLocaleDateString('vi-VN')}. Tổ chức không thể tiếp nhận ca SOS mới, chỉ tiếp tục hoàn tất các ca đang phụ trách.</div>}
      {tab === 'suggested' && !orgLocked && myOrg.opStatus !== 'READY' && <div className="alert alert-info">Tổ chức đang ở trạng thái “{ORG_STATUS[myOrg.opStatus].label}” nên không nhận ca đề xuất mới. Chuyển sang Sẵn sàng để xem danh sách.</div>}

      <div className="rescue-layout">
        <div className="rescue-map-column">
          <div className="card map-card">
            <MapView layerSwitch center={[myOrg.lat, myOrg.lng]} markers={markers} focus={sel ? [sel.lat, sel.lng] : null} focusOffset={sel && panelOpen && overlay ? PANEL_OFFSET : null} height={600} />
          </div>
          {sel && panelOpen && <SelectedPanel key={sel.id} panelRef={panelRef} s={sel} tab={modeOf(sel)} match={matchOf[sel.id]} onClose={() => setPanelOpen(false)} onReject={() => { setRejecting(sel); setReason(''); }} onNext={setNextStatus} />}
        </div>
        <aside className="card map-list">
          <div className="map-list-head"><b>Các ca cứu hộ</b><Tag tone="blue">{current.length} ca</Tag></div>
          {current.length === 0 && <Empty icon={<Siren size={30} />} title={tab === 'suggested' ? 'Chưa có ca SOS được đề xuất' : 'Không có ca nào'} />}
          {current.map((s) => {
            const dist = haversine(myOrg, s);
            return (
              <button key={s.id} className={`map-item sev-${s.severity} ${sel?.id === s.id ? 'sel' : ''}`} onClick={() => select(s.id)}>
                <div className="row between"><div className="row gap6"><Sev level={s.severity} /><Tag tone={SOS_STATUS[s.status].tone}>{SOS_STATUS[s.status].label}</Tag></div><span className="small muted">Cách {fmtDist(dist)}</span></div>
                <b>{s.desc}</b>
                <span className="muted small">{s.code} • {ago(s.createdAt)}{s.needsVerify ? ' • Nên xác minh' : ''}</span>
                {tab === 'suggested' && matchOf[s.id] && <span className="small match-line">Độ phù hợp <b>{matchOf[s.id].score}/100</b></span>}
                {s.status === 'ASSIGNED' && <span className="small countdown"><Clock size={13} /> {assignCountdown(s)}</span>}
                {s.status === 'ACCEPTED' && s.needsAttention && <span className="small countdown"><Clock size={13} /> Tiếp nhận {ago(s.acceptedAt)}, chưa hoàn thành</span>}
              </button>
            );
          })}
          {(tab === 'history' || tab === 'all') && hist && !hist.last && (
            <button className="btn btn-ghost" disabled={histLoading} onClick={() => loadHistory(false)}>{histLoading ? 'Đang tải…' : 'Tải thêm ca đã xong / đã hủy'}</button>
          )}
        </aside>
      </div>

      {rejecting && (
        <Modal title={`Từ chối ca ${rejecting.code}`} onClose={() => setRejecting(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setRejecting(null)}>Hủy</button>
          <button className="btn btn-danger" onClick={async () => { const r = await sos.rejectAssigned(rejecting.id, reason); if (r) toast(r, 'err'); else { toast('Đã từ chối, Admin sẽ điều phối lại'); setRejecting(null); } }}>Từ chối</button>
        </>}>
          <Field label="Lý do từ chối" required><textarea className="input" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="VD: Đội đang hết ca-nô khả dụng" /></Field>
        </Modal>
      )}
      {nextStatus && <Confirm title="Cập nhật trạng thái cứu hộ" text={`Chuyển ca ${nextStatus.s.code} sang “${SOS_STATUS[nextStatus.next].label}”?`} onClose={() => setNextStatus(null)} onOk={async () => { const r = await sos.updateStatus(nextStatus.s.id, nextStatus.next); toast(r || 'Đã cập nhật trạng thái', r ? 'err' : 'ok'); }} />}
      {statusTarget && <OrgStatusModal target={statusTarget} assigned={assigned.length} handling={handling.length}
        onClose={() => setStatusTarget(null)} onConfirm={confirmStatus} />}
    </div>
  );
}

/* Vì sao ca mở từ thông báo không có trong danh sách của tổ chức */
function missingCaseText(s, myOrg, orgLocked) {
  if (!s) return 'Ca SOS này không còn trong danh sách của tổ chức: ca đã bị hủy, đã kết thúc hoặc đã được chuyển cho tổ chức khác.';
  if (s.orgId && s.orgId !== myOrg.id) return `Ca ${s.code} đã được giao cho tổ chức khác${s.org?.name ? ` (${s.org.name})` : ''}.`;
  if (s.status === 'PENDING') {
    if (orgLocked) return `Ca ${s.code} đang chờ tổ chức tiếp nhận, nhưng tài khoản tổ chức đang bị khóa nên không nhận được ca mới.`;
    if (myOrg.opStatus !== 'READY') return `Ca ${s.code} đang chờ tổ chức tiếp nhận. Tổ chức đang ở trạng thái “${ORG_STATUS[myOrg.opStatus].label}”, chuyển sang Sẵn sàng để xem và tiếp nhận ca.`;
    return `Ca ${s.code} không nằm trong danh sách đề xuất của tổ chức (tổ chức đã từ chối hoặc không phản hồi ca này, hoặc ca ở ngoài bán kính hoạt động của tổ chức).`;
  }
  return `Ca ${s.code} hiện ở trạng thái “${SOS_STATUS[s.status].label}”, không còn trong danh sách của tổ chức.`;
}

function SelectedPanel({ panelRef, s, tab, match, onClose, onReject, onNext }) {
  const { state, sos, toast, myOrg, orgLocked } = useApp();
  const citizen = state.users.find((u) => u.id === s.citizenId);
  const dist = haversine(myOrg, s);
  const phone = tab !== 'history' && (citizen?.phone || s.citizenPhone);
  const hasMain = tab === 'handling' || (!orgLocked && (tab === 'suggested' || tab === 'assigned'));
  /* Ba phần: đầu (mã ca, nút đóng) và hàng nút luôn hiện; chỉ phần nội dung ở giữa cuộn khi bảng nổi trên bản đồ */
  return (
    <div className="sos-panel" ref={panelRef}>
      <div className="sos-panel-head">
        <div className="row gap6 wrap"><Sev level={s.severity} /><Tag tone={SOS_STATUS[s.status].tone}>{SOS_STATUS[s.status].label}</Tag><span className="muted small">{s.code} • Báo {ago(s.createdAt)}</span></div>
        <button className="icon-btn sm" onClick={onClose} aria-label="Đóng chi tiết ca" title="Đóng"><X size={18} /></button>
      </div>
      <div className="sos-panel-body">
        <h3>{s.desc || 'Người dân chỉ bấm SOS, chưa có mô tả'}</h3>
        <span className="muted small"><MapPin size={13} /> {s.address} • cách {fmtDist(dist)}</span>
        <VerifyNote s={s} />
        <AiNote s={s} />
        {s.weather && <span className="small"><CloudRain size={13} /> Thời tiết tại vị trí: {s.weather.rainMm} mm • {s.weather.forecast}{s.weather.alert ? ` • ${s.weather.alert}` : ''}</span>}
        {match && (
          <div className="match-box">
            <b>Độ phù hợp với tổ chức của bạn: {match.score}/100</b>
            <div className="match-bars">
              <span>Vị trí ({(match.weights || MATCH_WEIGHTS).L}%)<i style={{ width: `${match.L}%` }} /><em>{match.L}</em></span>
              <span>Mô tả tương đồng ({(match.weights || MATCH_WEIGHTS).S}%)<i style={{ width: `${match.S}%` }} /><em>{match.S}</em></span>
              <span>Năng lực đáp ứng ({(match.weights || MATCH_WEIGHTS).C}%)<i style={{ width: `${match.C ?? 0}%` }} /><em>{match.C ?? '—'}</em></span>
              <span>Tải hiện tại ({(match.weights || MATCH_WEIGHTS).W}%)<i style={{ width: `${match.W}%` }} /><em>{match.W}</em></span>
            </div>
            <span className="muted small">{match.reason}. Điểm chỉ là gợi ý, tổ chức tự quyết định tiếp nhận.</span>
          </div>
        )}
        {/* Backend đã nhắc vì ca Đã tiếp nhận quá lâu (ngưỡng giờ cấu hình ở backend) */}
        {/* Ca đã xong: tổ chức cần biết ca được đóng vì người dân báo an toàn / Admin xác nhận, không phải do mình bấm */}
        <DoneNote s={s} />
        {s.status === 'ACCEPTED' && s.needsAttention && <div className="alert alert-danger"><Clock size={15} /> Ca đã được tiếp nhận {ago(s.acceptedAt)} nhưng chưa hoàn thành. Nếu đã cứu hộ xong, hãy bấm “Hoàn thành cứu hộ”; nếu gặp khó khăn, hãy liên hệ Admin.</div>}
        <div className="kv">
          <span>Người gửi</span><b>{citizen?.name}</b>
        </div>
      </div>
      {(hasMain || phone) && (
        <div className="sos-panel-actions">
          {hasMain && (
            <div className="row gap8 wrap">
              {tab === 'suggested' && <button className="btn btn-primary" onClick={async () => { const r = await sos.accept(s.id); toast(r || `Đã tiếp nhận ca ${s.code}`, r ? 'err' : 'ok'); }}><Zap size={17} /> Tiếp nhận cứu hộ ngay</button>}
              {tab === 'assigned' && <>
                <button className="btn btn-primary" onClick={async () => { const r = await sos.confirmAssigned(s.id); toast(r || 'Đã xác nhận tiếp nhận', r ? 'err' : 'ok'); }}><CheckCircle2 size={17} /> Xác nhận tiếp nhận</button>
                <button className="btn btn-ghost" onClick={onReject}><XCircle size={17} /> Từ chối</button>
              </>}
              {tab === 'handling' && <>
                {s.status === 'ACCEPTED' && <button className="btn btn-success" onClick={() => onNext({ s, next: 'DONE' })}><CheckCircle2 size={16} /> Hoàn thành cứu hộ</button>}
                <button className="btn btn-soft" onClick={() => openGoogleMapsDirections(myOrg, s)}><Navigation size={16} /> Xem chỉ đường</button>
              </>}
            </div>
          )}
          {phone && <div className="row gap8 wrap"><ContactButtons phone={phone} label="Gọi người dân" /></div>}
        </div>
      )}
    </div>
  );
}
