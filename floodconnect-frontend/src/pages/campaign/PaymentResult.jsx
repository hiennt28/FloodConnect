import { useEffect } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { CheckCircle2, XCircle } from 'lucide-react';
import { useApp } from '@/hooks';

/* ================= PAYMENT RESULT (VNPay chuyển trình duyệt về đây) ================= */
export function PaymentResult() {
  const [q] = useSearchParams();
  const { load } = useApp();
  const status = q.get('status');
  const tx = q.get('transactionCode');
  const campaignId = q.get('campaignId');
  useEffect(() => { load.refresh(); }, []); // eslint-disable-line react-hooks/exhaustive-deps
  const ok = status === 'SUCCESS';
  const invalid = status === 'INVALID';
  const expired = status === 'EXPIRED'; // quay lại từ cổng sau khi giao dịch đã quá hạn
  const title = ok ? 'Cảm ơn bạn đã ủng hộ!' : invalid ? 'Không xác minh được giao dịch' : expired ? 'Giao dịch đã hết hạn' : 'Thanh toán thất bại';
  const desc = ok ? 'Khoản ủng hộ đã được ghi nhận vào chiến dịch.'
    : invalid ? 'Kết quả trả về không hợp lệ (sai chữ ký hoặc không tìm thấy giao dịch). Số tiền chưa được cộng vào chiến dịch.'
    : expired ? 'Giao dịch quá thời hạn thanh toán nên đã được đóng. Nếu bạn vẫn muốn ủng hộ, hãy tạo giao dịch mới.'
    : 'Giao dịch bị hủy hoặc không thành công. Số tiền chưa được cộng vào chiến dịch.';
  return (
    <div className="narrow card pad done-box">
      {ok ? <CheckCircle2 size={48} className="ok-ic" /> : <XCircle size={48} className="err-ic" />}
      <h2>{title}</h2>
      <p className="muted">{desc}</p>
      {tx && <p className="muted small">Mã giao dịch: <b>{tx}</b></p>}
      <div className="row gap8">
        <Link className="btn btn-ghost" to="/my-donations">Lịch sử đóng góp</Link>
        <Link className="btn btn-primary" to={campaignId ? `/campaigns/${campaignId}` : '/campaigns'}>Về chiến dịch</Link>
      </div>
    </div>
  );
}
