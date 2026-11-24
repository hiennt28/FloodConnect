import { useState } from 'react';
import { Field, Modal } from '@/components/common';

export function EditSosModal({ s, onClose, onSave }) {
  const [desc, setDesc] = useState(s.desc);
  const [error, setError] = useState('');
  const [saving, setSaving] = useState(false);

  const save = async () => {
    if (saving) return;
    setSaving(true);
    const result = await onSave({ desc });
    setSaving(false);
    if (result) setError(result);
  };

  return (
    <Modal title={`Sửa yêu cầu SOS ${s.code}`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-primary" disabled={saving} onClick={save}>Lưu thay đổi</button>
    </>}>
      <div className="stack">
        <div className="alert alert-info">Chỉ được sửa khi yêu cầu đang ở trạng thái Chờ xử lý. Khi mô tả đổi, AI đánh giá lại như lúc gửi mới: mức độ có thể tăng hoặc giảm theo nội dung mới. Vị trí không thể đổi: nếu sai vị trí, hãy hủy và gửi lại.</div>
        <Field label="Mô tả tình trạng" required><textarea className="input" rows={4} value={desc} onChange={(e) => { setDesc(e.target.value); setError(''); }} /></Field>
        {error && <div className="alert alert-danger">{error}</div>}
      </div>
    </Modal>
  );
}
