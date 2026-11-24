import { useState } from 'react';
import { Field, Modal } from '@/components/common';

/* Admin đã liên hệ xác minh và kết luận ca không phải yêu cầu cứu hộ: đóng ca, lý do được gửi cho người gửi */
export function MarkInvalidModal({ s, onClose, onSave }) {
  const [reason, setReason] = useState('');
  const [error, setError] = useState('');
  const save = async () => {
    const result = await onSave(reason);
    if (result) setError(result);
  };

  return (
    <Modal title={`Đánh dấu ca ${s.code} không hợp lệ`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-danger" onClick={save}>Xác nhận không hợp lệ</button>
    </>}>
      <div className="stack-sm">
        <Field label="Lý do" error={error} required>
          <textarea className="input" rows={3} maxLength={500} value={reason} onChange={(e) => { setReason(e.target.value); setError(''); }} placeholder="VD: Đã gọi xác minh, người gửi bấm nhầm" />
        </Field>
        <span className="muted small">Ca sẽ kết thúc với trạng thái “Không hợp lệ” và không được gửi cho tổ chức. Người gửi nhận thông báo kèm lý do và có thể gửi yêu cầu mới nếu vẫn cần cứu hộ.</span>
      </div>
    </Modal>
  );
}
