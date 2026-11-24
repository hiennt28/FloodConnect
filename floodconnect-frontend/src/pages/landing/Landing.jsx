import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { BrainCircuit, HandHeart, LifeBuoy, PhoneCall, Siren, Users, Waves } from 'lucide-react';
import { useApp } from '@/hooks';
import { Bone } from '@/components/common';
import { EMERGENCY_NUMBERS, HOTLINE, money } from '@/utils';

/* Số tiền lớn viết gọn cho ô thống kê ("1,2 tỷ đ"); số đầy đủ để trong title */
const moneyShort = (v) => {
  const f = (x) => x.toLocaleString('vi-VN', { maximumFractionDigits: 1 });
  if (v >= 1e9) return `${f(v / 1e9)} tỷ đ`;
  if (v >= 1e6) return `${f(v / 1e6)} triệu đ`;
  return money(v);
};
const hhmm = (t) => new Date(t).toLocaleTimeString('vi-VN', { hour: '2-digit', minute: '2-digit' });

const STEPS = [
  { icon: Siren, title: 'Gửi yêu cầu SOS', text: 'Người dân mô tả tình trạng và chọn vị trí. Hệ thống cảnh báo nếu gần đó vừa có ca tương tự, để tránh gửi trùng.' },
  { icon: BrainCircuit, title: 'AI đánh giá, Admin điều phối', text: 'AI gợi ý mức độ nguy hiểm và tổ chức phù hợp (khoảng cách, chuyên môn, tình trạng sẵn sàng). Người điều phối quyết định cuối cùng.' },
  { icon: LifeBuoy, title: 'Đội cứu hộ tiếp nhận', text: 'Tổ chức được chỉ định, hoặc tổ chức gần đó tự nhận ca. Người dân nhận thông báo theo thời gian thực đến khi hoàn thành.' },
];

const AUDIENCES = [
  { icon: Users, title: 'Người dân vùng lũ', items: ['Gửi SOS và theo dõi trạng thái ca', 'Chia sẻ tình hình khu vực trên bảng tin', 'Báo đã an toàn khi không còn cần cứu'], cta: { to: '/register', label: 'Tạo tài khoản' } },
  { icon: LifeBuoy, title: 'Tổ chức cứu hộ', items: ['Gửi hồ sơ để Admin xét duyệt', 'Nhận ca SOS gần khu vực hoạt động', 'Mở chiến dịch quyên góp cho vùng lũ'], cta: { to: '/register?type=org', label: 'Đăng ký tổ chức' } },
  { icon: HandHeart, title: 'Người ủng hộ', items: ['Ủng hộ chiến dịch qua cổng VNPay', 'Xem sao kê công khai từng khoản ủng hộ', 'Theo dõi lịch sử đóng góp của bạn'], cta: { to: '/login', label: 'Đăng nhập để ủng hộ' } },
];

/* Trang giới thiệu cho khách chưa đăng nhập. Đứng riêng, không nằm trong Layout (khách chưa có menu theo vai trò).
   Số liệu lấy từ GET /api/public/stats: chỉ con số tổng hợp, backend lưu tạm 5 phút. */
export function Landing() {
  const { load } = useApp();
  const [stats, setStats] = useState(undefined); // undefined: đang tải, null: không tải được
  useEffect(() => { load.publicStats().then(setStats); }, []); // eslint-disable-line react-hooks/exhaustive-deps

  const STAT_ITEMS = stats ? [
    { label: 'Ca SOS đã cứu hộ xong', value: stats.completedSos.toLocaleString('vi-VN') },
    { label: 'Tổ chức cứu hộ đang hoạt động', value: stats.activeOrgs.toLocaleString('vi-VN') },
    { label: 'Chiến dịch đang quyên góp', value: stats.activeCampaigns.toLocaleString('vi-VN') },
    { label: 'Đã ủng hộ qua hệ thống', value: moneyShort(stats.totalRaised), title: money(stats.totalRaised) },
  ] : [];

  return (
    <div className="landing">
      {/* Bọc thanh trên cùng + hero để ảnh nền phủ liền một khối (xem .landing-head trong theme.css) */}
      <div className="landing-head">
        <header className="landing-top">
          <Link to="/welcome" className="brand light"><span className="brand-mark"><Waves size={22} /></span><span><b>FloodConnect</b><small>Cứu trợ cộng đồng</small></span></Link>
          <nav className="landing-nav">
            <Link to="/feed" className="landing-link">Bảng tin</Link>
            <Link to="/login" className="btn btn-sm landing-ghost">Đăng nhập</Link>
            <Link to="/register" className="btn btn-sm landing-solid">Đăng ký</Link>
          </nav>
        </header>

        <section className="landing-hero">
          <div className="landing-hero-copy">
            <h1>Kết nối người cần cứu với người đang đến cứu.</h1>
            <p>FloodConnect giúp người dân vùng lũ gửi yêu cầu cứu hộ, giúp đội cứu hộ nhận đúng ca gần mình và giúp mọi người ủng hộ các chiến dịch cứu trợ minh bạch.</p>
            <div className="landing-cta">
              <Link to="/login" className="btn btn-lg landing-sos"><Siren size={18} /> Gửi yêu cầu SOS</Link>
              <Link to="/feed" className="btn btn-lg landing-ghost">Xem bảng tin cộng đồng</Link>
            </div>
            <small>Cần đăng nhập để gửi SOS, để đội cứu hộ có số điện thoại liên lạc lại với bạn.</small>
          </div>
          <aside className="landing-hot" aria-label="Số điện thoại khẩn cấp">
            <span className="landing-hot-title"><PhoneCall size={16} /> Nguy hiểm tức thời? Gọi ngay</span>
            <a className="landing-hot-big" href={`tel:${HOTLINE.number}`}>{HOTLINE.number}</a>
            <span className="muted small">{HOTLINE.label} 24/7, miễn phí</span>
            <div className="landing-hot-list">
              {EMERGENCY_NUMBERS.filter((h) => h.number !== HOTLINE.number).map((h) => <a key={h.number} href={`tel:${h.number}`}><span>{h.label}</span><b>{h.number}</b></a>)}
            </div>
          </aside>
        </section>
      </div>

      <main className="landing-body">
        {stats !== null && (
          <section className="landing-stats" aria-label="Số liệu FloodConnect" aria-busy={stats === undefined}>
            {stats === undefined
              ? Array.from({ length: 4 }, (_, i) => <div key={i} className="landing-stat"><Bone w="55%" h={30} /><Bone w="80%" h={12} /></div>)
              : STAT_ITEMS.map((s) => <div key={s.label} className="landing-stat" title={s.title}><b>{s.value}</b><span>{s.label}</span></div>)}
          </section>
        )}
        {stats && <p className="landing-note muted small">Số liệu toàn hệ thống, cập nhật lúc {hhmm(stats.updatedAt)}.</p>}

        <section className="landing-sec">
          <h2>Cách hoạt động</h2>
          <ol className="landing-steps">
            {STEPS.map(({ icon: Icon, title, text }, i) => (
              <li key={title} className="landing-step">
                <span className="landing-step-no">{i + 1}</span>
                <Icon size={22} className="landing-step-icon" />
                <b>{title}</b>
                <p className="muted">{text}</p>
              </li>
            ))}
          </ol>
        </section>

        <section className="landing-sec">
          <h2>Dành cho ai</h2>
          <div className="landing-cards">
            {AUDIENCES.map(({ icon: Icon, title, items, cta }) => (
              <div key={title} className="card pad landing-card">
                <b className="row gap8"><Icon size={20} /> {title}</b>
                <ul>{items.map((x) => <li key={x}>{x}</li>)}</ul>
                <Link to={cta.to} className="btn btn-soft btn-sm">{cta.label}</Link>
              </div>
            ))}
          </div>
        </section>
      </main>

      <footer className="landing-foot muted small">
        <span>FloodConnect · Cứu trợ cộng đồng mùa lũ</span>
        <span>{HOTLINE.label} 24/7: <a href={`tel:${HOTLINE.number}`}>{HOTLINE.number}</a></span>
      </footer>
    </div>
  );
}
