import { useEffect, useRef, useState } from 'react';
import { Link, NavLink, Outlet, useNavigate } from 'react-router-dom';
import { Asterisk, Bell, Building2, ChevronDown, FileWarning, Flag, HandHeart, History, Home, LayoutDashboard, LifeBuoy, LogOut, Map, Megaphone, Menu, Monitor, Moon, PhoneCall, Search, ShieldCheck, Siren, Sun, User, Users, Waves } from 'lucide-react';
import { useApp, useTheme } from '@/hooks';
import { Avatar } from '@/components/common';
import { ago, HOTLINE, ROLE_LABEL } from '@/utils';

const THEME_OPTIONS = [{ v: 'light', label: 'Sáng', icon: Sun }, { v: 'dark', label: 'Tối', icon: Moon }, { v: 'system', label: 'Theo máy', icon: Monitor }];

function navFor(me, canRescue) {
  if (me?.role === 'ADMIN') {
    return [
      { to: '/admin', icon: LayoutDashboard, label: 'Tổng quan', end: true },
      { to: '/admin/sos', icon: Siren, label: 'Điều phối SOS' },
      { to: '/map', icon: Map, label: 'Bản đồ cứu hộ' },
      { to: '/campaigns', icon: HandHeart, label: 'Chiến dịch cứu trợ' }, // cùng tên với mọi vai trò
      { to: '/admin/orgs', icon: Building2, label: 'Quản lý tổ chức' },
      { to: '/admin/citizens', icon: Users, label: 'Quản lý người dân' },
      // Chỉ Admin gốc tạo / vô hiệu hóa được Admin khác
      ...(me.rootAdmin ? [{ to: '/admin/admins', icon: ShieldCheck, label: 'Quản lý Admin' }] : []),
      { to: '/admin/post-reports', icon: Flag, label: 'Báo cáo bài đăng' },
      { to: '/admin/campaign-reports', icon: FileWarning, label: 'Báo cáo chiến dịch' },
      { to: '/feed', icon: Home, label: 'Bảng tin' },
    ];
  }
  const items = [
    { to: '/feed', icon: Home, label: 'Trang chủ' },
    { to: '/map', icon: Map, label: 'Bản đồ cứu hộ' }, // cùng tên với mọi vai trò (bản đồ có ca SOS và tổ chức cứu hộ)
    { to: '/campaigns', icon: HandHeart, label: 'Chiến dịch cứu trợ' },
  ];
  if (canRescue) items.splice(1, 0, { to: '/rescue', icon: LifeBuoy, label: 'Điều phối cứu hộ' });
  // Chỉ người dân gửi SOS (khách vẫn thấy mục này, bấm vào sẽ được mời đăng nhập); tổ chức tiếp nhận ca ở "Điều phối cứu hộ"
  if (!me || me.role === 'CITIZEN') items.push({ to: '/sos', icon: Asterisk, label: 'Cứu hộ SOS', sos: true });
  return items;
}

export default function Layout() {
  const { me, myOrg, canRescue, state, social, auth, isLocked } = useApp();
  const { pref: themePref, setPref: setThemePref } = useTheme();
  const nav = useNavigate();
  const [q, setQ] = useState('');
  const [open, setOpen] = useState(null);
  const [mobileNav, setMobileNav] = useState(false);
  const wrap = useRef(null);

  useEffect(() => {
    const h = (e) => { if (wrap.current && !wrap.current.contains(e.target)) setOpen(null); };
    document.addEventListener('mousedown', h);
    return () => document.removeEventListener('mousedown', h);
  }, []);

  const notifs = me ? state.notifications : [];
  const unread = notifs.filter((n) => !n.read).length;

  const submitSearch = (e) => {
    e.preventDefault();
    nav(`/search?q=${encodeURIComponent(q.trim())}`);
  };

  return (
    <div className={`app ${mobileNav ? 'nav-open' : ''}`}>
      <aside className="sidebar">
        <Link to="/" className="brand" onClick={() => setMobileNav(false)}>
          <span className="brand-mark"><Waves size={22} /></span>
          <span><b>FloodConnect</b><small>Cứu trợ cộng đồng</small></span>
        </Link>
        <nav className="side-nav">
          {navFor(me, canRescue).map(({ to, icon: Icon, label, sos, end }) => (
            <NavLink key={to} to={to} end={end} onClick={() => setMobileNav(false)} className={({ isActive }) => `nav-item ${isActive ? 'active' : ''} ${sos ? 'nav-sos' : ''}`}>
              <Icon size={20} /> {label}
            </NavLink>
          ))}
        </nav>
        <div className="hotline">
          <div className="hotline-title"><PhoneCall size={15} /> {HOTLINE.label} 24/7</div>
          <a href={`tel:${HOTLINE.number}`} className="hotline-num">{HOTLINE.number}</a>
        </div>
      </aside>
      <div className="scrim" onClick={() => setMobileNav(false)} />

      <div className="main">
        <header className="header" ref={wrap}>
          <button className="icon-btn only-mobile" aria-label="Mở menu" onClick={() => setMobileNav(true)}><Menu size={20} /></button>
          <form className="search" onSubmit={submitSearch} role="search">
            <Search size={18} />
            <input value={q} onChange={(e) => setQ(e.target.value)} placeholder="Tìm người dân, tổ chức cứu trợ..." aria-label="Tìm kiếm" />
          </form>
          {me ? (
            <div className="header-actions">
              <div className="pop-anchor">
                <button className="icon-btn" aria-label="Thông báo" onClick={() => setOpen(open === 'n' ? null : 'n')}>
                  <Bell size={20} />{unread > 0 && <span className="count-dot">{unread > 9 ? '9+' : unread}</span>}
                </button>
                {open === 'n' && (
                  <div className="popover notif-pop">
                    <div className="pop-head"><b>Thông báo</b><button className="link" onClick={() => social.markAllRead()}>Đánh dấu tất cả đã đọc</button></div>
                    <div className="pop-list">
                      {notifs.length === 0 && <div className="muted pad">Chưa có thông báo nào.</div>}
                      {notifs.slice(0, 12).map((n) => (
                        <button key={n.id} className={`notif ${n.read ? '' : 'unread'}`} onClick={() => { social.markRead(n.id); setOpen(null); if (n.link) nav(n.link); }}>
                          <span className="notif-text">{n.text}</span>
                          <span className="muted small">{ago(n.createdAt)}</span>
                        </button>
                      ))}
                    </div>
                    <Link to="/notifications" className="pop-foot" onClick={() => setOpen(null)}>Xem tất cả</Link>
                  </div>
                )}
              </div>
              <div className="pop-anchor">
                <button className="user-chip" onClick={() => setOpen(open === 'u' ? null : 'u')}>
                  <Avatar user={me} size={36} />
                  <span className="user-chip-text">
                    <b>{myOrg?.approval === 'APPROVED' ? myOrg.name : me.name}</b>
                    <small>{ROLE_LABEL[me.role]}{isLocked(me) ? ' • Đang bị khóa' : myOrg && myOrg.approval !== 'APPROVED' ? ' • Chờ duyệt' : ' • Đã xác minh'}</small>
                  </span>
                  <ChevronDown size={16} />
                </button>
                {open === 'u' && (
                  <div className="popover menu-pop" onClick={() => setOpen(null)}>
                    <Link to="/profile"><User size={16} /> Hồ sơ của tôi</Link>
                    <Link to="/my-donations"><History size={16} /> Lịch sử đóng góp của tôi</Link>
                    {canRescue && <Link to="/campaigns?tab=mine"><Megaphone size={16} /> Chiến dịch của chúng tôi</Link>}
                    {/* Chọn giao diện: không đóng menu khi bấm để thấy ngay kết quả */}
                    <div className="menu-theme" role="group" aria-label="Giao diện" onClick={(e) => e.stopPropagation()}>
                      <span className="muted small">Giao diện</span>
                      <div className="seg">
                        {THEME_OPTIONS.map(({ v, label, icon: Icon }) => (
                          <button key={v} type="button" className={themePref === v ? 'on' : ''} aria-pressed={themePref === v} onClick={() => setThemePref(v)}><Icon size={14} /> {label}</button>
                        ))}
                      </div>
                    </div>
                    <button onClick={() => { auth.logout(); nav('/login'); }} className="danger"><LogOut size={16} /> Đăng xuất</button>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="header-actions">
              <Link to="/login" className="btn btn-ghost">Đăng nhập</Link>
              <Link to="/register" className="btn btn-primary">Đăng ký</Link>
            </div>
          )}
        </header>

        {me && myOrg && myOrg.approval === 'PENDING' && <div className="banner banner-info">Hồ sơ tổ chức đang chờ xét duyệt. Trong thời gian chờ, bạn dùng được các chức năng của người dân.</div>}
        {me && myOrg && myOrg.approval === 'REJECTED' && <div className="banner banner-danger">Hồ sơ tổ chức bị từ chối: {myOrg.rejectReason}. <Link to="/profile">Chỉnh sửa và gửi lại</Link></div>}
        {me && isLocked(me) && <div className="banner banner-danger">Tài khoản đang bị khóa đến {new Date(me.lock.until).toLocaleDateString('vi-VN')}.</div>}

        <main className="content"><Outlet /></main>
      </div>
    </div>
  );
}
