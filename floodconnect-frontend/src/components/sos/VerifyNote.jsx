import { PhoneCall } from 'lucide-react';
import { fmtDateTime } from '@/utils';

/* Ghi chú xác minh: ca AI xếp nhãn "Không", Admin liên hệ người gửi rồi chọn mức thật và ghi lại thông tin hỏi được.
   Hiện cạnh mô tả gốc (mô tả của người dân không bị sửa). self = người đang xem là người gửi ca. */
export function VerifyNote({ s, self }) {
  if (!s.verifyNote) return null;
  const when = s.verifiedAt ? ` lúc ${fmtDateTime(s.verifiedAt)}` : '';
  return (
    <div className="alert alert-info verify-note">
      <b><PhoneCall size={15} /> {self ? `Admin đã liên hệ xác minh với bạn${when}` : `Admin đã liên hệ người gửi để xác minh${when}`}</b>
      <span className="verify-note-text">{s.verifyNote}</span>
    </div>
  );
}
