import { jsonPart, tokens } from '@/services/http';
import { authApi } from '@/services/api';
import { mProfile, sid } from '@/services/api/mappers';
import {
  DOC_SLOTS, docSlotLabel, docSlotsOf, EMAIL_RULE, PASSWORD_CHARS_MSG, PASSWORD_CHARS_RULE, PASSWORD_MSG, PASSWORD_RULE, PHONE_RULE, provinceName, requiredDocSlots, validateCoords,
  orgCapacityErrors, orgDescriptionError, wardName, WEBSITE_MSG, WEBSITE_RULE,
} from '@/utils';
import { empty, upUser, errs, mapErrors } from '../draft';

/** Xác thực: đăng nhập/đăng xuất, đăng ký người dân và tổ chức, xác thực email, quên mật khẩu. */
export function createAuthActions({ mutate, refresh, run }) {
  return {
    async login(id, password) {
      if (password && !PASSWORD_CHARS_RULE(password)) return PASSWORD_CHARS_MSG; // không gửi lên, không tốn một lần thử
      try {
        const r = await authApi.login(id.trim(), password);
        tokens.set(r.accessToken, r.refreshToken);
        mutate((d) => { upUser(d, mProfile(r.user)); d.session = { userId: sid(r.user.id) }; });
        await refresh();
        return null;
      } catch (e) {
        if (e.code === 'EMAIL_NOT_VERIFIED') return { unverified: e.errors?.email || id.trim() }; // backend trả email kể cả khi đăng nhập bằng SĐT
        if (e.code === 'LOGIN_LOCKED') {
          // backend trả số giây còn lại; nếu là bản backend cũ thì suy ra từ chữ "N phút" trong thông báo
          const sec = Number(e.errors?.retryAfterSeconds) || (Number(/(\d+)\s*phút/.exec(e.message)?.[1]) || 5) * 60;
          return { locked: sec };
        }
        return e.message;
      }
    },
    logout() {
      const t = tokens.get();
      if (t?.refresh) authApi.logout(t.refresh).catch(() => {});
      tokens.clear();
      mutate((d) => { Object.assign(d, empty()); });
    },
    async registerCitizen(f) {
      try {
        await authApi.register({
          fullName: f.name.trim(), email: f.email.trim(), phone: f.phone, password: f.password, confirmPassword: f.confirm,
        });
        return { email: f.email.trim() };
      } catch (e) {
        if (e.errors) return errs(mapErrors(e.errors));
        return errs({ email: e.message });
      }
    },
    async registerOrg(acc, o) {
      const e = {};
      if (!acc.name.trim()) e.name = 'Vui lòng nhập họ tên.';
      if (!EMAIL_RULE(acc.email)) e.email = 'Email không hợp lệ.';
      if (!PHONE_RULE(acc.phone)) e.phone = 'Số điện thoại chỉ gồm 9–11 chữ số.';
      if (!PASSWORD_RULE(acc.password)) e.password = PASSWORD_MSG;
      if (acc.password !== acc.confirm) e.confirm = 'Mật khẩu xác nhận không khớp.';
      if (!o.name.trim()) e.orgName = 'Vui lòng nhập tên tổ chức.';
      if (!o.type) e.type = 'Vui lòng chọn loại tổ chức.';
      if (!o.provinceCode || !o.wardCode || !o.street.trim()) e.address = 'Vui lòng nhập đủ địa chỉ hoạt động.';
      else if (!wardName(o.provinceCode, o.wardCode)) e.address = 'Phường/Xã không thuộc Tỉnh/Thành phố đã chọn.';
      // Ô hồ sơ phụ thuộc loại tổ chức: chưa chọn loại thì phần hồ sơ chưa hiện nên chưa báo lỗi ô nào
      if (o.type) requiredDocSlots(o.type).forEach((s) => { if (!o.docs[s]?.length) e[DOC_SLOTS[s].part] = `Vui lòng tải lên "${docSlotLabel(o.type, s)}".`; });
      const website = (o.website || '').trim();
      if (website && !WEBSITE_RULE(website)) e.website = WEBSITE_MSG;
      const descriptionError = orgDescriptionError(o.description); // bắt buộc: AI Matching suy năng lực tổ chức từ mô tả
      if (descriptionError) e.description = descriptionError;
      Object.assign(e, orgCapacityErrors(o.cases, o.radiusKm)); // bắt buộc: AI dùng cho điểm tải W và vùng phục vụ
      if (!o.point) e.location = 'Vui lòng đặt ghim vị trí trụ sở trên bản đồ.';
      else { const ce = validateCoords(o.point.lat, o.point.lng); if (ce) e.location = ce; }
      if (Object.keys(e).length) return errs(e);
      const fd = new FormData();
      fd.append('data', jsonPart({
        account: { fullName: acc.name.trim(), email: acc.email.trim(), phone: acc.phone, password: acc.password, confirmPassword: acc.confirm },
        // Mã xã: backend tự lấy tên tỉnh, tên xã. Tọa độ trụ sở = ghim người đại diện đặt (backend kiểm tra ghim nằm trong xã đã chọn)
        orgName: o.name.trim(), orgType: o.type, wardCode: o.wardCode, streetAddress: o.street.trim(), description: o.description.trim(),
        latitude: o.point.lat, longitude: o.point.lng, serviceAreas: provinceName(o.provinceCode), websiteUrl: website || null,
        maxConcurrentCases: Number(o.cases), serviceRadiusKm: Number(o.radiusKm),
      }));
      docSlotsOf(o.type).forEach((s) => o.docs[s].forEach((f) => fd.append(DOC_SLOTS[s].part, f)));
      try {
        await authApi.registerOrg(fd);
        return { email: acc.email.trim() };
      } catch (ex) {
        if (ex.errors) return errs(mapErrors(ex.errors));
        return errs({ orgName: ex.message });
      }
    },
    verifyEmail: (email, code) => run(() => authApi.verifyEmail(email.trim(), code)),
    resendVerification: (email) => run(() => authApi.resendVerification(email)),
    requestReset: (email) => run(() => authApi.forgotPassword(email)),
    resetPassword: (email, code, pw, confirm) => run(() => authApi.resetPassword(email.trim(), code, pw, confirm)),
  };
}
