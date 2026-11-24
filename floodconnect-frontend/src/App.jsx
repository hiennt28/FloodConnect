import { Navigate, Route, Routes } from 'react-router-dom';
import { Waves } from 'lucide-react';
import { useApp } from '@/hooks';
import { SkeletonApp } from '@/components/common';
import Layout from '@/components/layout/Layout';
import { Login } from '@/pages/auth/Login';
import { Register } from '@/pages/auth/Register';
import { Forgot } from '@/pages/auth/Forgot';
import { ResetPassword } from '@/pages/auth/ResetPassword';
import { VerifyEmail } from '@/pages/auth/VerifyEmail';
import { Feed } from '@/pages/community/Feed';
import { Landing } from '@/pages/landing/Landing';
import { PostDetail } from '@/pages/community/PostDetail';
import { SearchPage } from '@/pages/community/SearchPage';
import { UserProfile } from '@/pages/community/UserProfile';
import { MyProfile } from '@/pages/community/MyProfile';
import { Notifications } from '@/pages/community/Notifications';
import MapPage from '@/pages/map/MapPage';
import SosPage from '@/pages/sos/SosPage';
import { CampaignList } from '@/pages/campaign/CampaignList';
import { CampaignCreate } from '@/pages/campaign/CampaignCreate';
import { CampaignEdit } from '@/pages/campaign/CampaignEdit';
import { CampaignDetail } from '@/pages/campaign/CampaignDetail';
import { Donate } from '@/pages/campaign/Donate';
import { MyDonations } from '@/pages/campaign/MyDonations';
import { PaymentResult } from '@/pages/campaign/PaymentResult';
import RescueDashboard from '@/pages/rescue/RescueDashboard';
import { AdminDashboard } from '@/pages/admin/AdminDashboard';
import { AdminSos } from '@/pages/admin/AdminSos';
import { OrgManage } from '@/pages/admin/OrgManage';
import { OrgDetail } from '@/pages/admin/OrgDetail';
import { CitizenManage } from '@/pages/admin/CitizenManage';
import { AdminAccounts } from '@/pages/admin/AdminAccounts';
import { PostReports } from '@/pages/admin/PostReports';
import { PostReportDetail } from '@/pages/admin/PostReportDetail';
import { CampaignReports } from '@/pages/admin/CampaignReports';
import { CampaignReportDetail } from '@/pages/admin/CampaignReportDetail';

function Home() {
  const { me, canRescue } = useApp();
  if (!me) return <Navigate to="/welcome" replace />; // khách: trang giới thiệu
  if (me.role === 'ADMIN') return <Navigate to="/admin" replace />;
  if (canRescue) return <Navigate to="/rescue" replace />;
  return <Navigate to="/feed" replace />;
}

/* Bảo vệ route theo trạng thái đăng nhập / vai trò */
function Guard({ role, children }) {
  const { me, canRescue } = useApp();
  if (!me) return <Navigate to="/login" replace />;
  if (role === 'ADMIN' && me.role !== 'ADMIN') return <Navigate to="/" replace />;
  if (role === 'ORG' && !canRescue) return <Navigate to="/" replace />;
  if (role === 'NOT_ADMIN' && me.role === 'ADMIN') return <Navigate to="/admin" replace />;
  /* Chỉ người dân gửi SOS; Admin về trang điều phối, tổ chức về trang chủ của mình (Home tự chuyển tới /rescue hoặc /feed) */
  if (role === 'CITIZEN' && me.role !== 'CITIZEN') return <Navigate to={me.role === 'ADMIN' ? '/admin/sos' : '/'} replace />;
  return children;
}

export default function App() {
  const { me, booting } = useApp();
  /* Khởi động (đã có token): vẽ sẵn khung ứng dụng thay vì một dòng chữ, trong lúc chờ tải hồ sơ + dữ liệu theo vai trò */
  if (booting) return <SkeletonApp brand={<div className="brand"><span className="brand-mark"><Waves size={22} /></span><span><b>FloodConnect</b><small>Cứu trợ cộng đồng</small></span></div>} />;
  return (
    <Routes>
      <Route path="/welcome" element={me ? <Navigate to="/" replace /> : <Landing />} />
      <Route path="/login" element={me ? <Navigate to="/" replace /> : <Login />} />
      <Route path="/register" element={me ? <Navigate to="/" replace /> : <Register />} />
      <Route path="/forgot" element={<Forgot />} />
      <Route path="/reset-password" element={<ResetPassword />} />
      <Route path="/verify-email" element={<VerifyEmail />} />
      <Route element={<Layout />}>
        <Route index element={<Home />} />
        <Route path="/feed" element={<Feed />} />
        <Route path="/posts/:id" element={<PostDetail />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/users/:id" element={<UserProfile />} />
        <Route path="/map" element={<Guard><MapPage /></Guard>} />
        <Route path="/campaigns" element={<Guard><CampaignList /></Guard>} />
        <Route path="/campaigns/new" element={<Guard role="ORG"><CampaignCreate /></Guard>} />
        <Route path="/campaigns/:id/edit" element={<Guard role="ORG"><CampaignEdit /></Guard>} />
        <Route path="/campaigns/:id" element={<Guard><CampaignDetail /></Guard>} />
        <Route path="/campaigns/:id/donate" element={<Guard><Donate /></Guard>} />
        <Route path="/my-donations" element={<Guard><MyDonations /></Guard>} />
        <Route path="/payment/result" element={<Guard><PaymentResult /></Guard>} />
        <Route path="/sos" element={<Guard role="CITIZEN"><SosPage /></Guard>} />
        <Route path="/profile" element={<Guard><MyProfile /></Guard>} />
        <Route path="/notifications" element={<Guard><Notifications /></Guard>} />
        <Route path="/rescue" element={<Guard role="ORG"><RescueDashboard /></Guard>} />
        <Route path="/admin" element={<Guard role="ADMIN"><AdminDashboard /></Guard>} />
        <Route path="/admin/sos" element={<Guard role="ADMIN"><AdminSos /></Guard>} />
        <Route path="/admin/orgs" element={<Guard role="ADMIN"><OrgManage /></Guard>} />
        <Route path="/admin/orgs/:id" element={<Guard role="ADMIN"><OrgDetail /></Guard>} />
        <Route path="/admin/citizens" element={<Guard role="ADMIN"><CitizenManage /></Guard>} />
        {/* Chỉ Admin gốc: trang tự đưa Admin thường về /admin (backend cũng trả 403) */}
        <Route path="/admin/admins" element={<Guard role="ADMIN"><AdminAccounts /></Guard>} />
        <Route path="/admin/post-reports" element={<Guard role="ADMIN"><PostReports /></Guard>} />
        <Route path="/admin/post-reports/:id" element={<Guard role="ADMIN"><PostReportDetail /></Guard>} />
        <Route path="/admin/campaign-reports" element={<Guard role="ADMIN"><CampaignReports /></Guard>} />
        <Route path="/admin/campaign-reports/:id" element={<Guard role="ADMIN"><CampaignReportDetail /></Guard>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
