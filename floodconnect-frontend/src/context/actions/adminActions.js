import { adminApi } from '@/services/api';
import { EMAIL_RULE, PHONE_RULE } from '@/utils';

/** Chức năng của Admin: thống kê, duyệt/khóa tổ chức, khóa người dân, xử lý báo cáo. */
export function createAdminActions({ run, get }) {
  return {
    /* Mọi con số trên trang tổng quan đều do backend đếm/cộng trên toàn bộ dữ liệu,
       không tự đếm trên danh sách giao diện đã tải (chỉ 100 ca / 100 chiến dịch mới nhất). */
    stats: async (period) => {
      const [citizens, sos, sosStatus, overview, orgs, topOrgs, topDonors] = await Promise.all([
        get(adminApi.citizenStats), get(() => adminApi.sosStats(period)), get(() => adminApi.sosStatusStats(period)),
        get(adminApi.overview), get(adminApi.orgStats), get(adminApi.topOrgs), get(adminApi.topDonors),
      ]);
      return { citizens, sos, sosStatus, overview, orgs, topOrgs, topDonors };
    },
    timeline: (days) => get(() => adminApi.sosTimeline(days)),
    orgs: (filter) => get(() => adminApi.orgs(filter)),
    orgDetail: (id) => get(() => adminApi.orgDetail(id)),
    approveOrg: (id) => run(() => adminApi.reviewOrg(id, { decision: 'APPROVED' })),
    rejectOrg(id, reason) {
      if (!reason.trim()) return 'Vui lòng nhập lý do từ chối.';
      return run(() => adminApi.reviewOrg(id, { decision: 'REJECTED', reason: reason.trim() }));
    },
    lockOrg(id, months, reason) {
      const m = Number(months);
      if (!Number.isInteger(m) || m < 1) return 'Thời gian khóa phải là số nguyên ≥ 1 tháng.';
      if (!reason.trim()) return 'Vui lòng nhập lý do khóa tài khoản.'; // lý do được gửi cho người bị khóa, không dùng lý do chung chung
      return run(() => adminApi.lockOrg(id, { months: m, reason: reason.trim() }));
    },
    unlockOrg: (id) => run(() => adminApi.unlockOrg(id)),
    citizens: (filter) => get(() => adminApi.citizens(filter)),
    citizenSearch: (q, page, size) => get(() => adminApi.citizenSearch(q, page, size)),
    citizenDetail: (id) => get(() => adminApi.citizenDetail(id)),
    lockCitizen(id, months, reason) {
      const m = Number(months);
      if (!Number.isInteger(m) || m < 1) return 'Thời gian khóa phải là số nguyên ≥ 1 tháng.';
      if (!reason.trim()) return 'Vui lòng nhập lý do khóa tài khoản.'; // lý do được gửi cho người bị khóa, không dùng lý do chung chung
      return run(() => adminApi.lockCitizen(id, { months: m, reason: reason.trim() }));
    },
    unlockCitizen: (id) => run(() => adminApi.unlockCitizen(id)),
    /* ----- Tài khoản Admin (chỉ Admin gốc) ----- */
    admins: () => get(adminApi.admins),
    /* Tạo Admin: { ok: true } hoặc { errors: { fullName|email|phone: thông báo } } để hiện lỗi đúng ô */
    async createAdmin(f) {
      const e = {};
      if (!f.fullName?.trim()) e.fullName = 'Vui lòng nhập họ tên.';
      if (!EMAIL_RULE(f.email?.trim() || '')) e.email = 'Email không hợp lệ.';
      if (!PHONE_RULE(f.phone?.trim() || '')) e.phone = 'Số điện thoại chỉ gồm 9–11 chữ số.';
      if (Object.keys(e).length) return { errors: e };
      try {
        await adminApi.createAdmin({ fullName: f.fullName.trim(), email: f.email.trim(), phone: f.phone.trim() });
        return { ok: true };
      } catch (ex) {
        return { errors: ex.errors && Object.keys(ex.errors).length ? ex.errors : { email: ex.message } };
      }
    },
    resendAdminInvite: (id) => run(() => adminApi.resendAdminInvite(id)),
    disableAdmin(id, reason) {
      if (!reason?.trim()) return 'Vui lòng nhập lý do vô hiệu hóa.';
      return run(() => adminApi.disableAdmin(id, reason.trim()));
    },
    enableAdmin: (id) => run(() => adminApi.enableAdmin(id)),
    /* Trả cả trang { items, total, last } (bảng có Trước / Sau); null nếu lỗi */
    reportedPosts: (page = 0) => get(() => adminApi.reportedPosts(page)),
    postReports: (page = 0) => get(() => adminApi.postReports(page)),
    postReportDetail: (id) => get(() => adminApi.postReportDetail(id)),
    resolvePost: (id, decision, reason, description) => run(() => adminApi.moderatePost(id, { decision, reason: reason || null, description: description || null })),
    reportedCampaigns: (page = 0) => get(() => adminApi.reportedCampaigns(page)),
    campaignReports: (page = 0) => get(() => adminApi.campaignReports(page)),
    campaignReportDetail: (id) => get(() => adminApi.campaignReportDetail(id)),
    resolveCampaign: (id, decision, reason, description) => run(() => adminApi.moderateCampaign(id, { decision, reason: reason || null, description: description || null })),
  };
}
