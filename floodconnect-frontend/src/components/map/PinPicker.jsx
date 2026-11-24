import { useEffect, useRef, useState } from 'react';
import { Crosshair } from 'lucide-react';
import { useApp } from '@/hooks';
import MapView, { VN_BOUNDS } from '@/components/map/MapView';
import { geoJsonBounds, validateCoords } from '@/utils';

/*
 * Đặt ghim một vị trí chính xác trên bản đồ (dùng cho trụ sở tổ chức: AI Matching tính khoảng cách từ đây tới ca SOS).
 * Ô Tỉnh, Phường/Xã nằm ở form cha; bản đồ đi theo xã đang chọn (vẽ đường viền), người dùng chạm hoặc kéo ghim tới đúng chỗ,
 * hoặc bấm lấy GPS. Ghim là vị trí thật nên địa chỉ đi theo ghim: mỗi lần ghim đổi, tra xã chứa ghim (bảng địa giới ở backend)
 * và tự cập nhật Tỉnh, Phường/Xã nếu khác. Người dùng tự đổi xã sau khi đã đặt ghim thì bản đồ đưa tới xã mới và nhắc đặt lại
 * ghim. Backend cũng kiểm tra lại ghim phải nằm trong xã đã chọn.
 *
 * Props: wardCode (xã đang chọn), point { lat, lng } | null, onChange(point), onPickWard({ provinceCode, wardCode }), label.
 */
const VIEW_D = 0.003; // nửa cạnh khung nhìn quanh ghim (~300 m)
const around = (p) => [[p.lat - VIEW_D, p.lng - VIEW_D], [p.lat + VIEW_D, p.lng + VIEW_D]];
const round5 = (x) => +Number(x).toFixed(5);

export default function PinPicker({ wardCode, point, onChange, onPickWard, label = 'Vị trí' }) {
  const { sos } = useApp();
  const [wardInfo, setWardInfo] = useState(null); // đường viền + điểm đại diện của xã đang chọn
  const [located, setLocated] = useState(undefined); // undefined: chưa tra; null: ghim không thuộc xã nào; ward
  const [geoOff, setGeoOff] = useState(false); // tra cứu địa giới lỗi: bỏ qua phần kiểm tra, vẫn đặt ghim được
  const [view, setView] = useState(point ? around(point) : VN_BOUNDS);
  const [viewKey, setViewKey] = useState(0);
  const [locating, setLocating] = useState(false);
  const [gpsErr, setGpsErr] = useState('');
  const [autoWard, setAutoWard] = useState(''); // mã xã vừa được tự điền theo ghim (để hiện dòng thông báo)
  const seq = useRef(0);
  const latestWard = useRef(wardCode);
  latestWard.current = wardCode;

  const fit = (b) => { setView(b); setViewKey((k) => k + 1); };
  const put = (p, { recenter = false } = {}) => {
    const q = { lat: round5(p.lat), lng: round5(p.lng) };
    onChange(q);
    if (recenter) fit(around(q));
  };

  /* Đổi xã: tải đường viền; ghim chưa có hoặc nằm ngoài xã mới thì đưa bản đồ tới xã để người dùng chạm đúng chỗ */
  useEffect(() => {
    if (!wardCode || geoOff) { setWardInfo(null); return undefined; }
    let alive = true;
    sos.wardDetail(wardCode).then((w) => {
      if (!alive) return;
      if (!w) { setGeoOff(true); setWardInfo(null); return; }
      setWardInfo(w);
      if (!point || (located?.code && located.code !== wardCode)) { const b = geoJsonBounds(w.outline); if (b) fit(b); }
    });
    return () => { alive = false; };
  }, [wardCode, geoOff]); // eslint-disable-line react-hooks/exhaustive-deps

  /* Ghim đổi: tra xã chứa ghim; khác xã đang chọn (hoặc chưa chọn) thì tự cập nhật Tỉnh, Phường/Xã theo ghim */
  useEffect(() => {
    if (!point || geoOff || validateCoords(point.lat, point.lng)) { setLocated(undefined); return; }
    const n = ++seq.current;
    sos.locateWard(point.lat, point.lng).then((r) => {
      if (n !== seq.current) return;
      if (!r) { setGeoOff(true); setLocated(undefined); return; }
      setLocated(r.ward || null);
      if (r.ward && r.ward.code !== latestWard.current) {
        onPickWard({ provinceCode: r.ward.provinceCode, wardCode: r.ward.code });
        setAutoWard(r.ward.code);
      }
    });
  }, [point?.lat, point?.lng, geoOff]); // eslint-disable-line react-hooks/exhaustive-deps

  const locate = () => {
    if (!navigator.geolocation) { setGpsErr('Trình duyệt không hỗ trợ định vị, hãy chạm vào bản đồ để đặt ghim.'); return; }
    setLocating(true); setGpsErr('');
    navigator.geolocation.getCurrentPosition(
      (p) => { setLocating(false); put({ lat: p.coords.latitude, lng: p.coords.longitude }, { recenter: true }); },
      () => { setLocating(false); setGpsErr('Không lấy được vị trí hiện tại, hãy chạm vào bản đồ để đặt ghim.'); },
      { enableHighAccuracy: true, timeout: 8000 },
    );
  };

  const mismatch = wardCode && located && located.code !== wardCode ? located : null;

  return (
    <div className="stack-sm">
      <div className="row gap6 wrap">
        <button type="button" className="btn btn-soft btn-sm" disabled={locating} onClick={locate}>
          <Crosshair size={15} /> {locating ? 'Đang lấy vị trí…' : 'Tôi đang ở đây (GPS)'}
        </button>
      </div>
      {gpsErr && <span className="muted small">{gpsErr}</span>}
      <MapView layerSwitch height={280} center={point ? [point.lat, point.lng] : [16.0, 106.0]} zoom={point ? 16 : 5}
        bounds={view} fitKey={viewKey} onMapClick={(p) => put(p)}
        outline={wardInfo ? { id: wardInfo.code, geometry: wardInfo.outline } : null}
        markers={point ? [{ id: 'pin', lat: point.lat, lng: point.lng, kind: 'org', color: '#0e7490', label, onDragEnd: (p) => put(p) }] : []} />
      <span className="muted small">
        {point
          ? 'Chạm vào bản đồ hoặc kéo ghim tới đúng vị trí.'
          : 'Chọn Phường/Xã ở trên để bản đồ đưa tới khu vực, rồi chạm vào đúng vị trí để đặt ghim.'}
      </span>
      {located === null && <span className="small field-error">Ghim không nằm trong phường/xã nào (có thể ở ngoài biển), hãy kiểm tra lại.</span>}
      {autoWard && autoWard === wardCode && located?.code === wardCode && (
        <span className="small">Đã cập nhật Tỉnh, Phường/Xã theo vị trí ghim: <b>{located.name}, {located.provinceName}</b>.</span>
      )}
      {/* Chỉ xảy ra khi người dùng tự đổi xã sau khi đã đặt ghim */}
      {mismatch && (
        <div className="alert alert-info">
          Ghim đang nằm ở <b>{mismatch.name}, {mismatch.provinceName}</b>, không thuộc Phường/Xã vừa chọn. Hãy chạm vào bản đồ để đặt lại ghim.
        </div>
      )}
      {geoOff && <span className="muted small">Tạm thời chưa tra cứu được phường/xã, bạn vẫn đặt ghim bình thường.</span>}
      {point && <span className="muted small">Tọa độ: {point.lat}, {point.lng}</span>}
    </div>
  );
}
