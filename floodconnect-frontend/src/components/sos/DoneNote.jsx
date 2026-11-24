import { CheckCircle2 } from 'lucide-react';
import { COMPLETED_BY, fmtDateTime } from '@/utils';

/* Ca Hoàn thành: ai hoàn thành (tổ chức / người dân báo an toàn / Admin xác nhận), lúc nào, kèm ghi chú hoặc lý do nếu có.
   self = người đang xem là người gửi ca (đổi "Người dân" thành "Bạn") */
export function DoneNote({ s, self }) {
  if (s.status !== 'DONE') return null;
  const by = s.doneBy || 'ORG';
  const who = by === 'CITIZEN' && self ? 'Bạn đã báo an toàn'
    : by === 'ORG' && s.org?.name ? `${s.org.name} đã hoàn thành cứu hộ`
      : COMPLETED_BY[by];
  return (
    <div className="alert alert-success">
      <CheckCircle2 size={15} /> {who}{s.doneAt ? ` lúc ${fmtDateTime(s.doneAt)}` : ''}.{s.doneNote && ` ${by === 'ADMIN' ? 'Lý do' : 'Ghi chú'}: ${s.doneNote.replace(/[.\s]+$/, '')}.`}
    </div>
  );
}
