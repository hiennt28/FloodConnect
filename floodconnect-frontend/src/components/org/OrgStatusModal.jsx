import { useState } from 'react';
import { Modal, Tag } from '@/components/common';
import { ORG_STATUS } from '@/utils';

/* Nút xác nhận và màu nhãn theo trạng thái đích: Sẵn sàng xanh lá, Đang bận cam, Không hoạt động đỏ (làm trả ca) */
const OK_CLASS = { READY: 'btn-success', BUSY: 'btn-warning', INACTIVE: 'btn-danger' };

/* Hệ quả khi chuyển sang trạng thái đích, khớp backend: OrgService.setOperatingStatus (Không hoạt động trả ca Đã phân công
   về Chờ xử lý qua RescueService.releaseAssigned), SosAdminService.assign / thông báo ca mới chỉ cho tổ chức Sẵn sàng,
   RescueScheduler tự chuyển ca Đã phân công quá hạn cho tổ chức khác */
function effects(target, assigned, handling) {
  if (target === 'READY') return {
    lead: 'Tổ chức sẽ bắt đầu nhận việc mới:',
    items: [
      <>Hiện danh sách <b>ca đề xuất</b> trong khu vực để bạn chủ động tiếp nhận.</>,
      <><b>Admin có thể phân công</b> ca cho tổ chức (cần xác nhận trong 5 phút).</>,
      <>Nhận <b>thông báo</b> khi có ca SOS mới trong khu vực.</>,
    ],
  };
  if (target === 'BUSY') {
    const items = [<>Không hiện ca đề xuất, không nhận thông báo ca mới.</>, <><b>Admin không phân công thêm</b> ca cho tổ chức.</>];
    if (assigned) items.push(<><b>{assigned} ca đang chờ bạn xác nhận</b> vẫn giữ nguyên: hãy xác nhận hoặc từ chối sớm, quá hạn ca sẽ được chuyển cho tổ chức khác.</>);
    if (handling) items.push(<><b>{handling} ca đang cứu hộ</b> vẫn do bạn phụ trách cho tới khi hoàn thành.</>);
    return { lead: 'Tổ chức sẽ tạm ngừng nhận ca mới:', items };
  }
  const items = [<>Không hiện ca đề xuất, không nhận thông báo ca mới, Admin không phân công ca cho tổ chức.</>];
  if (assigned) items.push(<><b>{assigned} ca đang chờ bạn xác nhận sẽ được trả lại</b> cho Admin để điều phối cho tổ chức khác.</>);
  if (handling) items.push(<><b>{handling} ca đang cứu hộ bạn vẫn phải tiếp tục</b> và bấm Hoàn thành khi xong.</>);
  return { lead: 'Tổ chức sẽ ngừng nhận ca:', items, warn: assigned > 0 };
}

/* Xác nhận đổi trạng thái hoạt động của tổ chức, ghi rõ hệ quả kèm số ca thật của tổ chức.
   onConfirm() trả null nếu thành công, thông báo lỗi nếu thất bại */
export function OrgStatusModal({ target, assigned = 0, handling = 0, onClose, onConfirm }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const e = effects(target, assigned, handling);
  const label = ORG_STATUS[target].label;
  const ok = async () => {
    setBusy(true);
    const err = await onConfirm();
    setBusy(false);
    if (err) setError(err);
  };

  return (
    <Modal title={`Chuyển sang ${label}?`} onClose={onClose} footer={<>
      <button className="btn btn-ghost" onClick={onClose}>Hủy</button>
      <button className={`btn ${OK_CLASS[target]}`} onClick={ok} disabled={busy}>Chuyển sang {label}</button>
    </>}>
      <div className="stack-sm">
        <p className="row gap6 wrap"><Tag tone={ORG_STATUS[target].tone}>{label}</Tag>{e.lead}</p>
        <ul className="status-effects">{e.items.map((item, k) => <li key={k}>{item}</li>)}</ul>
        {e.warn && <div className="alert alert-danger small">Ca bị trả lại sẽ được báo cho người dân và các tổ chức gần đó.</div>}
        {error && <div className="alert alert-danger small">{error}</div>}
      </div>
    </Modal>
  );
}
