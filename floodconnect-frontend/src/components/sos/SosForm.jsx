import { useState } from 'react';
import { Asterisk, ChevronDown, ChevronUp, CloudRain, MapPin, Siren } from 'lucide-react';
import { useApp } from '@/hooks';
import MapView from '@/components/map/MapView';
import { Field, Modal, Sev, Tag } from '@/components/common';
import { ago, DUP_RADIUS_M, DUP_WINDOW_MIN, fmtDateTime, fmtDist, SEVERITY, SOS_STATUS } from '@/utils';
import LocationPicker from './LocationPicker';

/* Cột địa chỉ của ca SOS là varchar(255): mốc tối đa 150 ký tự, cộng tên phường/xã, tỉnh vẫn còn dư */
const LANDMARK_MAX = 150;
const ADDRESS_MAX = 255;
/* Mốc không bắt buộc (nhiều người gặp nạn không có số nhà), nhưng đã ghi thì phải có nghĩa: ít nhất 3 ký tự, có chữ hoặc số */
const landmarkError = (s) => (s && (s.length < 3 || !/[\p{L}\p{N}]/u.test(s))
  ? 'Số nhà, ngõ hoặc mốc cần ít nhất 3 ký tự và có chữ hoặc số. Nếu không biết, hãy để trống.' : '');

/* Khung bản đồ chứa cả hai điểm (cách nhau tối đa 500 m), chừa lề để không cắt marker */
const pairBounds = (a, b) => {
  const d = 0.003;
  return [[Math.min(a.lat, b.lat) - d, Math.min(a.lng, b.lng) - d], [Math.max(a.lat, b.lat) + d, Math.max(a.lng, b.lng) + d]];
};

export function SosForm({ onCreated }) {
  const { sos, toast } = useApp();
  const [desc, setDesc] = useState('');
  const [landmark, setLandmark] = useState(''); // số nhà, ngõ, mốc dễ nhận biết (người dân tự gõ)
  /* Vị trí do LocationPicker báo lên: point { lat, lng } | null, adminText "Phường/Xã, Tỉnh", ready (đủ điều kiện gửi), blockMsg, proxy (gửi hộ) */
  const [picked, setPicked] = useState({ point: null, adminText: '', ready: false, blockMsg: '', proxy: false });
  const [gpsRequest, setGpsRequest] = useState(0); // nút SOS lớn: tăng để LocationPicker lấy GPS
  const [nudge, setNudge] = useState(0); // gửi khi chưa xác nhận vị trí: tăng để LocationPicker cuộn tới ô xác nhận và nháy
  /* Nhắc MỘT lần khi mốc trống: askLandmark = đang mở hộp nhắc; landmarkAsked = đã nhắc rồi, lần gửi sau không hỏi lại */
  const [askLandmark, setAskLandmark] = useState(false);
  const [landmarkAsked, setLandmarkAsked] = useState(false);
  const [err, setErr] = useState('');
  const [nearby, setNearby] = useState(null);
  const [openDup, setOpenDup] = useState(null); // ca trùng đang mở xem chi tiết
  const [sending, setSending] = useState(false);

  const loc = picked.point;
  const place = [landmark.trim(), picked.adminText].filter(Boolean).join(', ');
  const onPicked = (p) => { setPicked(p); if (p.ready) setErr(''); };

  const focusLandmark = () => document.getElementById('sos-landmark')?.focus();
  /* Thứ tự kiểm tra: vị trí → mô tả → mốc (nếu có ghi) → nhắc mốc trống (một lần) → backend (ca trùng, một ca đang hoạt động) */
  const send = async (force, skipLandmark = false) => {
    if (sending) return;
    if (!picked.ready) { setErr(picked.blockMsg); if (picked.point) setNudge((n) => n + 1); return; }
    if (!desc.trim()) { setErr('Vui lòng mô tả tình trạng cần cứu hộ.'); document.getElementById('sos-desc')?.focus(); return; }
    const lmErr = landmarkError(landmark.trim());
    if (lmErr) { setErr(lmErr); focusLandmark(); return; }
    if (!landmark.trim() && !skipLandmark && !landmarkAsked) { setErr(''); setLandmarkAsked(true); setAskLandmark(true); return; }
    setSending(true);
    const r = await sos.create({ desc, lat: loc.lat, lng: loc.lng, address: place.slice(0, ADDRESS_MAX) || null, force });
    setSending(false);
    if (r.error) { setErr(r.error); return; }
    if (r.nearby) { setNearby(r.nearby); setOpenDup(null); return; }
    toast(r.adminReview ? 'Đã nhận SOS. Admin sẽ xem xét và xác minh với bạn.' : 'Yêu cầu SOS đã được gửi thành công');
    setNearby(null);
    setDesc('');
    setLandmark('');
    setLandmarkAsked(false);
    onCreated(r.id);
  };

  return (
    <div className="card sos-form">
      <div className="sos-hero">
        <button type="button" className="sos-button" onClick={() => (loc ? document.getElementById('sos-desc')?.focus() : setGpsRequest((n) => n + 1))} aria-label="Bắt đầu gửi SOS"><Asterisk size={40} /><span>SOS</span></button>
        <div>
          <h2>Gửi yêu cầu cứu hộ khẩn cấp</h2>
          <p className="muted">Mô tả ngắn tình trạng của bạn (số người, trẻ em/người cao tuổi, bị thương, mắc kẹt...) rồi bấm “Xác nhận gửi SOS”. AI đánh giá mức độ khẩn cấp để các tổ chức cứu hộ phù hợp tiếp nhận.</p>
        </div>
      </div>
      <div className="stack">
        {/* group: không bọc bản đồ trong <label>, nếu không mỗi lần chạm/thả ghim trình duyệt lại tự bấm nút GPS */}
        <Field label="Vị trí cần cứu hộ" required group>
          <LocationPicker onChange={onPicked} gpsRequest={gpsRequest} nudge={nudge} />
        </Field>
        <Field label="Số nhà, ngõ hoặc mốc dễ nhận biết" hint="Không bắt buộc, nhưng giúp đội cứu hộ tìm đúng nhà nhanh hơn. Phường/Xã và Tỉnh tự điền theo vị trí ghim.">
          <input id="sos-landmark" className="input" value={landmark} maxLength={LANDMARK_MAX} onChange={(e) => setLandmark(e.target.value)} placeholder="VD: Kiệt 126 Nguyễn Trãi, nhà mái tôn xanh cạnh chợ" />
        </Field>
        <Field label="Mô tả tình trạng" required hint="AI phân tích nội dung chữ này để đánh giá mức độ nguy hiểm.">
          <textarea id="sos-desc" className="input" rows={4} value={desc} onChange={(e) => setDesc(e.target.value)} placeholder="VD: Nước ngập 1,2 m, cụ bà 82 tuổi khó di chuyển, đang mắc kẹt trên gác lửng." />
        </Field>
        {picked.proxy && (
          <div className="alert alert-info">Bạn đang gửi SOS cho người khác: đội cứu hộ sẽ gọi số điện thoại của bạn, nên hãy ghi trong mô tả <b>tên và số điện thoại của người đang ở chỗ cần cứu</b>.</div>
        )}
        <span className="muted small"><CloudRain size={13} /> Hệ thống tự lấy giờ gửi, thời tiết tại vị trí và mật độ SOS xung quanh, bạn không cần nhập.</span>
        {/* Tóm tắt bằng chữ: đọc lại tên xã dễ phát hiện đặt nhầm ghim hơn nhìn bản đồ */}
        {loc && (
          <div className="alert alert-info"><MapPin size={14} /> Đội cứu hộ sẽ tìm tới: <b>{place || `tọa độ ${loc.lat}, ${loc.lng}`}</b>
            {!picked.adminText && <span className="muted"> (chưa xác định được phường/xã)</span>}</div>
        )}
        {err && <div className="alert alert-danger">{err}</div>}
        <button className="btn btn-danger btn-lg" disabled={sending} onClick={() => send(false)}><Siren size={18} /> Xác nhận gửi SOS</button>
      </div>
      {askLandmark && (
        /* Nhắc một lần, không chặn: nhiều người gặp nạn không có số nhà hoặc không biết địa chỉ nơi đang mắc kẹt */
        <Modal title="Bạn chưa ghi số nhà hoặc mốc" onClose={() => setAskLandmark(false)} footer={<>
          <button className="btn btn-ghost" onClick={() => { setAskLandmark(false); send(false, true); }}>Gửi luôn</button>
          <button className="btn btn-primary" onClick={() => { setAskLandmark(false); setTimeout(focusLandmark, 0); }}>Ghi thêm</button>
        </>}>
          <p>Ghi thêm số nhà, ngõ hoặc mốc dễ nhận biết (VD: “cạnh chùa”, “nhà mái tôn xanh”) sẽ giúp đội cứu hộ tìm đúng nhà nhanh hơn.</p>
          <p className="muted small">Nếu không biết, bạn có thể gửi luôn: đội cứu hộ vẫn có vị trí ghim, phường/xã và số điện thoại của bạn.</p>
        </Modal>
      )}
      {nearby && (
        /* "Không gửi nữa": chỉ đóng hộp thoại, giữ nguyên nội dung đã nhập để người dân còn gửi lại nếu đổi ý */
        <Modal title="Có yêu cầu SOS gần vị trí của bạn" onClose={() => setNearby(null)} footer={<>
          <button className="btn btn-ghost" onClick={() => setNearby(null)}>Không gửi nữa</button>
          <button className="btn btn-danger" onClick={() => send(true)}>Vẫn gửi yêu cầu mới</button>
        </>}>
          <p>Trong bán kính {DUP_RADIUS_M} m, {DUP_WINDOW_MIN} phút gần đây có ca SOS đang hoạt động. Có thể đây là cùng một sự cố, nhưng hệ thống không tự gộp để tránh bỏ sót người cần hỗ trợ.</p>
          <div className="stack-sm">
            {nearby.map((n) => {
              const open = openDup === n.id;
              return (
                <div key={n.id} className={`dup-case sev-${n.severity}`}>
                  <div className="row between gap8 wrap">
                    <div className="row gap8 wrap"><b>{n.code}</b><span className="small">cách khoảng {fmtDist(n.dist)}</span><span className="muted small">{ago(n.createdAt)}</span></div>
                    <button type="button" className="btn btn-ghost btn-sm" aria-expanded={open} onClick={() => setOpenDup(open ? null : n.id)}>{open ? <ChevronUp size={15} /> : <ChevronDown size={15} />} {open ? 'Thu gọn' : 'Xem chi tiết'}</button>
                  </div>
                  {open && (
                    <div className="stack-sm">
                      <MapView height={200} center={[n.lat, n.lng]} bounds={pairBounds({ lat: Number(loc.lat), lng: Number(loc.lng) }, n)} markers={[
                        { id: 'me', lat: Number(loc.lat), lng: Number(loc.lng), kind: 'me', color: '#1d4ed8', label: 'Vị trí của bạn' },
                        { id: n.id, lat: n.lat, lng: n.lng, kind: 'sos', color: SEVERITY[n.severity].color, label: n.code },
                      ]} />
                      <div className="kv">
                        <span>Mức độ</span><b><Sev level={n.severity} /></b>
                        <span>Trạng thái</span><b><Tag tone={SOS_STATUS[n.status].tone}>{SOS_STATUS[n.status].label}</Tag></b>
                        <span>Gửi lúc</span><b>{fmtDateTime(n.createdAt)} ({ago(n.createdAt)})</b>
                        <span>Tổ chức phụ trách</span><b>{n.org ? n.org.name : 'Chưa có'}</b>
                      </div>
                      <span className="muted small">Vị trí ca kia là vị trí gần đúng (khoảng 100 m). Mô tả và địa chỉ chỉ người gửi, tổ chức cứu hộ và Admin xem được.</span>
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </Modal>
      )}
    </div>
  );
}
