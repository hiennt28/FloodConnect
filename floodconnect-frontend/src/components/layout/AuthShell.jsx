import { Link } from 'react-router-dom';
import { Waves } from 'lucide-react';
import { HOTLINE, SEVERITY } from '@/utils';

/* Màu sáng hơn để đọc được trên nền xanh sông đậm */
const GAUGE = { KHAN_CAP: '#f08a7e', CAO: '#f2a46b', TRUNG_BINH: '#e8c766', THAP: '#8fcfa6' };

export function Shell({ title, sub, children, wide }) {
  return (
    <div className="auth">
      <div className="auth-side">
        <Link to="/feed" className="brand light"><span className="brand-mark"><Waves size={22} /></span><span><b>FloodConnect</b><small>Cứu trợ cộng đồng</small></span></Link>
        <div className="auth-side-copy">
          <h2>Kết nối người cần cứu với người đang đến cứu.</h2>
          <p>Gửi SOS, theo dõi đội cứu hộ trên bản đồ và ủng hộ các chiến dịch minh bạch cho vùng lũ.</p>
          <div className="auth-gauge" aria-label="Thang mức độ khẩn cấp của ca SOS">
            <span className="auth-gauge-title">AI gợi ý mức độ, người điều phối quyết định</span>
            {['KHAN_CAP', 'CAO', 'TRUNG_BINH', 'THAP'].map((k) => (
              <div key={k} className="gauge-row" style={{ '--g': GAUGE[k] }}><i style={{ width: `${(SEVERITY[k].w / 4) * 100}%` }} /><span>{SEVERITY[k].label}</span></div>
            ))}
          </div>
        </div>
        <div className="auth-hotline">{HOTLINE.label} 24/7 <b>{HOTLINE.number}</b></div>
      </div>
      <div className="auth-main">
        <div className={`auth-card ${wide ? 'wide' : ''}`}>
          <h1>{title}</h1>
          {sub && <p className="muted">{sub}</p>}
          {children}
        </div>
      </div>
    </div>
  );
}
