import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { Crosshair, Flame, MapPin, Navigation, RefreshCw } from 'lucide-react';
import { useApp, useNow } from '@/hooks';
import MapView, { VN_BOUNDS } from '@/components/map/MapView';
import { Modal, Sev, SkeletonLines, Tag } from '@/components/common';
import { ACTIVE_SOS, adminOrgLink, ago, dispatchLink, fmtDateTime, fmtDist, haversine, openGoogleMapsDirections, ORG_STATUS, SEVERITY, SOS_STATUS } from '@/utils';

const REGIONS = {
  all: { label: 'Toàn quốc', bounds: VN_BOUNDS, test: () => true },
  north: { label: 'Miền Bắc', bounds: [[19.5, 102.2], [23.4, 108.0]], test: (s) => s.lat >= 19.5 },
  central: { label: 'Miền Trung', bounds: [[11.5, 104.8], [19.5, 109.5]], test: (s) => s.lat >= 11.5 && s.lat < 19.5 },
  south: { label: 'Miền Nam', bounds: [[8.4, 104.4], [11.8, 107.6]], test: (s) => s.lat < 11.5 },
  near: { label: 'Gần tôi (10 km)' },
};
const ENDED_SOS = ['DONE', 'CANCELLED', 'INVALID'];
/* Bản đồ Admin: ca đã kết thúc chỉ để xem lại, lấy trong khoảng thời gian này (tính theo lúc kết thúc) */
const ENDED_DAYS = [[1, '24 giờ qua'], [7, '7 ngày qua'], [30, '30 ngày qua']];
const DEFAULT_DAYS = 7;

export default function MapPage() {
  const { state, me, myOrg, canRescue, load } = useApp();
  useNow(5000);
  const role = me?.role === 'ADMIN' ? 'ADMIN' : canRescue ? 'ORG' : 'CITIZEN';
  const isAdmin = role === 'ADMIN';
  const [region, setRegion] = useState('all');
  const [sev, setSev] = useState('');
  const [status, setStatus] = useState('ACTIVE'); // mặc định: ca đang hoạt động (với Admin là việc cần điều phối)
  const [days, setDays] = useState(DEFAULT_DAYS);
  const [adminMap, setAdminMap] = useState(null); // Admin: { cases, total, limit, days } từ /api/admin/sos/map
  const [full, setFull] = useState(null); // Admin: chi tiết đầy đủ của ca đang xem (null = đang tải, false = lỗi)
  const [focus, setFocus] = useState(null);
  const [detailFor, setDetailFor] = useState(null);
  const [fitKey, setFitKey] = useState(0);
  const [showHeat, setShowHeat] = useState(false);
  const [adminOrgs, setAdminOrgs] = useState([]);
  const [heatRaw, setHeatRaw] = useState([]);
  const [tempLoc, setTempLoc] = useState(null); // vị trí lấy tạm từ trình duyệt, chỉ dùng trên trang này, không lưu hồ sơ
  const [locating, setLocating] = useState(false); // đang chờ trình duyệt trả vị trí
  const [locErr, setLocErr] = useState(false); // xin vị trí thất bại hoặc bị từ chối

  /* Không dùng vị trí giả: base = null nghĩa là chưa biết vị trí người dùng (chưa bật GPS trong hồ sơ, chưa cấp quyền tạm) */
  const savedLoc = me?.lat != null && me?.lng != null ? { lat: me.lat, lng: me.lng } : null;
  const base = role === 'ORG' ? myOrg : savedLoc || tempLoc;
  const hasBase = base?.lat != null && base?.lng != null;

  useEffect(() => {
    if (me?.role !== 'ADMIN') return;
    load.markers().then((r) => setAdminOrgs((r?.markers || []).filter((m) => m.kind === 'ORG').map((m) => ({
      id: String(m.id), name: m.label, lat: m.lat, lng: m.lng, opStatus: { AVAILABLE: 'READY', BUSY: 'BUSY', INACTIVE: 'INACTIVE' }[m.status] || 'INACTIVE',
    }))));
  }, [me?.role]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => { if (me?.role === 'ADMIN' && showHeat) load.heatmap().then(setHeatRaw); }, [me?.role, showHeat]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Admin: ca trên bản đồ do backend lọc theo trạng thái / mức / thời gian, không dùng danh sách 100 ca mới nhất của trang
     Điều phối SOS (ca đang hoạt động gửi đã lâu sẽ bị thiếu). Tải lại khi đổi bộ lọc và mỗi lần ứng dụng đồng bộ dữ liệu
     (state.sos đổi: 30 giây một lần hoặc khi có sự kiện thời gian thực); đợi 300 ms để gộp các thay đổi liên tiếp. */
  useEffect(() => {
    if (!isAdmin) return undefined;
    let alive = true;
    const t = setTimeout(() => load.adminMap({ status, level: sev, days }).then((r) => { if (alive && r) setAdminMap(r); }), 300);
    return () => { alive = false; clearTimeout(t); };
  }, [isAdmin, status, sev, days, state.sos]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Admin mở chi tiết: dữ liệu bản đồ không có mô tả, tải chi tiết đầy đủ của ca */
  useEffect(() => {
    setFull(null);
    if (!isAdmin || !detailFor) return undefined;
    let alive = true;
    load.adminCase(detailFor.id).then((c) => { if (alive) setFull(c || false); });
    return () => { alive = false; };
  }, [isAdmin, detailFor?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const source = isAdmin ? adminMap?.cases || [] : state.sos;
  const inRegion = (s) => (region === 'near' ? hasBase && haversine(base, s) <= 10000 : REGIONS[region].test(s));
  const bounds = region === 'near' && hasBase
    ? [[base.lat - 0.09, base.lng - 0.09], [base.lat + 0.09, base.lng + 0.09]]
    : REGIONS[region].bounds || VN_BOUNDS;

  const list = useMemo(() => source.filter((s) => {
    if (role !== 'ADMIN' && (s.invalid || s.adminReview)) return false;
    if (role === 'ORG') {
      const mine = s.orgId === myOrg.id && ['ASSIGNED', 'ACCEPTED'].includes(s.status);
      if (!(mine || s.status === 'PENDING')) return false;
    }
    if (status === 'ACTIVE' && !ACTIVE_SOS.includes(s.status)) return false;
    if (status && status !== 'ACTIVE' && s.status !== status) return false;
    if (sev && s.severity !== sev) return false;
    return inRegion(s);
  }).sort((a, b) => SEVERITY[b.severity].w - SEVERITY[a.severity].w || b.createdAt - a.createdAt), [source, role, status, sev, region, base, myOrg]); // eslint-disable-line

  /* Người dân xem ca của người khác: không có mô tả, địa chỉ (backend ẩn) và tọa độ chỉ gần đúng ~100 m, nên hiện mã ca và khoảng cách.
     Admin: dữ liệu bản đồ không kèm mô tả (cho nhẹ), nên tiêu đề là mã ca, mô tả xem trong chi tiết */
  const isPublic = (s) => role === 'CITIZEN' && !s.mine;
  const distText = (s) => (hasBase ? `Cách bạn khoảng ${fmtDist(haversine(base, s))}` : 'Chưa có vị trí của bạn để tính khoảng cách');
  const titleOf = (s) => (isPublic(s) || isAdmin ? `Ca ${s.code}` : s.desc);
  const addressOf = (s) => s.address || `${Number(s.lat).toFixed(4)}, ${Number(s.lng).toFixed(4)}`;
  const placeOf = (s) => (isPublic(s) ? distText(s) : `${addressOf(s)}${!isAdmin && hasBase ? ` • cách ${fmtDist(haversine(base, s))}` : ''}`);
  const myActive = role === 'CITIZEN' && me && state.sos.find((s) => s.mine && ['ACCEPTED'].includes(s.status));
  const zoomedIn = region === 'near' || !!focus;

  const markers = list.map((s) => {
    const meta = SEVERITY[s.severity];
    const org = s.org;
    return {
      id: s.id, lat: s.lat, lng: s.lng, kind: 'sos',
      color: s.status === 'DONE' ? '#16a34a' : ['CANCELLED', 'INVALID'].includes(s.status) ? '#94a3b8' : meta.color,
      pulse: s.status === 'PENDING' && s.severity === 'KHAN_CAP',
      label: zoomedIn ? `${meta.label}: ${isPublic(s) ? s.code : s.address.split(',')[0] || s.code}` : null, // địa chỉ ca của người khác bị ẩn
      popup: (
        <div className="map-popup">
          <div className="row gap6 wrap"><Tag tone={meta.tone}>{meta.label}</Tag><Tag tone={SOS_STATUS[s.status].tone}>{SOS_STATUS[s.status].label}</Tag></div>
          <b>{titleOf(s)}</b>
          <span className="muted small"><MapPin size={12} /> {placeOf(s)} • {ago(s.createdAt)}</span>
          {org && <span className="small">Phụ trách: {role === 'ADMIN' ? <Link to={adminOrgLink(org.id)}><b>{org.name}</b></Link> : <b>{org.name}</b>}</span>}
          {role === 'CITIZEN' && <button className="btn btn-ghost btn-sm" onClick={() => setDetailFor(s)}><MapPin size={14} /> Xem chi tiết ca SOS</button>}
          {role === 'ORG' && <Link to={dispatchLink('ORG', s.id)} className="btn btn-primary btn-sm">Mở bảng điều phối</Link>}
          {role === 'ADMIN' && <Link to={dispatchLink('ADMIN', s.id)} className="btn btn-primary btn-sm">Điều phối ca này</Link>}
        </div>
      ),
    };
  });
  // Vùng ảnh hưởng quanh các ca đang hoạt động
  const circles = list.filter((s) => ACTIVE_SOS.includes(s.status))
    .map((s) => ({ lat: s.lat, lng: s.lng, radius: zoomedIn ? 400 : 9000, color: SEVERITY[s.severity].color }));
  if (role !== 'ADMIN' && hasBase) {
    markers.push({ id: 'me', lat: base.lat, lng: base.lng, kind: 'me', color: '#1d4ed8',
      label: zoomedIn ? (role === 'ORG' ? 'Trụ sở của tôi' : 'Vị trí của tôi') : null });
  }

  // Người dân: hiện trụ sở tổ chức đang xử lý ca của mình (hệ thống chưa có vị trí thời gian thực của đội cứu hộ)
  const hq = myActive?.org;
  if (hq?.lat != null && hq?.lng != null) {
    markers.push({ id: `hq-${hq.id}`, lat: hq.lat, lng: hq.lng, kind: 'org', color: '#0e7490', label: zoomedIn ? `Trụ sở ${hq.name}` : null, popup: <div className="map-popup"><b>{hq.name}</b><span className="small">Trụ sở tổ chức đang xử lý ca {myActive.code}</span></div> });
  }
  if (role === 'ADMIN') {
    adminOrgs.forEach((o) => markers.push({
      id: o.id, lat: o.lat, lng: o.lng, kind: 'org', color: o.opStatus === 'READY' ? '#2e7d4f' : o.opStatus === 'BUSY' ? '#c2410c' : '#7b878b',
      label: zoomedIn ? o.name : null,
      popup: <div className="map-popup"><Link to={adminOrgLink(o.id)} title="Xem chi tiết tổ chức"><b>{o.name}</b></Link><Tag tone={ORG_STATUS[o.opStatus].tone}>{ORG_STATUS[o.opStatus].label}</Tag></div>,
    }));
  }

  /* Bản đồ nhiệt (Admin): mật độ SOS đang hoạt động trong bán kính 500 m, chỉ để nhận biết cụm và ca trùng, không tự gộp ca */
  const activeAll = source.filter((s) => ACTIVE_SOS.includes(s.status) && s.severity !== 'KHONG');
  const heat = role === 'ADMIN' && showHeat ? (heatRaw.length ? heatRaw.map((h) => ({ lat: h.lat, lng: h.lng, n: Math.max(1, Math.round(h.weight * 4)) }))
    : activeAll.map((s) => ({ lat: s.lat, lng: s.lng, n: activeAll.filter((x) => haversine(s, x) <= 500).length }))) : [];
  const counts = Object.keys(SEVERITY).reduce((a, k) => ({ ...a, [k]: list.filter((s) => s.severity === k).length }), {});
  const pickRegion = (r) => { setRegion(r); setFocus(null); setFitKey((k) => k + 1); };
  /* Đã có vị trí thì dùng luôn; chưa có thì xin trình duyệt ngay lúc người dùng bấm.
     Vị trí lấy được chỉ giữ trong state của trang (tempLoc), không gọi API lưu vào hồ sơ.
     onReady nhận loc trực tiếp vì setTempLoc chỉ có hiệu lực ở lần render sau */
  const withLocation = (onReady) => {
    setLocErr(false);
    if (hasBase) { onReady(base); return; }
    if (!navigator.geolocation) { setLocErr(true); return; }
    setLocating(true);
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const loc = { lat: +p.coords.latitude.toFixed(5), lng: +p.coords.longitude.toFixed(5) };
        setTempLoc(loc);
        setLocating(false);
        onReady(loc);
      },
      () => { setLocating(false); setLocErr(true); },
      { enableHighAccuracy: true, timeout: 8000 },
    );
  };
  const defaultStatus = 'ACTIVE';
  const showsEnded = isAdmin && (status === '' || ENDED_SOS.includes(status)); // bộ lọc có lấy ca đã kết thúc
  const truncated = isAdmin && adminMap && adminMap.total > adminMap.cases.length;
  const shown = isAdmin && full ? full : detailFor; // Admin: chi tiết đầy đủ khi đã tải xong

  return (
    <div className="stack">
      <div className="map-toolbar">
        <div className="seg">
          {Object.entries(REGIONS).filter(([k]) => role !== 'ADMIN' || k !== 'near').map(([k, v]) => (
            <button key={k} className={region === k ? 'on' : ''} disabled={k === 'near' && locating}
              onClick={() => (k === 'near' ? withLocation(() => pickRegion('near')) : pickRegion(k))}>{v.label}</button>
          ))}
        </div>
        <select className="input sm" value={sev} onChange={(e) => setSev(e.target.value)} aria-label="Mức độ">
          <option value="">Mức độ: Tất cả</option>{Object.entries(SEVERITY).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        <select className="input sm" value={status} onChange={(e) => setStatus(e.target.value)} aria-label="Trạng thái">
          <option value="">Mọi trạng thái</option><option value="ACTIVE">Đang hoạt động</option>{/* Người dân, tổ chức: bản đồ là tình hình hiện tại (chỉ ca chưa xong được đồng bộ); Admin xem lại ca đã kết thúc theo khoảng thời gian */}{Object.entries(SOS_STATUS).filter(([k]) => isAdmin || ACTIVE_SOS.includes(k)).map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}
        </select>
        {showsEnded && (
          <select className="input sm" value={days} onChange={(e) => setDays(Number(e.target.value))} aria-label="Ca đã kết thúc trong khoảng"
            title="Ca đã kết thúc (hoàn thành, đã hủy, không hợp lệ) chỉ lấy trong khoảng này, tính theo lúc kết thúc. Ca đang hoạt động luôn hiện đủ.">
            {ENDED_DAYS.map(([d, l]) => <option key={d} value={d}>Ca kết thúc: {l}</option>)}
          </select>
        )}
        {role === 'ADMIN' && <button className={`btn btn-sm ${showHeat ? 'btn-primary' : 'btn-ghost'}`} aria-pressed={showHeat} onClick={() => setShowHeat(!showHeat)}><Flame size={14} /> Bản đồ nhiệt mật độ SOS</button>}
        {(sev || region !== 'all' || status !== defaultStatus || days !== DEFAULT_DAYS) && (
          <button className="btn btn-ghost btn-sm" onClick={() => { setSev(''); pickRegion('all'); setStatus(defaultStatus); setDays(DEFAULT_DAYS); }}><RefreshCw size={14} /> Xóa bộ lọc</button>
        )}
      </div>

      {truncated && (
        <div className="alert alert-info">
          Đang hiển thị {adminMap.cases.length} / {adminMap.total} ca khớp bộ lọc (tối đa {adminMap.limit} ca mỗi lần: ca đang hoạt động trước,
          sau đó ca kết thúc gần nhất). Hãy thu hẹp bộ lọc trạng thái, mức độ hoặc thời gian để xem đủ.
        </div>
      )}

      {myActive && <div className="alert alert-info">Đội cứu hộ đang xử lý ca {myActive.code} của bạn; bản đồ hiển thị trụ sở của tổ chức phụ trách. <Link to="/sos">Xem tiến trình</Link></div>}
      {locErr && (
        <div className="alert alert-danger">
          Bạn chưa cho phép truy cập vị trí nên chưa xem được các ca gần bạn. Hãy bật quyền vị trí cho trang này trên trình duyệt
          {me ? <> hoặc bật chia sẻ vị trí trong <Link to="/profile">Hồ sơ cá nhân</Link></> : null}.
        </div>
      )}

      <div className="map-layout">
        <div className="card map-card vn-map">
          <MapView center={[16.0, 106.0]} zoom={6} bounds={bounds} fitKey={fitKey} markers={markers} focus={focus} circles={circles} heat={heat} height={680} />
          <div className="map-overlay top-right">
            <span className="overlay-title">{REGIONS[region].label}</span>
            <div className="row gap6"><b className="overlay-num">{list.length}</b><span className="muted small">điểm sự cố</span></div>
            <div className="overlay-legend">
              {Object.entries(SEVERITY).map(([k, v]) => <span key={k}><i style={{ background: v.color }} />{v.label}<b>{counts[k]}</b></span>)}
            </div>
          </div>
          {role !== 'ADMIN' && (
            <button className="map-overlay bottom-left btn btn-primary btn-sm" disabled={locating}
              onClick={() => withLocation((p) => { setRegion('near'); setFocus([p.lat, p.lng]); })}>
              <Crosshair size={15} /> {locating ? 'Đang lấy vị trí…' : role === 'ORG' ? 'Trụ sở của tôi' : 'Vị trí của tôi'}
            </button>
          )}
        </div>
        <aside className="card map-list">
          <div className="map-list-head"><b>Danh sách sự cố</b><Tag tone="red">{list.length} ca</Tag></div>
          {list.length === 0 && <div className="pad muted">Không có ca SOS phù hợp với bộ lọc.</div>}
          {list.map((s) => (
            <div key={s.id} className={`map-item sev-${s.severity}`}>
              <div className="row between"><div className="row gap6"><Sev level={s.severity} /><Tag tone={SOS_STATUS[s.status].tone}>{SOS_STATUS[s.status].label}</Tag></div><span className="muted small">{ago(s.createdAt)}</span></div>
              <b>{titleOf(s)}</b>
              <span className="muted small"><MapPin size={12} /> {placeOf(s)}</span>
              <div className="row gap6">
                <button className="btn btn-ghost btn-sm" onClick={() => { setFocus([s.lat, s.lng]); setDetailFor(s); }}>Xem chi tiết</button>
                {role === 'ORG' && <button className="btn btn-primary btn-sm" onClick={() => { setFocus([s.lat, s.lng]); openGoogleMapsDirections(base, s); }}><Navigation size={14} /> Đường đi</button>}
              </div>
            </div>
          ))}
        </aside>
      </div>
      {detailFor && (
        <Modal title={`Chi tiết ca SOS ${detailFor.code}`} onClose={() => setDetailFor(null)} footer={<>
          {isAdmin && <Link to={dispatchLink('ADMIN', detailFor.id)} className="btn btn-ghost">Điều phối ca này</Link>}
          <button className="btn btn-primary" onClick={() => { setFocus([detailFor.lat, detailFor.lng]); setDetailFor(null); }}>Xem trên bản đồ</button>
        </>}>
          <div className="stack">
            <div className="row gap6 wrap"><Sev level={shown.severity} /><Tag tone={SOS_STATUS[shown.status].tone}>{SOS_STATUS[shown.status].label}</Tag></div>
            {isAdmin && full === null && <SkeletonLines lines={2} label="Đang tải mô tả ca…" />}
            {isAdmin && full === false && <span className="muted small">Không tải được mô tả của ca. Hãy mở trong trang Điều phối SOS.</span>}
            {!isPublic(shown) && shown.desc && <h3>{shown.desc}</h3>}
            <div className="row between small gap8"><span className="muted">Vị trí</span><b>{placeOf(shown)}</b></div>
            <div className="row between small gap8"><span className="muted">Thời gian gửi</span><b>{fmtDateTime(shown.createdAt)} ({ago(shown.createdAt)})</b></div>
            {shown.org && <div className="row between small gap8"><span className="muted">Tổ chức phụ trách</span><b>{shown.org.name}</b></div>}
            {isPublic(shown) && <span className="muted small">Mô tả và địa chỉ chi tiết chỉ hiển thị cho người gửi, tổ chức cứu hộ và Admin.</span>}
          </div>
        </Modal>
      )}
    </div>
  );
}
