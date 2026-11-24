import { jsonPart } from '@/services/http';
import { campaignApi } from '@/services/api';
import { putCampaign, isLocked, lockedMsg } from '../draft';

const validateCampaign = (f, partial, creating = false) => {
  if (!f.name?.trim()) return 'Vui lòng nhập tên chiến dịch.';
  if (f.name.length > 200) return 'Tên chiến dịch tối đa 200 ký tự.';
  if (partial) return null;
  if (creating && !f.coverFile) return 'Vui lòng chọn ảnh đại diện cho chiến dịch.';
  if (!f.purpose.trim() || !f.area.trim() || !f.beneficiaries.trim()) return 'Vui lòng nhập đủ mục đích, khu vực và đối tượng hỗ trợ.';
  if (!Number.isInteger(Number(f.target)) || Number(f.target) <= 0) return 'Số tiền mục tiêu phải là số nguyên lớn hơn 0.';
  if (!f.bank?.trim()) return 'Vui lòng chọn ngân hàng nhận tiền.';
  if (f.bank.trim().length > 100) return 'Tên ngân hàng tối đa 100 ký tự.';
  if (!f.accountName.trim() || !/^\d{6,20}$/.test(f.accountNo)) return 'Thông tin tài khoản nhận tiền không hợp lệ (số tài khoản 6–20 chữ số).';
  if (!f.start || !f.end) return 'Vui lòng chọn thời gian bắt đầu và kết thúc.';
  if (f.end <= f.start) return 'Thời gian kết thúc phải sau thời gian bắt đầu.';
  return null;
};
const iso = (ms) => new Date(ms).toISOString();

/** Chiến dịch gây quỹ: tạo/sửa/kết thúc/gia hạn (tổ chức), ủng hộ, báo cáo. */
export function createCampaignActions({ mutate, refresh, run, ref, me }) {
  return {
    async create(f) {
      if (isLocked(me)) return lockedMsg;
      const err = validateCampaign(f, false, true);
      if (err) return err;
      return run(async () => {
        const fd = new FormData();
        fd.append('data', jsonPart({
          name: f.name.trim(), purpose: f.purpose.trim(), supportArea: f.area.trim(), targetAudience: f.beneficiaries.trim(), orgInfo: f.orgInfo.trim(),
          targetAmount: Number(f.target), bankName: f.bank.trim(), accountHolder: f.accountName.trim(), accountNumber: f.accountNo,
          startDate: iso(f.start), endDate: iso(f.end),
        }));
        fd.append('avatar', f.coverFile);
        (f.galleryNew || []).forEach((g) => fd.append('images', g));
        const c = await campaignApi.create(fd);
        mutate((d) => { putCampaign(d, c); });
      });
    },
    async edit(id, f) {
      const c0 = ref.current.campaigns.find((x) => x.id === id);
      if (!c0) return 'Không tìm thấy chiến dịch.';
      const st = c0.status;
      const err = validateCampaign(f, st === 'ACTIVE');
      if (err) return err;
      return run(async () => {
        const data = { name: f.name.trim(), purpose: f.purpose?.trim(), supportArea: f.area?.trim(), targetAudience: f.beneficiaries?.trim(), orgInfo: f.orgInfo?.trim() };
        if (st === 'UPCOMING') Object.assign(data, { targetAmount: Number(f.target), bankName: f.bank.trim(), accountHolder: f.accountName.trim(), accountNumber: f.accountNo, startDate: iso(f.start), endDate: iso(f.end) });
        else {
          if (f.end && f.end !== c0.end) data.endDate = iso(f.end);
          if (!c0.bank && f.bank?.trim()) data.bankName = f.bank.trim(); // chiến dịch tạo trước khi có trường ngân hàng: bổ sung một lần
        }
        let c = await campaignApi.update(id, data);
        if (f.coverFile) { const fd = new FormData(); fd.append('file', f.coverFile); c = await campaignApi.setAvatar(id, fd); }
        for (const mid of f.galleryRemove || []) await campaignApi.removeImage(id, mid);
        if ((f.galleryNew || []).length) { const fd = new FormData(); f.galleryNew.forEach((g) => fd.append('images', g)); c = await campaignApi.addImages(id, fd); }
        else if ((f.galleryRemove || []).length) c = await campaignApi.get(id);
        mutate((d) => { putCampaign(d, c); });
      });
    },
    end(id) {
      return run(async () => { const c = await campaignApi.end(id); mutate((d) => { putCampaign(d, c); }); });
    },
    extend(id, newEnd) {
      if (newEnd <= Date.now()) return 'Thời gian kết thúc mới phải lớn hơn thời điểm hiện tại.';
      return run(async () => { const c = await campaignApi.extend(id, iso(newEnd)); mutate((d) => { putCampaign(d, c); }); });
    },
    /* Khởi tạo khoản ủng hộ PENDING; trả về mã giao dịch để mô phỏng cổng thanh toán */
    /* Chỉ thanh toán qua VNPay: trên trang VNPay người ủng hộ tự chọn ngân hàng, quét QR hoặc nhập thẻ */
    async startDonation(campaignId, { amount, message, anonymous }) {
      const a = Number(amount);
      if (!Number.isInteger(a) || a <= 0) return { error: 'Số tiền ủng hộ phải là số nguyên lớn hơn 0.' };
      try {
        const r = await campaignApi.donate(campaignId, { amount: a, message: message.trim(), paymentMethod: 'VNPAY', anonymous: !!anonymous });
        return { id: r.transactionCode, amount: a, paymentUrl: r.paymentUrl, mock: !!r.mock };
      } catch (e) { return { error: e.message }; }
    },
    /* Mô phỏng callback (IPN) của cổng thanh toán: backend cập nhật khoản ủng hộ và tổng tiền chiến dịch */
    async completeDonation(txCode, success, amount) {
      const e = await run(() => campaignApi.paymentCallback({ transactionCode: txCode, success, gatewayRef: `MOCK-${Date.now()}`, amount }));
      await refresh();
      return e;
    },
    report(campaignId, reason, desc) {
      if (isLocked(me)) return lockedMsg;
      if (reason === 'OTHER' && !desc.trim()) return 'Vui lòng mô tả khi chọn "Lý do khác".';
      return run(async () => {
        await campaignApi.report(campaignId, { reason, description: desc.trim() });
        mutate((d) => { const c = d.campaigns.find((x) => x.id === campaignId); if (c) c.reportedByMe = true; });
      });
    },
  };
}
