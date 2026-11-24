import { useEffect, useState } from 'react';
import { Crosshair, ImagePlus, MapPin, X } from 'lucide-react';
import { useApp } from '@/hooks';
import { Avatar, Photo } from '@/components/common';
import AddressPicker from '@/components/common/AddressPicker';
import { previewOf, provinceOfWard, validateFiles } from '@/utils';

/*
 * Vị trí bài đăng nhập bằng địa chỉ, không bắt nhập vĩ độ, kinh độ (người dân thường không biết):
 *   { provinceCode, wardCode, detail: thôn/xóm, số nhà, đường, gps: { lat, lng } | null }
 * Backend tự tính tọa độ: GPS nếu có và nằm đúng xã đã chọn, không thì điểm đại diện của xã.
 */
const EMPTY_LOC = { provinceCode: '', wardCode: '', detail: '', gps: null };
const round5 = (x) => +Number(x).toFixed(5);

/* Sửa bài: điền lại địa chỉ đã lưu. Bài cũ (trước khi có địa chỉ) chỉ có tọa độ + tên địa điểm: giữ tọa độ làm GPS, tên vào ô chi tiết */
function initialLoc(l) {
  if (!l) return null;
  return { provinceCode: provinceOfWard(l.wardCode), wardCode: l.wardCode || '', detail: (l.wardCode ? l.detail : l.label) || '', gps: { lat: l.lat, lng: l.lng } };
}

/* ---------- Soạn / sửa bài ---------- */
export function PostComposer({ initial, onDone, onCancel }) {
  const { me, social, sos, toast } = useApp();
  const [content, setContent] = useState(initial?.content || '');
  const [images, setImages] = useState(initial?.images || []);
  const [loc, setLoc] = useState(() => initialLoc(initial?.location));
  const [locating, setLocating] = useState(false);
  const [err, setErr] = useState('');
  const [busy, setBusy] = useState(false);

  /* Tra phường/xã chứa tọa độ để tự điền Tỉnh, Phường/Xã. Tra không được thì người dùng tự chọn */
  const fillWardFrom = async (gps) => {
    const r = await sos.locateWard(gps.lat, gps.lng);
    const w = r?.ward;
    setLoc((x) => (x && x.gps === gps && w ? { ...x, provinceCode: w.provinceCode, wardCode: w.code } : x));
    if (!w) setErr('Không xác định được phường/xã từ vị trí GPS, vui lòng tự chọn Tỉnh/Thành phố và Phường/Xã.');
  };
  // Bài cũ chỉ có tọa độ: tự điền xã từ tọa độ đã lưu
  useEffect(() => { if (loc?.gps && !loc.wardCode) fillWardFrom(loc.gps); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const addFiles = async (files) => {
    const list = [...files];
    const e = validateFiles(list, images.length);
    if (e) { setErr(e); return; }
    setBusy(true);
    setImages((x) => [...x, ...list.map(previewOf)]);
    setErr('');
    setBusy(false);
  };

  const useGps = () => {
    if (!navigator.geolocation) { setErr('Trình duyệt không hỗ trợ định vị, vui lòng bấm "Nhập vị trí" để chọn địa chỉ.'); return; }
    setLocating(true); setErr('');
    navigator.geolocation.getCurrentPosition(
      (p) => {
        const gps = { lat: round5(p.coords.latitude), lng: round5(p.coords.longitude) };
        setLocating(false);
        setLoc((x) => ({ ...(x || EMPTY_LOC), gps }));
        fillWardFrom(gps);
      },
      () => {
        setLocating(false);
        setLoc((x) => x || { ...EMPTY_LOC });
        setErr('Không lấy được vị trí GPS, vui lòng chọn Tỉnh/Thành phố và Phường/Xã.');
      },
      { timeout: 8000 },
    );
  };

  /* Tự đổi xã thì vị trí GPS (nếu có) không còn khớp địa chỉ nữa: bỏ GPS, backend dùng điểm đại diện của xã mới */
  const pickWard = (v) => setLoc((x) => ({ ...x, ...v, gps: null }));

  const submit = async () => {
    const payload = { content, images, location: loc };
    setBusy(true);
    const r = initial ? await social.editPost(initial.id, payload) : await social.createPost(payload);
    setBusy(false);
    if (r) { setErr(r); return; }
    toast(initial ? 'Đã lưu thay đổi bài viết' : 'Đã đăng bài');
    setContent(''); setImages([]); setLoc(null); setErr('');
    onDone?.();
  };

  return (
    <div className={initial ? '' : 'card composer'}>
      <div className="row gap12 top">
        {!initial && <Avatar user={me} />}
        <div className="grow">
          <textarea className="input composer-input" rows={initial ? 5 : 3} maxLength={2100} placeholder="Bạn đang muốn chia sẻ điều gì với bà con xóm lũ?" value={content} onChange={(e) => setContent(e.target.value)} />
          <div className="row between small muted"><span>{content.trim().length}/2.000</span>{images.length > 0 && <span>{images.length}/5 tệp</span>}</div>
          {images.length > 0 && (
            <div className="thumbs">
              {images.map((im, i) => (
                <div key={i} className="thumb"><Photo img={im} /><button className="thumb-x" aria-label="Bỏ ảnh" onClick={() => setImages(images.filter((_, j) => j !== i))}><X size={14} /></button></div>
              ))}
            </div>
          )}
          {loc && (
            <div className="loc-row loc-form">
              <div className="row between">
                <span className="row gap6"><MapPin size={16} /> <b>Vị trí</b>{loc.gps && <span className="small">· đã lấy theo GPS</span>}</span>
                <button className="icon-btn" aria-label="Bỏ vị trí" onClick={() => { setLoc(null); setErr(''); }}><X size={14} /></button>
              </div>
              <AddressPicker provinceCode={loc.provinceCode} wardCode={loc.wardCode} onChange={pickWard} />
              <input className="input" placeholder="Thôn/xóm, số nhà, đường (không bắt buộc)" maxLength={100} value={loc.detail}
                onChange={(e) => setLoc({ ...loc, detail: e.target.value })} />
            </div>
          )}
          {err && <div className="field-error">{err}</div>}
          <div className="composer-bar">
            <label className="btn btn-ghost btn-sm"><ImagePlus size={16} /> Ảnh<input hidden type="file" multiple accept="image/jpeg,image/png,image/webp" onChange={(e) => { addFiles(e.target.files); e.target.value = ''; }} /></label>
            <button className="btn btn-ghost btn-sm" disabled={locating} onClick={useGps}><Crosshair size={16} /> {locating ? 'Đang lấy vị trí…' : 'Vị trí GPS'}</button>
            {!loc && <button className="btn btn-ghost btn-sm" onClick={() => setLoc({ ...EMPTY_LOC })}><MapPin size={16} /> Nhập vị trí</button>}
            <span className="grow" />
            {onCancel && <button className="btn btn-ghost btn-sm" onClick={onCancel}>Hủy</button>}
            <button className="btn btn-primary btn-sm" disabled={busy} onClick={submit}>{initial ? 'Lưu' : 'Đăng bài'}</button>
          </div>
        </div>
      </div>
    </div>
  );
}
