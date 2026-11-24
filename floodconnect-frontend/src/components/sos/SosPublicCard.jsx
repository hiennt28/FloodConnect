import { Clock, MapPin } from 'lucide-react';
import { useApp } from '@/hooks';
import { Sev, Tag } from '@/components/common';
import { ago, fmtDist, haversine, SOS_STATUS } from '@/utils';

/* Thẻ rút gọn trong tab "Tất cả ca SOS": chỉ thông tin công khai (mã, mức độ, trạng thái, khoảng cách, thời gian, tổ chức).
   Ca của người khác: backend ẩn mô tả/địa chỉ và chỉ trả tọa độ làm tròn ~100 m, nên khoảng cách là gần đúng.
   Ca của chính mình (s.mine): hiện địa chỉ và nút mở thẻ đầy đủ ở tab "SOS của tôi". Viền trái theo mức độ (lớp sev-*) */
export function SosPublicCard({ s, onOpenMine }) {
  const { me } = useApp();
  return (
    <div className={`card sos-pub sev-${s.severity}`}>
      <div className="row between wrap gap8">
        <div className="row gap8 wrap"><b>{s.code}</b><Sev level={s.severity} />{s.mine && <Tag tone="blue">Ca của bạn</Tag>}</div>
        <Tag tone={SOS_STATUS[s.status].tone} dot>{SOS_STATUS[s.status].label}</Tag>
      </div>
      <div className="row gap12 wrap small muted">
        <span><MapPin size={13} /> {s.mine ? s.address : me?.lat != null ? `Cách bạn khoảng ${fmtDist(haversine(me, s))}` : 'Chưa có vị trí của bạn để tính khoảng cách'}</span>
        <span><Clock size={13} /> Gửi {ago(s.createdAt).toLowerCase()}</span>
      </div>
      {s.org && <span className="small">Tổ chức phụ trách: <b>{s.org.name}</b></span>}
      {s.mine && onOpenMine && <button type="button" className="btn btn-ghost btn-sm sos-pub-open" onClick={() => onOpenMine(s.id)}>Xem chi tiết trong “SOS của tôi”</button>}
    </div>
  );
}
