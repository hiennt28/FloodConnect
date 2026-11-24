import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ArrowLeft, CheckCircle2, ShieldCheck, XCircle } from 'lucide-react';
import { useApp } from '@/hooks';
import { Empty, Modal, Photo, Progress } from '@/components/common';
import { campaignStatus, coverOf, money } from '@/utils';

const PRESETS = [50000, 100000, 200000, 500000, 1000000];

/* ================= DONATE ================= */
export function Donate() {
  const { id } = useParams();
  const { state, me, campaigns, toast, load } = useApp();
  const nav = useNavigate();
  useEffect(() => { load.campaign(id); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  const c = state.campaigns.find((x) => x.id === id);
  const [f, setF] = useState({ amount: '', message: '', anonymous: false });
  const [err, setErr] = useState('');
  const [pending, setPending] = useState(null);
  const [result, setResult] = useState(null);
  if (!c) return <Empty title="Chiến dịch không tồn tại" />;
  const canDonate = campaignStatus(c) === 'ACTIVE' && c.raised < c.target;

  const confirm = async () => {
    const r = await campaigns.startDonation(c.id, f);
    if (r.error) { setErr(r.error); return; }
    setErr('');
    if (!r.mock && r.paymentUrl) { window.location.href = r.paymentUrl; return; } // sandbox VNPay: sang trang thanh toán của VNPay
    setPending(r);                                                                  // chế độ giả lập: hiện modal
  };
  const finish = async (ok) => {
    const tx = pending;
    setPending(null);
    const e = await campaigns.completeDonation(tx.id, ok, tx.amount);
    await load.campaign(c.id);
    if (e) { setErr(e); toast(e, 'err'); return; }
    setResult(ok ? 'SUCCESS' : 'FAILED');
    toast(ok ? 'Thanh toán thành công' : 'Giao dịch đã bị hủy', ok ? 'ok' : 'err');
  };

  if (result) {
    return (
      <div className="narrow card pad done-box">
        {result === 'SUCCESS' ? <CheckCircle2 size={48} className="ok-ic" /> : <XCircle size={48} className="err-ic" />}
        <h2>{result === 'SUCCESS' ? 'Cảm ơn bạn đã ủng hộ!' : 'Thanh toán thất bại'}</h2>
        <p className="muted">{result === 'SUCCESS' ? `Khoản ủng hộ ${money(Number(f.amount))} đã được ghi nhận vào chiến dịch.` : 'Giao dịch bị hủy hoặc không thành công. Số tiền chưa được cộng vào chiến dịch.'}</p>
        <div className="row gap8"><Link className="btn btn-ghost" to="/my-donations">Lịch sử đóng góp</Link><Link className="btn btn-primary" to={`/campaigns/${c.id}`}>Về chiến dịch</Link></div>
      </div>
    );
  }

  const amount = Number(f.amount) || 0;
  /* Tiến độ xem trước "sau khi bạn ủng hộ" (chỉ để hiển thị; tiền chỉ được cộng thật khi VNPay xác nhận) */
  const after = Math.min(c.raised + amount, c.target);
  const pct = c.target ? Math.round((after / c.target) * 100) : 0;
  const ctaLabel = amount ? `Ủng hộ ${money(amount)} qua VNPay` : 'Nhập số tiền để tiếp tục';

  return (
    <div className="stack">
      <button className="link row gap6" onClick={() => nav(`/campaigns/${c.id}`)}><ArrowLeft size={16} /> Quay lại chiến dịch</button>
      {!canDonate ? <div className="alert alert-danger">Chiến dịch hiện không nhận ủng hộ.</div> : (
        <>
          <div className="donate-layout">
            <div className="card pad stack">
              <div className="donate-sec"><h3>Số tiền ủng hộ</h3></div>
              <label className="amount-input">
                <input inputMode="numeric" autoComplete="off" value={amount ? amount.toLocaleString('vi-VN') : ''}
                  onChange={(e) => setF({ ...f, amount: e.target.value.replace(/\D/g, '').slice(0, 11) })} placeholder="Nhập số tiền" aria-label="Số tiền ủng hộ" />
                <b>VNĐ</b>
              </label>
              <div className="amount-presets" role="group" aria-label="Chọn nhanh số tiền">
                {PRESETS.map((v) => (
                  <button key={v} type="button" className={amount === v ? 'on' : ''} aria-pressed={amount === v} onClick={() => setF({ ...f, amount: String(v) })}>{v.toLocaleString('vi-VN')}</button>
                ))}
              </div>
              <hr />
              <div className="donate-sec"><h3>Lời nhắn <small>(không bắt buộc)</small></h3><span className="muted small">{f.message.length}/150</span></div>
              <textarea className="input" maxLength={150} rows={3} value={f.message} placeholder="Gửi vài lời động viên tới bà con vùng lũ…" aria-label="Lời nhắn"
                onChange={(e) => setF({ ...f, message: e.target.value })} />
              <label className="donate-check">
                <input type="checkbox" checked={f.anonymous} onChange={(e) => setF({ ...f, anonymous: e.target.checked })} />
                <span><b>Ủng hộ ẩn danh</b><span className="muted small">Trên bảng sao kê công khai, tên bạn hiện là “Người gửi ẩn danh”.</span></span>
              </label>
            </div>

            {/* Tiền đi qua VNPay nên không hiện số tài khoản của tổ chức (người ủng hộ không tự chuyển khoản), chỉ hiện người nhận */}
            <aside className="card pad stack donate-summary" aria-label="Tóm tắt khoản ủng hộ">
              <div className="donate-camp">
                <Photo img={coverOf(c)} />
                <div className="grow" style={{ minWidth: 0 }}><b>{c.name}</b>{c.orgName && <span className="muted small">bởi {c.orgName}</span>}</div>
              </div>
              <div className="stack-sm">
                <Progress value={after} max={c.target} />
                <div className="donate-prog"><span><b>{money(after)}</b> / {money(c.target)}</span><span>{pct}%{amount ? ' sau khi bạn ủng hộ' : ''}</span></div>
              </div>
              <div className="donate-kv">
                <span>Người ủng hộ</span><b>{f.anonymous ? 'Người gửi ẩn danh' : me.name}</b>
                {c.orgName && <><span>Người nhận</span><b>{c.orgName}</b></>}
              </div>
              <div className="donate-total"><span>Số tiền</span><b>{money(amount)}</b></div>
              {/* Chỉ VNPay: trên trang VNPay người ủng hộ tự chọn ngân hàng, quét mã QR bằng app ngân hàng hoặc nhập thẻ */}
              <div className="donate-paywith">
                <span className="vnpay-badge" aria-hidden="true"><span className="r">VN</span><span className="b">PAY</span></span>
                <span><b>Thanh toán qua VNPay</b><br />Quét QR bằng app ngân hàng, thẻ ATM nội địa hoặc thẻ quốc tế</span>
              </div>
              {err && <div className="alert alert-danger">{err}</div>}
              <button className="btn btn-primary btn-lg donate-cta" disabled={!amount} onClick={confirm}>{ctaLabel}</button>
              <div className="donate-note"><ShieldCheck size={15} /><span>Số tiền chỉ được cộng vào chiến dịch sau khi VNPay xác nhận thanh toán thành công.</span></div>
            </aside>
          </div>

          {/* Màn hình hẹp: tóm tắt nằm dưới form nên nút thanh toán dính ở đáy màn hình */}
          <div className="donate-paybar">
            <div><span className="muted small">Số tiền</span><br /><b>{money(amount)}</b></div>
            <button className="btn btn-primary" disabled={!amount} onClick={confirm}>{amount ? 'Ủng hộ qua VNPay' : 'Nhập số tiền'}</button>
          </div>
        </>
      )}
      {pending && (
        <Modal title="Cổng thanh toán VNPay (mô phỏng)" onClose={() => finish(false)} footer={<>
          <button className="btn btn-ghost" onClick={() => finish(false)}>Hủy giao dịch</button>
          <button className="btn btn-primary" onClick={() => finish(true)}>Thanh toán thành công</button>
        </>}>
          <div className="gateway">
            <div className="gw-logo VNPAY">VNPAY</div>
            <div className="qr" aria-hidden="true">{Array.from({ length: 49 }, (_, i) => <i key={i} className={(i * 7 + i * i) % 3 === 0 || [0, 1, 7, 8, 5, 6, 12, 13, 35, 36, 42, 43].includes(i) ? 'on' : ''} />)}</div>
            <b className="big-money">{money(Number(f.amount))}</b>
            <span className="muted small">Đơn hàng đang ở trạng thái PENDING. Chọn kết quả để mô phỏng callback từ cổng thanh toán về backend.</span>
          </div>
        </Modal>
      )}
    </div>
  );
}
