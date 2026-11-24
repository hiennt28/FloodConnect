import { useState } from 'react';
import { useApp } from '@/hooks';
import { Field, Modal } from '@/components/common';
import { toInputDate } from '@/utils';

/* Gia hạn chiến dịch đã kết thúc vì hết thời gian: chọn ngày kết thúc mới, chiến dịch tiếp tục nhận ủng hộ.
   Dùng ở bảng "Chiến dịch của chúng tôi" và trang chi tiết chiến dịch (mở từ thông báo "Chiến dịch hết hạn"). */
export function ExtendCampaignModal({ c, onClose }) {
  const { campaigns, toast } = useApp();
  const [newEnd, setNewEnd] = useState('');
  const [err, setErr] = useState('');
  const save = async () => {
    if (!newEnd) { setErr('Vui lòng chọn thời gian kết thúc mới.'); return; }
    const r = await campaigns.extend(c.id, new Date(`${newEnd}T23:59:59`).getTime());
    if (r) setErr(r);
    else { toast('Đã gia hạn, chiến dịch tiếp tục nhận ủng hộ'); onClose(); }
  };
  return (
    <Modal title="Gia hạn chiến dịch" onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className="btn btn-primary" onClick={save}>Gia hạn</button>
    </>}>
      <Field label="Thời gian kết thúc mới" error={err}><input className="input" type="date" value={newEnd} min={toInputDate(Date.now() + 86400000)} onChange={(e) => { setNewEnd(e.target.value); setErr(''); }} /></Field>
    </Modal>
  );
}
