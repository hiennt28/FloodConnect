import { useEffect, useRef, useState } from 'react';
import { CheckCircle2, ChevronDown, ChevronUp, ClipboardPaste, Copy, Crosshair, LocateFixed } from 'lucide-react';
import { useApp } from '@/hooks';
import MapView, { VN_BOUNDS } from '@/components/map/MapView';
import AddressPicker from '@/components/common/AddressPicker';
import { Modal } from '@/components/common';
import { fmtDist, geoJsonBounds, haversine, isShortMapLink, parseLocationText, provinceName, validateCoords, wardName } from '@/utils';

/*
 * Chọn vị trí cần cứu hộ cho form SOS. Người dân không cần biết tọa độ:
 *  - Có GPS: bản đồ mở tại vị trí GPS, chạm hoặc kéo ghim nếu lệch.
 *  - Không có GPS: chọn Tỉnh → Phường/Xã, bản đồ đưa tới xã (có đường viền), chạm vào đúng chỗ để đặt ghim.
 *  - Gửi hộ người khác: dán tọa độ hoặc link Google Maps người thân gửi.
 * Ghim đặt ở đâu, hệ thống tra phường/xã chứa ghim (bảng địa giới ở backend, không gọi dịch vụ ngoài) để tự điền địa chỉ
 * và cảnh báo khi ghim nằm ngoài xã đã chọn. Tra cứu lỗi thì chỉ bỏ qua phần kiểm tra, không chặn gửi SOS.
 * Báo lên form qua onChange({ point, adminText, ready, blockMsg, proxy }).
 */

/* GPS sai số trên mức này (m) thì bắt người dân xác nhận lại vị trí trên bản đồ */
const GPS_TRUST_M = 100;
/* Nửa cạnh khung nhìn quanh ghim (~200 m), đủ gần để nhận ra nhà, ngõ */
const VIEW_D = 0.002;
/* Ghim cách vị trí GPS hơn mức này (và hơn 2 lần sai số GPS) thì hỏi có phải gửi SOS hộ người khác không */
const FAR_FROM_GPS_M = 2000;

const around = (p) => [[p.lat - VIEW_D, p.lng - VIEW_D], [p.lat + VIEW_D, p.lng + VIEW_D]];
const round5 = (x) => +Number(x).toFixed(5);

/* nudge: form tăng số này khi người dân bấm gửi mà chưa xác nhận vị trí → cuộn tới ô xác nhận và nháy viền */
export default function LocationPicker({ onChange, gpsRequest = 0, nudge = 0 }) {
  const { me, sos, toast } = useApp();
  const confirmRef = useRef(null);
  const profilePoint = me.lat != null && me.lng != null ? { lat: me.lat, lng: me.lng } : null;

  const [point, setPointState] = useState(profilePoint);
  /* Nguồn của ghim: 'gps' (vừa lấy), 'profile' (lưu trong hồ sơ, có thể cũ), 'map' (chạm/kéo trên bản đồ), 'paste' (dán tọa độ/link) */
  const [src, setSrc] = useState(profilePoint ? 'profile' : null);
  const [gps, setGps] = useState(null); // { lat, lng, accuracy } của lần lấy GPS gần nhất
  const [locating, setLocating] = useState(false);
  const [gpsErr, setGpsErr] = useState('');
  const [confirmed, setConfirmed] = useState(false); // người dân đã bấm "Đúng vị trí này"
  const [proxy, setProxy] = useState(false); // người dân xác nhận đang gửi SOS hộ người khác
  const [askFar, setAskFar] = useState(false);

  const [codes, setCodes] = useState({ provinceCode: '', wardCode: '' });
  const [wardBy, setWardBy] = useState(null); // 'user': người dân tự chọn; 'auto': hệ thống điền theo ghim
  const [wardInfo, setWardInfo] = useState(null); // điểm đại diện + đường viền của xã đang chọn
  const [located, setLocated] = useState(undefined); // undefined: chưa tra; null: ghim không thuộc xã nào; { ward, approximate }
  const [geoOff, setGeoOff] = useState(false); // tra cứu địa giới đang lỗi: bỏ qua phần kiểm tra phường/xã
  const [keepWard, setKeepWard] = useState(''); // "mã xã của ghim|mã xã đã chọn" người dân đã bấm giữ nguyên

  const [view, setView] = useState(profilePoint ? around(profilePoint) : VN_BOUNDS);
  const [viewKey, setViewKey] = useState(0);
  const [pasteOpen, setPasteOpen] = useState(false);
  const [pasteText, setPasteText] = useState('');
  const [pasteErr, setPasteErr] = useState('');

  const fitWard = useRef(false); // xã vừa do người dân chọn: đưa bản đồ tới xã khi tải xong đường viền
  const locateSeq = useRef(0); // bỏ kết quả tra cứu cũ khi ghim đã đổi
  const latest = useRef({ wardBy, codes });
  latest.current = { wardBy, codes };

  const fit = (b) => { setView(b); setViewKey((k) => k + 1); };
  /* Mọi thay đổi ghim đều phải xác nhận lại. Chạm/kéo trên bản đồ thì giữ nguyên khung nhìn để bản đồ không nhảy */
  const setPoint = (p, from, { recenter = true } = {}) => {
    const q = { lat: round5(p.lat), lng: round5(p.lng) };
    setPointState(q); setSrc(from); setConfirmed(false); setProxy(false);
    if (recenter) fit(around(q));
  };
  const pickOnMap = (p) => setPoint(p, 'map', { recenter: false });

  const locate = () => {
    if (!navigator.geolocation) { setGpsErr('Trình duyệt không hỗ trợ định vị. Hãy chọn Tỉnh, Phường/Xã rồi chạm vào bản đồ để đặt ghim.'); return; }
    setLocating(true); setGpsErr('');
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const g = { lat: round5(p.coords.latitude), lng: round5(p.coords.longitude), accuracy: p.coords.accuracy ?? null };
        setLocating(false); setGps(g); setPoint(g, 'gps');
      },
      () => {
        setLocating(false);
        setGpsErr('Không lấy được vị trí hiện tại. Hãy bật quyền vị trí cho trang này, hoặc chọn Tỉnh, Phường/Xã rồi chạm vào bản đồ để đặt ghim.');
      },
      { enableHighAccuracy: true, timeout: 8000 },
    );
  };
  useEffect(() => { if (gpsRequest) locate(); }, [gpsRequest]); // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(() => {
    if (!nudge || !confirmRef.current) return;
    confirmRef.current.scrollIntoView({ behavior: 'smooth', block: 'center' });
    confirmRef.current.querySelector('button')?.focus({ preventScroll: true });
  }, [nudge]);

  /* Ghim đổi: tra phường/xã chứa ghim. Chưa tự chọn xã thì điền theo ghim; đã chọn tỉnh nhưng chưa chọn xã thì chỉ điền khi cùng tỉnh */
  useEffect(() => {
    if (!point || geoOff || validateCoords(point.lat, point.lng)) { setLocated(undefined); return; }
    const seq = ++locateSeq.current;
    sos.locateWard(point.lat, point.lng).then((r) => {
      if (seq !== locateSeq.current) return;
      if (!r) { setGeoOff(true); setLocated(undefined); return; }
      setLocated(r.ward ? r : null);
      const { wardBy: by, codes: c } = latest.current;
      const sameProvinceNoWard = by === 'user' && !c.wardCode && c.provinceCode === r.ward?.provinceCode;
      if (r.ward && (by !== 'user' || sameProvinceNoWard)) {
        setCodes({ provinceCode: r.ward.provinceCode, wardCode: r.ward.code });
        setWardBy('auto');
      }
    });
  }, [point?.lat, point?.lng, geoOff]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Xã đổi: tải điểm đại diện + đường viền để vẽ; nếu người dân vừa tự chọn xã thì đưa bản đồ tới xã */
  useEffect(() => {
    if (!codes.wardCode || geoOff) { setWardInfo(null); return undefined; }
    let alive = true;
    sos.wardDetail(codes.wardCode).then((w) => {
      if (!alive) return;
      if (!w) { setGeoOff(true); setWardInfo(null); return; }
      setWardInfo(w);
      if (fitWard.current) {
        fitWard.current = false;
        const b = geoJsonBounds(w.outline);
        if (b) fit(b);
      }
    });
    return () => { alive = false; };
  }, [codes.wardCode, geoOff]); // eslint-disable-line react-hooks/exhaustive-deps

  const pickWard = ({ provinceCode, wardCode }) => {
    setCodes({ provinceCode, wardCode });
    setWardBy(provinceCode ? 'user' : null);
    setKeepWard('');
    if (wardCode) fitWard.current = true;
  };

  const applyPasted = () => {
    const p = parseLocationText(pasteText);
    if (!p) {
      setPasteErr(isShortMapLink(pasteText)
        ? 'Link rút gọn (maps.app.goo.gl) không chứa tọa độ. Hãy mở link, rồi sao chép tọa độ (VD 16.0544, 108.2022) hoặc link đầy đủ trên thanh địa chỉ.'
        : 'Không đọc được tọa độ. Dán theo dạng "16.0544, 108.2022" hoặc link Google Maps đầy đủ.');
      return;
    }
    const e = validateCoords(p.lat, p.lng);
    if (e) { setPasteErr(e); return; }
    setPasteErr(''); setPasteText(''); setPasteOpen(false);
    setPoint(p, 'paste');
  };

  const copyCoords = async () => {
    try { await navigator.clipboard.writeText(`${point.lat}, ${point.lng}`); toast('Đã sao chép tọa độ'); }
    catch { toast('Không sao chép được, hãy chép tay tọa độ', 'err'); }
  };

  // ---- trạng thái suy ra ----
  const coordErr = point ? validateCoords(point.lat, point.lng) : null;
  const accuracy = src === 'gps' ? gps?.accuracy : null;
  /* GPS vừa lấy và đủ chính xác thì không bắt xác nhận để không làm chậm ca khẩn cấp; còn lại (hồ sơ cũ, chạm/kéo, dán, GPS kém) phải xác nhận */
  const needsConfirm = src !== 'gps' || (accuracy != null && accuracy > GPS_TRUST_M);
  const farFromGps = !!(gps && point && src !== 'gps'
    && haversine(gps, point) > Math.max(FAR_FROM_GPS_M, 2 * (gps.accuracy || 0)));
  /* Ghim nằm ngoài xã người dân đã chọn (bỏ qua khi ghim sát ranh giới: kết quả chỉ là gần đúng) */
  const mismatch = wardBy === 'user' && codes.wardCode && located?.ward && !located.approximate
    && located.ward.code !== codes.wardCode && keepWard !== `${located.ward.code}|${codes.wardCode}` ? located.ward : null;
  const adminText = [wardName(codes.provinceCode, codes.wardCode), provinceName(codes.provinceCode)].filter(Boolean).join(', ');
  const ready = !!point && !coordErr && (!needsConfirm || confirmed);
  const blockMsg = !point
    ? 'Hãy xác định vị trí cần cứu hộ: bấm lấy GPS, hoặc chọn Tỉnh, Phường/Xã rồi chạm vào bản đồ để đặt ghim.'
    : coordErr || (ready ? '' : 'Hãy kiểm tra ghim trên bản đồ rồi bấm “Đúng vị trí này” trước khi gửi SOS.');

  useEffect(() => { onChange({ point, adminText, ready, blockMsg, proxy }); }, [point, adminText, ready, blockMsg, proxy]); // eslint-disable-line react-hooks/exhaustive-deps

  const confirm = () => { if (farFromGps) setAskFar(true); else setConfirmed(true); };
  const answerFar = (isProxy) => { setAskFar(false); setProxy(isProxy); setConfirmed(true); };

  return (
    <div className="stack-sm">
      <div className="row gap6 wrap">
        <button type="button" className="btn btn-soft btn-sm" disabled={locating} onClick={locate}>
          <Crosshair size={15} /> {locating ? 'Đang lấy vị trí…' : gps ? 'Lấy lại GPS' : 'Lấy vị trí hiện tại (GPS)'}
        </button>
        {gps && point && src !== 'gps' && (
          <button type="button" className="btn btn-ghost btn-sm" onClick={() => setPoint(gps, 'gps')}><LocateFixed size={15} /> Về vị trí GPS</button>
        )}
      </div>
      {gpsErr && <span className="muted small">{gpsErr}</span>}

      <AddressPicker provinceCode={codes.provinceCode} wardCode={codes.wardCode} onChange={pickWard} />

      <MapView layerSwitch height={300} center={point ? [point.lat, point.lng] : [16.0, 106.0]} zoom={point ? 16 : 5}
        bounds={view} fitKey={viewKey} onMapClick={pickOnMap}
        outline={wardInfo ? { id: wardInfo.code, geometry: wardInfo.outline } : null}
        circles={accuracy ? [{ lat: gps.lat, lng: gps.lng, radius: accuracy, color: '#1d4ed8' }] : []}
        markers={point ? [{ id: 'sos-point', lat: point.lat, lng: point.lng, kind: 'me', color: '#1d4ed8', label: 'Vị trí cần cứu hộ', onDragEnd: pickOnMap }] : []} />

      <span className="muted small">
        {point
          ? 'Chạm vào bản đồ hoặc kéo ghim tới đúng chỗ cần cứu hộ.'
          : 'Chưa có vị trí. Bấm lấy GPS, hoặc chọn Tỉnh, Phường/Xã để bản đồ đưa tới khu vực, rồi chạm vào đúng chỗ cần cứu hộ.'}
        {accuracy ? ` Sai số GPS khoảng ${fmtDist(accuracy)}.` : ''}
        {src === 'profile' ? ' Đây là vị trí lưu trong hồ sơ của bạn, có thể đã cũ.' : ''}
      </span>
      {/* Ô xác nhận đặt ngay dưới bản đồ, nền màu, nút chính: bước bắt buộc nên phải dễ thấy; key = nudge để nháy lại mỗi lần bị nhắc */}
      {point && !coordErr && needsConfirm && (confirmed
        ? <div className="alert alert-success row gap6"><CheckCircle2 size={16} /> <b>Đã xác nhận vị trí</b>{proxy ? ' (gửi hộ người khác)' : ''}</div>
        : (
          <div ref={confirmRef} key={nudge} className={`alert alert-info row between gap8 wrap ${nudge ? 'flash' : ''}`}>
            <span><b>Ghim đã đúng chỗ cần cứu hộ chưa?</b> Kiểm tra trên bản đồ rồi bấm xác nhận.</span>
            <button type="button" className="btn btn-primary" onClick={confirm}><CheckCircle2 size={16} /> Đúng vị trí này</button>
          </div>
        ))}
      {coordErr && <span className="small field-error">{coordErr}</span>}
      {!coordErr && located === null && <span className="muted small">Ghim không nằm trong phường/xã nào (có thể ở ngoài biển), hãy kiểm tra lại.</span>}
      {located?.approximate && <span className="muted small">Ghim nằm sát ranh giới, phường/xã đã điền chỉ là gợi ý.</span>}
      {geoOff && <span className="muted small">Tạm thời chưa tra cứu được phường/xã. Bạn vẫn đặt ghim và gửi SOS được bình thường.</span>}

      {mismatch && (
        <div className="alert alert-info stack-sm">
          <span>Ghim đang nằm ở <b>{mismatch.name}, {mismatch.provinceName}</b>, không thuộc <b>{adminText}</b> đã chọn.</span>
          <div className="row gap6 wrap">
            <button type="button" className="btn btn-primary btn-sm" onClick={() => { setCodes({ provinceCode: mismatch.provinceCode, wardCode: mismatch.code }); setKeepWard(''); }}>
              Đổi địa chỉ sang {mismatch.name}
            </button>
            <button type="button" className="btn btn-ghost btn-sm" onClick={() => setKeepWard(`${mismatch.code}|${codes.wardCode}`)}>Giữ {wardName(codes.provinceCode, codes.wardCode)}</button>
          </div>
        </div>
      )}

      {point && (
        <div className="row gap8 wrap small">
          <span className="muted">Tọa độ: {point.lat}, {point.lng}</span>
          <button type="button" className="link" onClick={copyCoords}><Copy size={13} /> Sao chép</button>
        </div>
      )}
      <button type="button" className="link small" style={{ alignSelf: 'flex-start' }} aria-expanded={pasteOpen} onClick={() => setPasteOpen(!pasteOpen)}>
        {pasteOpen ? <ChevronUp size={13} /> : <ChevronDown size={13} />} Tôi có tọa độ hoặc link vị trí (VD người thân gửi qua Zalo)
      </button>
      {pasteOpen && (
        <div className="stack-sm">
          <div className="row gap6">
            <input className="input" value={pasteText} onChange={(e) => { setPasteText(e.target.value); setPasteErr(''); }}
              onKeyDown={(e) => { if (e.key === 'Enter') applyPasted(); }}
              placeholder="VD: 16.0544, 108.2022 hoặc https://www.google.com/maps/..." aria-label="Tọa độ hoặc link vị trí" />
            <button type="button" className="btn btn-soft btn-sm" disabled={!pasteText.trim()} onClick={applyPasted}><ClipboardPaste size={15} /> Dùng</button>
          </div>
          {pasteErr && <span className="small field-error">{pasteErr}</span>}
        </div>
      )}

      {askFar && (
        <Modal title="Vị trí ghim cách xa bạn" onClose={() => setAskFar(false)} footer={<>
          <button className="btn btn-ghost" onClick={() => setAskFar(false)}>Để tôi chỉnh lại</button>
          <button className="btn btn-ghost" onClick={() => answerFar(false)}>Vị trí GPS của tôi bị sai</button>
          <button className="btn btn-primary" onClick={() => answerFar(true)}>Đúng, tôi gửi hộ người khác</button>
        </>}>
          <p>Ghim cách vị trí GPS hiện tại của bạn khoảng <b>{fmtDist(haversine(gps, point))}</b>. Bạn đang gửi SOS cho người khác?</p>
          <p className="muted small">Nếu gửi hộ, đội cứu hộ sẽ gọi số điện thoại của bạn, nên hãy ghi trong mô tả tên và số điện thoại của người đang ở chỗ cần cứu.</p>
        </Modal>
      )}
    </div>
  );
}
