import { jsonPart } from '@/services/http';
import { orgApi, userApi } from '@/services/api';
import { mOrg, mProfile } from '@/services/api/mappers';
import { DOC_SLOTS, docSlotsOf, orgCapacityErrors, orgDescriptionError, PASSWORD_CHARS_MSG, PASSWORD_CHARS_RULE, PASSWORD_MSG, PASSWORD_RULE, PHONE_RULE, provinceName, wardName, WEBSITE_MSG, WEBSITE_RULE } from '@/utils';
import { upBy, upUser } from '../draft';

/** Hồ sơ cá nhân: cập nhật thông tin, ảnh đại diện, mật khẩu, quyền GPS; tổ chức gửi lại hồ sơ. */
export function createProfileActions({ mutate, run, myOrg }) {
  return {
    async update({ name, phone }) {
      if (!name.trim()) return 'Họ tên không được để trống.';
      if (!PHONE_RULE(phone)) return 'Số điện thoại chỉ gồm 9–11 chữ số.';
      return run(async () => {
        const p = await userApi.updateMe({ fullName: name.trim(), phone });
        mutate((d) => { upUser(d, mProfile(p)); });
      });
    },
    /* Đổi ảnh đại diện (với tổ chức là logo): tải lên ngay khi chọn, không chờ nút "Lưu thay đổi" */
    async updateAvatar(file) {
      if (!file) return 'Vui lòng chọn ảnh.';
      if (!['image/jpeg', 'image/png'].includes(file.type)) return 'Ảnh đại diện chỉ nhận JPG/PNG.';
      if (file.size > 5 * 1024 * 1024) return 'Ảnh đại diện tối đa 5 MB.';
      return run(async () => {
        const fd = new FormData();
        fd.append('file', file);
        const p = await userApi.updateAvatar(fd);
        mutate((d) => { upUser(d, mProfile(p)); });
      });
    },
    /* Xóa ảnh đại diện (tổ chức: logo): giao diện quay về ảnh mặc định là chữ cái đầu của tên */
    removeAvatar() {
      return run(async () => {
        const p = await userApi.removeAvatar();
        mutate((d) => { upUser(d, mProfile(p)); });
      });
    },
    changePassword(cur, pw, confirm) {
      if (cur && !PASSWORD_CHARS_RULE(cur)) return Promise.resolve(PASSWORD_CHARS_MSG);
      if (!PASSWORD_RULE(pw)) return Promise.resolve(PASSWORD_MSG);
      return run(() => userApi.changePassword({ currentPassword: cur, newPassword: pw, confirmPassword: confirm }));
    },
    setGps(status, lat, lng) {
      return run(async () => {
        const p = await userApi.updateGps({ permission: status, latitude: lat ?? null, longitude: lng ?? null });
        mutate((d) => { upUser(d, mProfile(p)); });
      });
    },
    /* Tổ chức đã duyệt sửa thông tin: mô tả, trang web, địa chỉ và ghim trụ sở. Tên, loại tổ chức, giấy tờ không sửa được.
       Trả null nếu thành công, chuỗi lỗi nếu thất bại. */
    async updateOrg(c) {
      if (!c.street?.trim()) return 'Vui lòng nhập thôn/xóm, số nhà, đường.';
      if (c.street.trim().length > 255) return 'Thôn/xóm, số nhà, đường tối đa 255 ký tự.';
      if (!wardName(c.provinceCode, c.wardCode)) return 'Vui lòng chọn Tỉnh/Thành phố và Phường/Xã hợp lệ.';
      if (!c.point) return 'Vui lòng đặt ghim vị trí trụ sở trên bản đồ.';
      const descriptionError = orgDescriptionError(c.description); // bắt buộc, không xóa trắng: AI Matching dùng để suy năng lực
      if (descriptionError) return descriptionError;
      const capacityError = Object.values(orgCapacityErrors(c.cases, c.radiusKm))[0];
      if (capacityError) return capacityError;
      const website = (c.website || '').trim();
      if (website && !WEBSITE_RULE(website)) return WEBSITE_MSG;
      try {
        const o = await orgApi.update({
          description: c.description.trim(), websiteUrl: website || null, // trang web để trống: xóa
          wardCode: c.wardCode, latitude: c.point.lat, longitude: c.point.lng, streetAddress: c.street.trim(),
          maxConcurrentCases: Number(c.cases), serviceRadiusKm: Number(c.radiusKm),
        });
        mutate((d) => { upBy(d.orgs, mOrg(o, { userId: d.session.userId })); });
        return null;
      } catch (e) {
        return e.errors ? Object.values(e.errors).join(' ') : e.message; // vd. ghim nằm ngoài phường/xã đã chọn
      }
    },
    /* Tổ chức bị từ chối sửa hồ sơ và gửi lại (backend chỉ cho sửa thông tin tổ chức ở bước này) */
    async resubmitOrg(c) {
      if (!c.name.trim() || !c.street.trim() || !wardName(c.provinceCode, c.wardCode)) return 'Vui lòng nhập đủ tên và địa chỉ hoạt động hợp lệ.';
      if (!c.point) return 'Vui lòng đặt ghim vị trí trụ sở trên bản đồ.';
      const descriptionError = orgDescriptionError(c.description); // bắt buộc (AI Matching suy năng lực từ mô tả)
      if (descriptionError) return descriptionError;
      const capacityError = Object.values(orgCapacityErrors(c.cases, c.radiusKm))[0];
      if (capacityError) return capacityError;
      const website = (c.website || '').trim();
      if (website && !WEBSITE_RULE(website)) return WEBSITE_MSG;
      const fd = new FormData();
      fd.append('data', jsonPart({
        // Mã xã + ghim trụ sở: backend kiểm tra ghim nằm trong xã đã chọn (đổi xã mà quên dời ghim sẽ bị báo lỗi)
        orgName: c.name.trim(), orgType: myOrg.type, wardCode: c.wardCode, latitude: c.point.lat, longitude: c.point.lng,
        streetAddress: c.street.trim(), description: c.description.trim(), serviceAreas: provinceName(c.provinceCode),
        websiteUrl: website, // chuỗi rỗng: xóa trang web cũ
        maxConcurrentCases: Number(c.cases), serviceRadiusKm: Number(c.radiusKm),
      }));
      // Chỉ gửi ô có tệp mới; ô để trống giữ tệp đã nộp
      docSlotsOf(myOrg.type).forEach((s) => (c.docs?.[s] || []).forEach((f) => fd.append(DOC_SLOTS[s].part, f)));
      try {
        const o = await orgApi.resubmit(fd);
        mutate((d) => { upBy(d.orgs, mOrg(o, { userId: d.session.userId })); });
        return null;
      } catch (e) {
        return e.errors ? Object.values(e.errors).join(' ') : e.message; // lỗi theo ô hồ sơ, ví dụ thiếu CCCD mặt sau
      }
    },
  };
}
