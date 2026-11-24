import { createContext, useCallback, useContext, useEffect, useMemo, useRef, useState } from 'react';
import { tokens } from '@/services/http';
import { connectNotifications } from '@/services/realtime';
import { adminApi, campaignApi, notificationApi, orgApi, rescueApi, sosApi, userApi } from '@/services/api';
import { mDonation, mNotif, mOrg, mProfile, mSos, mUserSummary, sid } from '@/services/api/mappers';
import { empty, upBy, upUser, putCampaign, isLocked } from './draft';
import { createAuthActions } from './actions/authActions';
import { createProfileActions } from './actions/profileActions';
import { createLoadActions } from './actions/loadActions';
import { createSocialActions } from './actions/socialActions';
import { createCampaignActions } from './actions/campaignActions';
import { createSosActions } from './actions/sosActions';
import { createAdminActions } from './actions/adminActions';

const Ctx = createContext(null);
export const useApp = () => useContext(Ctx);

export function AppProvider({ children }) {
  const [state, setState] = useState(empty);
  const ref = useRef(state);
  const [booting, setBooting] = useState(() => !!tokens.get());
  const [toasts, setToasts] = useState([]);

  const mutate = useCallback((fn) => {
    const draft = structuredClone(ref.current);
    const result = fn(draft);
    ref.current = draft;
    setState(draft);
    return result;
  }, []);

  const toast = useCallback((text, tone = 'ok') => {
    const id = `t_${Date.now()}${Math.random().toString(36).slice(2, 5)}`;
    setToasts((t) => {
      if (t.some((x) => x.text === text && x.tone === tone)) return t; // thông báo giống hệt đang hiện: bỏ qua, không chồng thêm
      return [...t, { id, text, tone }].slice(-3);                      // tối đa 3 thông báo cùng lúc
    });
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 3200);
  }, []);

  const me = state.session ? state.users.find((u) => u.id === state.session.userId) : null;
  const myOrg = me?.orgId ? state.orgs.find((o) => o.id === me.orgId) : null;
  const canRescue = !!(myOrg && myOrg.approval === 'APPROVED');
  const orgLocked = canRescue && isLocked(me);

  /* ================= ĐỒNG BỘ DỮ LIỆU THEO VAI TRÒ ================= */
  const refresh = useCallback(async () => {
    if (!tokens.get()) return null;
    const safe = (p) => p.catch(() => null);
    const prof = await safe(userApi.me());
    if (!prof) return null;
    const role = prof.role;
    const org = prof.organization;
    /* Mọi ca ĐANG HOẠT ĐỘNG (luôn đủ, kể cả ca gửi đã lâu; Admin thấy cả ca chờ xác minh). Ca đã kết thúc không đồng bộ định kỳ:
       từng trang tự tải theo trang (Điều phối SOS, tab "Tất cả", lịch sử của tổ chức) */
    const jobs = { notifs: safe(notificationApi.list()), active: safe(sosApi.active()) };
    /* Ca đã kết thúc được mở từ thông báo (không nằm trong danh sách đang hoạt động): tải lại riêng từng ca, nếu không ca sẽ biến mất khi đồng bộ */
    const pinned = ref.current.sos.filter((s) => s.pinned).map((s) => s.id);
    const detailOf = role === 'ADMIN' ? adminApi.sosDetail : sosApi.detail;
    if (pinned.length) jobs.pinned = Promise.all(pinned.map((id) => safe(detailOf(id)))).then((xs) => xs.filter(Boolean));
    if (role !== 'ADMIN') {
      if (role === 'CITIZEN') jobs.mine = safe(sosApi.mine()); // chỉ người dân gửi SOS nên chỉ người dân có "ca của tôi"
      jobs.donations = safe(campaignApi.myDonations());
      if (role === 'RESCUE_ORG') {
        jobs.cases = safe(rescueApi.cases());
        if (org?.status === 'ACTIVE' && org.operatingStatus === 'AVAILABLE') jobs.suggested = safe(rescueApi.suggested());
        jobs.myCampaigns = safe(campaignApi.mine());
        jobs.orgMe = safe(orgApi.me());
      }
    }
    const keys = Object.keys(jobs);
    const vals = await Promise.all(keys.map((k) => jobs[k]));
    const r = Object.fromEntries(keys.map((k, i) => [k, vals[i]]));

    mutate((d) => {
      upUser(d, mProfile(prof));
      const orgInfo = r.orgMe || org;
      if (orgInfo) upBy(d.orgs, mOrg(orgInfo, { userId: sid(prof.id) }));
      d.session = { userId: sid(prof.id) };
      if (r.notifs) d.notifications = r.notifs.items.map((n) => mNotif(n, role));
      if (r.donations) d.donations = r.donations.items.map(mDonation);
      if (r.myCampaigns) r.myCampaigns.forEach((c) => putCampaign(d, c));

      const byId = new Map();
      const feed = (list, extra = {}) => (list || []).forEach((i) => {
        if (i.citizen) upUser(d, { ...mUserSummary(i.citizen), phone: i.citizenPhone || undefined });
        const n = { ...mSos(i), ...extra };
        const o = byId.get(n.id);
        byId.set(n.id, o ? { ...o, ...n, citizenId: n.citizenId ?? o.citizenId, citizenPhone: n.citizenPhone ?? o.citizenPhone, suggested: n.suggested || o.suggested, matching: n.matching || o.matching } : n);
      });
      feed(r.active?.items);
      feed(r.mine);
      feed(r.cases);
      feed(r.suggested, { suggested: true });
      feed(r.pinned, { pinned: true });
      d.sos = [...byId.values()];
      /* Số ca đang hoạt động thật và trần: giao diện báo khi danh sách bị cắt (total > số ca đã tải) */
      if (r.active) d.sosActive = { total: r.active.total, limit: r.active.limit };
    });
    return prof;
  }, [mutate]);

  useEffect(() => {
    if (!tokens.get()) { setBooting(false); return undefined; }
    refresh().finally(() => setBooting(false));
    return undefined;
  }, [refresh]);

  useEffect(() => {
    const dropSession = () => mutate((d) => { Object.assign(d, empty()); });
    const onStorage = (e) => { if (e.key === 'floodconnect-tokens' && !e.newValue) dropSession(); }; // đã đăng xuất ở tab khác
    window.addEventListener('floodconnect:logout', dropSession);
    window.addEventListener('storage', onStorage);
    return () => { window.removeEventListener('floodconnect:logout', dropSession); window.removeEventListener('storage', onStorage); };
  }, [mutate]);

  const loggedIn = !!state.session;
  useEffect(() => {
    if (!loggedIn) return undefined;
    // Thông báo mới được backend đẩy qua WebSocket: hiện ngay và tải lại dữ liệu (gom các tin đến dồn dập thành một lần tải)
    let pending;
    const stop = connectNotifications((n) => {
      if (n?.title) toast(n.title);
      clearTimeout(pending);
      pending = setTimeout(() => refresh(), 500);
    });
    // Tải lại định kỳ vẫn giữ: dự phòng khi mất kết nối WebSocket, và cho dữ liệu không đi kèm thông báo (vd. ca vừa bị tổ chức khác nhận)
    const t = setInterval(() => { if (!document.hidden) refresh(); }, 30000);
    const onFocus = () => refresh();
    window.addEventListener('focus', onFocus);
    return () => { stop(); clearTimeout(pending); clearInterval(t); window.removeEventListener('focus', onFocus); };
  }, [loggedIn, refresh, toast]);

  /* Gọi API: run -> null nếu thành công, thông báo lỗi nếu thất bại; get -> dữ liệu hoặc null (hiện toast lỗi); quiet -> như get nhưng không báo lỗi */
  const run = async (fn) => { try { await fn(); return null; } catch (e) { return e.message; } };
  const get = async (request) => { try { return await request(); } catch (e) { toast(e.message, 'err'); return null; } };
  const quiet = async (request) => { try { return await request(); } catch { return null; } };
  const pageOf = async (request) => { const r = await get(request); return r ? r.items : null; };

  /* ================= NGHIỆP VỤ (mỗi nhóm một file trong context/actions) ================= */
  const ctx = { mutate, refresh, run, get, quiet, pageOf, toast, ref, me, myOrg };
  const auth = createAuthActions(ctx);
  const profile = createProfileActions(ctx);
  const load = createLoadActions(ctx);
  const social = createSocialActions(ctx);
  const campaigns = createCampaignActions(ctx);
  const sosActions = createSosActions(ctx);
  const admin = createAdminActions(ctx);

  const value = useMemo(() => ({
    state, me, myOrg, canRescue, orgLocked, isLocked, toast, booting, auth, profile, social, campaigns, sos: sosActions, admin, load,
  }), [state, me, myOrg, canRescue, orgLocked, toast, booting]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <Ctx.Provider value={value}>
      {children}
      <div className="toast-wrap" role="status" aria-live="polite">
        {toasts.map((t) => <div key={t.id} className={`toast toast-${t.tone}`}>{t.text}</div>)}
      </div>
    </Ctx.Provider>
  );
}
