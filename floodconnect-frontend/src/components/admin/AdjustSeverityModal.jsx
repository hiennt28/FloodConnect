import { useState } from 'react';
import { Field, Modal } from '@/components/common';
import { SEVERITY } from '@/utils';

/* Nhãn KHONG chỉ do AI gán (chờ xác minh), Admin không chọn được; ca không phải yêu cầu cứu hộ thì dùng "Đánh dấu không hợp lệ" */
const LEVELS = Object.entries(SEVERITY).filter(([key]) => key !== 'KHONG');
export const VERIFY_NOTE_MAX = 1000;

/* s.verify = xác minh ca AI xếp KHONG: bắt buộc ghi chú xác minh (thông tin hỏi được khi liên hệ người gửi), vì mô tả gốc thường
   quá ít mà tổ chức chỉ dựa vào đó. Điều chỉnh mức thông thường không có ô ghi chú. */
export function AdjustSeverityModal({ s, onClose, onSave }) {
  const [nextSeverity, setNextSeverity] = useState(s.severity === 'KHONG' ? 'TRUNG_BINH' : s.severity);
  const [note, setNote] = useState('');
  const [error, setError] = useState('');
  const [noteError, setNoteError] = useState('');
  const save = async () => {
    if (s.verify && !note.trim()) { setNoteError('Vui lòng ghi lại thông tin đã xác minh với người gửi.'); return; }
    const result = await (s.verify ? onSave(nextSeverity, note.trim()) : onSave(nextSeverity));
    if (result) setError(result);
  };

  return (
    <Modal title={`${s.verify ? 'Xác minh' : 'Điều chỉnh mức độ'} ca ${s.code}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-primary" onClick={save}>{s.verify ? 'Xác minh & chuyển tổ chức' : 'Xác nhận thay đổi'}</button>
    </>}>
      <div className="stack-sm">
        <span className="muted small">Mức độ AI dự đoán / hiện tại: <b>{SEVERITY[s.severity].label}</b></span>
        <Field label="Mức độ nguy hiểm mới" error={error} required>
          <select className="input" value={nextSeverity} onChange={(e) => { setNextSeverity(e.target.value); setError(''); }}>
            {LEVELS.map(([key, value]) => <option key={key} value={key}>{key} – {value.label}</option>)}
          </select>
        </Field>
        {s.verify && (
          <Field label="Ghi chú xác minh" error={noteError} required
            hint={`Tổ chức và người gửi sẽ thấy ghi chú này bên cạnh mô tả gốc. ${note.length}/${VERIFY_NOTE_MAX}`}>
            <textarea className="input" rows={4} maxLength={VERIFY_NOTE_MAX} value={note}
              onChange={(e) => { setNote(e.target.value); setNoteError(''); setError(''); }}
              placeholder="VD: Đã gọi người gửi lúc 21:40. Nhà ngập khoảng 1 m, có 2 người già và 1 trẻ em, cần xuồng đưa ra ngoài." />
          </Field>
        )}
        <span className="muted small">Chỉ ca đang Chờ xử lý mới được điều chỉnh. Ca được gửi cho tổ chức phù hợp theo mức mới. Nếu ca không phải yêu cầu cứu hộ, hãy dùng “Đánh dấu không hợp lệ”.</span>
      </div>
    </Modal>
  );
}
