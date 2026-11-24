import { FileText, UploadCloud } from 'lucide-react';
import { Field } from '@/components/common';
import { DOC_SLOTS, docSlotHint, docSlotLabel, docSlotsOf, requiredDocSlots } from '@/utils';

const fileKey = (f) => `${f.name}|${f.size}|${f.lastModified}`;

/* Hồ sơ xác minh theo từng ô của loại tổ chức. value: { MAIN: File[], ID_FRONT: File[], ID_BACK: File[], EVIDENCE: File[] }.
   keepOld: đang gửi lại hồ sơ, ô để trống thì giữ tệp đã nộp nên không đánh dấu bắt buộc. Lỗi theo khóa DOC_SLOTS[ô].part. */
export function DocSlotsInput({ type, value, onChange, errors = {}, keepOld = false }) {
  const required = requiredDocSlots(type);
  const pick = (slot, list) => {
    const picked = [...list].filter((f) => /\.(pdf|jpe?g|png)$/i.test(f.name));
    if (!DOC_SLOTS[slot].multiple) { onChange({ ...value, [slot]: picked.slice(0, 1) }); return; }
    const seen = new Set(value[slot].map(fileKey));
    onChange({ ...value, [slot]: [...value[slot], ...picked.filter((f) => !seen.has(fileKey(f)))] });
  };

  return (
    <>
      {docSlotsOf(type).map((slot) => {
        const s = DOC_SLOTS[slot];
        const files = value[slot] || [];
        const hint = [docSlotHint(type, slot), keepOld && 'Để trống thì giữ tệp đã nộp; chọn tệp mới thì thay tệp cũ của mục này.'].filter(Boolean).join(' ');
        return (
          <Field key={slot} label={docSlotLabel(type, slot)} required={!keepOld && required.includes(slot)} error={errors[s.part]} hint={hint}>
            <label className="dropzone">
              <UploadCloud size={24} />
              <b>{s.multiple ? 'Bấm để chọn tệp' : 'Bấm để chọn 1 tệp'}</b>
              <span className="small">PDF, JPG, PNG • tối đa 5 MB mỗi tệp</span>
              <input className="sr-only" type="file" multiple={s.multiple} accept=".pdf,.jpg,.jpeg,.png"
                onChange={(e) => { pick(slot, e.target.files); e.target.value = ''; /* cho phép chọn lại cùng tệp */ }} />
            </label>
            {files.length > 0 && <div className="file-list">{files.map((f, i) => (
              <span key={`${f.name}-${i}`} className="file-chip"><FileText size={14} /> {f.name}
                <button type="button" aria-label="Xóa tệp" style={{ marginLeft: 6, border: 0, background: 'none', cursor: 'pointer' }}
                  onClick={() => onChange({ ...value, [slot]: files.filter((_, j) => j !== i) })}>✕</button>
              </span>))}</div>}
          </Field>
        );
      })}
    </>
  );
}
