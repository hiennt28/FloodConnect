import { adminApi, geoApi, orgApi, rescueApi, sosApi } from '@/services/api';
import { mMatching, mOrg, mOrgBrief, mSos, mUserSummary, OP_TO_API, sid } from '@/services/api/mappers';
import { haversine, validateCoords } from '@/utils';
import { upBy, upUser } from '../draft';

/** Ca SOS: người dân gửi/sửa/hủy; tổ chức tiếp nhận/xác nhận/từ chối/hoàn thành; Admin phân công, điều chỉnh mức. */
export function createSosActions({ mutate, refresh, run, get, quiet, me }) {
  const after = async (fn) => { const e = await run(fn); await refresh(); return e; };

  return {
    async create({ desc = '', lat, lng, address, force }) {
      if (!desc.trim()) return { error: 'Vui lòng mô tả tình trạng cần cứu hộ.' };
      const err = validateCoords(lat, lng);
      if (err) return { error: err };
      try {
        const r = await sosApi.create({ description: desc.trim(), latitude: Number(lat), longitude: Number(lng), address: address || null, forceCreate: !!force });
        if (r.duplicateWarning) {
          /* Tọa độ ca kia đã được backend làm tròn ~100 m nên khoảng cách là gần đúng */
          return { nearby: r.similar.map((i) => ({ ...mSos(i), dist: haversine({ lat: Number(lat), lng: Number(lng) }, { lat: i.latitude, lng: i.longitude }) })) };
        }
        await refresh();
        return { id: sid(r.incident.id), code: r.incident.code, adminReview: r.incident.dangerLevel === 'KHONG' };
      } catch (e) { return { error: e.message }; }
    },
    /* Địa giới cho form SOS: chỉ là tiện ích, lỗi (chưa nạp dữ liệu địa giới 503 GEO_UNAVAILABLE, mất mạng, mã không có)
       trả null và không báo lỗi, để form bỏ qua phần kiểm tra phường/xã mà vẫn gửi SOS được.
       locateWard: { ward: { code, name, provinceCode, provinceName } | null, approximate } — ward null: không thuộc xã nào */
    async locateWard(lat, lng) {
      try { return await geoApi.locate(lat, lng); } catch { return null; }
    },
    /* { code, name, provinceCode, provinceName, areaKm2, centerLat, centerLng, outline (GeoJSON MultiPolygon) } */
    async wardDetail(code) {
      try { return await geoApi.ward(code); } catch { return null; }
    },
    cancel: (id) => after(() => sosApi.cancel(id)),
    /* Mở ca từ thông báo khi ca chưa có trong state. Vai trò khác Admin đồng bộ lại trước (ca vừa tạo / vừa đổi trạng thái).
       Rồi tải chi tiết ca: ca đã kết thúc không nằm trong danh sách đang hoạt động nên đánh dấu pinned để các lần đồng bộ sau
       tải lại riêng ca này thay vì làm mất nó (ca đang hoạt động thì không cần, đã có trong danh sách đồng bộ) */
    async loadCase(id) {
      const admin = me?.role === 'ADMIN';
      if (!admin) await refresh();
      const i = await quiet(() => (admin ? adminApi.sosDetail(id) : sosApi.detail(id)));
      if (!i) return;
      mutate((d) => {
        if (i.citizen) upUser(d, { ...mUserSummary(i.citizen), phone: i.citizenPhone || undefined });
        upBy(d.sos, { ...mSos(i), pinned: !['PENDING', 'ASSIGNED', 'ACCEPTED'].includes(i.status) });
      });
    },
    /* Ca theo trang (ca đã kết thúc, không đồng bộ định kỳ): { items (đã chuyển như mSos), total, last }; lỗi thì null.
       scope = 'admin' (bảng Điều phối, tham số như adminApi.sosPage) | 'public' (tab "Tất cả", status + page) | 'org' (lịch sử tổ chức) */
    async casesPage(scope, params = {}) {
      const call = { admin: () => adminApi.sosPage(params), public: () => sosApi.page(params), org: () => rescueApi.history(params.page, params.size) }[scope];
      const r = await quiet(call);
      if (!r) return null;
      mutate((d) => r.items.forEach((i) => { if (i.citizen) upUser(d, { ...mUserSummary(i.citizen), phone: i.citizenPhone || undefined }); }));
      return { items: r.items.map(mSos), total: r.total, last: r.last };
    },
    /* Ca Đã tiếp nhận không hủy được: người dân đã an toàn thì báo để tổ chức dừng cứu hộ. Ghi chú không bắt buộc */
    markSafe: (id, note = '') => after(() => sosApi.markSafe(id, note.trim() || null)),
    /* Chỉ sửa được mô tả (ca SOS không có ảnh, vị trí không đổi) và chỉ khi ca còn Chờ xử lý; AI đánh giá lại khi mô tả đổi */
    edit(id, { desc }) {
      if (typeof desc !== 'string' || !desc.trim()) return 'Vui lòng mô tả tình trạng cần cứu hộ.';
      return after(() => sosApi.update(id, desc.trim()));
    },
    accept: (id) => after(() => rescueApi.claim(id)),
    confirmAssigned: (id) => after(() => rescueApi.accept(id)),
    rejectAssigned(id, reason) {
      if (!reason.trim()) return 'Vui lòng nhập lý do từ chối.';
      return after(() => rescueApi.reject(id, reason.trim()));
    },
    updateStatus(id, next) {
      if (next !== 'DONE') return 'Chuyển trạng thái không hợp lệ.';
      return after(() => rescueApi.complete(id));
    },
    async setOrgStatus(status, confirmed) {
      try {
        const o = await orgApi.setOperatingStatus(OP_TO_API[status], !!confirmed);
        mutate((d) => { upBy(d.orgs, mOrg(o, { userId: d.session.userId })); });
        await refresh();
        return null;
      } catch (e) { return e.code === 'CONFIRM_REQUIRED' ? 'CONFIRM' : e.message; }
    },
    assign: (id, orgId) => after(() => adminApi.assign(id, Number(orgId))),
    /* Hủy phân công ca Đã phân công khi biết tổ chức không đi được: ca về Chờ xử lý, lý do gửi cho tổ chức bị hủy */
    unassign(id, reason) {
      if (!reason?.trim()) return 'Vui lòng nhập lý do hủy phân công.';
      return after(() => adminApi.unassign(id, reason.trim()));
    },
    /* Xác minh ca AI xếp KHONG: chọn mức thật + ghi chú xác minh bắt buộc, ca được gửi cho tổ chức */
    verifyReview(id, level, note) {
      if (!note?.trim()) return 'Vui lòng ghi lại thông tin đã xác minh với người gửi.';
      return after(() => adminApi.adjustLevel(id, level, note.trim()));
    },
    adjustSeverity: (id, level) => after(() => adminApi.adjustLevel(id, level)),
    /* Đã liên hệ người gửi, mức hiện tại phù hợp: gỡ cảnh báo "nên xác minh" mà không đổi mức */
    confirmVerification: (id) => after(() => adminApi.confirmVerification(id)),
    markInvalid(id, reason) {
      if (!reason.trim()) return 'Vui lòng nhập lý do.';
      return after(() => adminApi.markInvalid(id, reason.trim()));
    },
    /* Admin đã liên hệ và biết người dân an toàn (hoặc tổ chức đã cứu xong nhưng quên bấm): đóng ca Đã tiếp nhận */
    adminComplete(id, reason) {
      if (!reason.trim()) return 'Vui lòng nhập lý do.';
      return after(() => adminApi.completeSos(id, reason.trim()));
    },
    async candidates(id) {
      const r = await get(() => adminApi.candidates(id));
      return (r || []).map((x) => ({
        org: mOrgBrief(x.org), m: mMatching(x.matching), canTake: x.canTake,
        // Ca đang xử lý của đội trong bán kính 5 km quanh ca này; busy: nhận thêm ca này thì đội gần kín (>= 70% sức chứa)
        nearby: (x.nearbyCases || []).map((n) => ({ id: sid(n.id), code: n.code, dist: n.distanceM })),
        busy: !!x.busyIfAssigned, cap: x.maxConcurrentCases ?? null, radiusKm: x.serviceRadiusKm ?? null,
      }));
    },
    /* Ca lân cận trong 500 m (Admin), tính tại lúc xem nên có cả ca gửi sau. Lỗi: trả null để giao diện báo không tải được */
    async nearby(id) {
      const r = await get(() => adminApi.nearby(id));
      if (!r) return null;
      mutate((d) => r.forEach(({ incident: i }) => { if (i.citizen) upUser(d, { ...mUserSummary(i.citizen), phone: i.citizenPhone || undefined }); }));
      return r.map((x) => ({ ...mSos(x.incident), distFromCase: x.distanceM }));
    },
  };
}
