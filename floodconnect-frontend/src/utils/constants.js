export const MIN = 60 * 1000;
export const HOUR = 60 * MIN;
export const DAY = 24 * HOUR;

export const ROLE_LABEL = { CITIZEN: 'Người dân', RESCUE_ORG: 'Tổ chức', ADMIN: 'Quản trị viên' };

export const SOS_STATUS = {
  PENDING: { label: 'Chờ xử lý', tone: 'yellow' },
  ASSIGNED: { label: 'Đã phân công', tone: 'orange' },
  ACCEPTED: { label: 'Đã tiếp nhận', tone: 'blue' },
  DONE: { label: 'Hoàn thành', tone: 'green' },
  CANCELLED: { label: 'Đã hủy', tone: 'gray' },
  /* Admin đã xác minh và kết luận không phải yêu cầu cứu hộ (trạng thái kết thúc) */
  INVALID: { label: 'Không hợp lệ', tone: 'gray' },
};
export const ACTIVE_SOS = ['PENDING', 'ASSIGNED', 'ACCEPTED'];
/* Ai đưa ca sang Hoàn thành (backend trả ORG cho ca cũ chưa ghi) */
export const COMPLETED_BY = {
  ORG: 'Tổ chức cứu hộ xác nhận hoàn thành',
  CITIZEN: 'Người dân báo đã an toàn',
  ADMIN: 'Admin xác nhận hoàn thành',
};
/* Số khẩn cấp quốc gia (miễn phí, 24/7). Chỉ dùng số thật: người gặp nạn bấm vào là gọi ngay. */
export const HOTLINE = { label: 'Cứu nạn, cứu hộ', number: '112' };
export const EMERGENCY_NUMBERS = [
  HOTLINE,
  { label: 'Cứu hỏa', number: '114' },
  { label: 'Cấp cứu y tế', number: '115' },
];
/* Cảnh báo ca tương tự: bán kính 500 m, gửi trong 30 phút gần nhất (theo Mô tả tóm tắt bài toán) */
export const DUP_RADIUS_M = 500;
export const DUP_WINDOW_MIN = 30;
/* Quá thời gian này mà chưa tổ chức nào nhận: hệ thống thông báo Admin điều phối (không tự đổi mức) */
export const PENDING_TIMEOUT_MIN = 15;
/* Tổ chức không phản hồi ca được phân công sau mốc này: Admin được điều phối lại */
export const ASSIGN_TIMEOUT_MIN = 5;
/* Quá hạn phản hồi thêm ngần này phút mà Admin chưa điều phối lại: ca tự về Chờ xử lý (tổng 15 phút kể từ lúc giao).
   Phải khớp app.rescue.assign-release-minutes (ASSIGN_RELEASE_MINUTES) của backend */
export const ASSIGN_RELEASE_MIN = 10;
/* Ca SOS "trong khu vực" của tổ chức: trong bán kính hoạt động tổ chức tự khai (backend tính, không còn bán kính chung 50 km) */

export const SEVERITY = {
  KHAN_CAP: { label: 'Khẩn cấp', tone: 'red', w: 4, color: '#b42318' },
  CAO: { label: 'Cao', tone: 'orange', w: 3, color: '#c2410c' },
  TRUNG_BINH: { label: 'Trung bình', tone: 'yellow', w: 2, color: '#94660a' },
  THAP: { label: 'Thấp', tone: 'green', w: 1, color: '#2e7d4f' },
  KHONG: { label: 'Không', tone: 'gray', w: 0, color: '#66757a' },
};

/* desc: ý nghĩa từng trạng thái, khớp backend (OrgService.setOperatingStatus, RescueService.releaseAssigned) */
export const ORG_STATUS = {
  READY: { label: 'Sẵn sàng', tone: 'green', desc: 'Nhận ca đề xuất, được Admin phân công và nhận thông báo ca mới trong khu vực.' },
  BUSY: { label: 'Đang bận', tone: 'orange', desc: 'Tạm ngừng nhận ca mới. Các ca đã được phân công vẫn giữ để bạn xác nhận và xử lý tiếp.' },
  INACTIVE: { label: 'Không hoạt động', tone: 'gray', desc: 'Ngừng nhận ca. Ca được phân công nhưng chưa xác nhận sẽ trả lại cho Admin điều phối; ca đang cứu hộ vẫn phải hoàn thành.' },
};

export const ORG_TYPES = {
  STATE: {
    label: 'Cơ quan nhà nước',
    hint: 'Cứu hộ khẩn cấp, kỹ thuật cao, chỉ đạo điều phối vĩ mô',
    docLabel: 'Văn bản / Quyết định thành lập',
    docHint: 'Quyết định thành lập, văn bản chỉ định/bổ nhiệm hoặc văn bản xác nhận chức năng cứu hộ từ cơ quan cấp trên.',
  },
  SOCIAL: {
    label: 'Tổ chức xã hội',
    hint: 'Hội Chữ thập đỏ, Đoàn Thanh niên, Hội Phụ nữ...',
    docLabel: 'Quyết định thành lập / Điều lệ / Giấy phép hoạt động',
    docHint: 'Quyết định thành lập hội/đoàn thể, giấy phép hoạt động hoặc văn bản xác nhận của tổ chức cấp trên.',
  },
  LOGISTICS: {
    label: 'Đơn vị hỗ trợ hậu cần',
    hint: 'Phương tiện vận tải, vật tư, kho bãi, viễn thông',
    docLabel: 'Giấy chứng nhận Đăng ký kinh doanh',
    docHint: 'Giấy chứng nhận đăng ký doanh nghiệp của đơn vị cung cấp dịch vụ hậu cần.',
  },
  VOLUNTEER: {
    label: 'Nhóm thiện nguyện/cứu trợ tự phát',
    hint: 'Nhóm tình nguyện tự tổ chức',
    docLabel: 'CCCD/CMND hai mặt của trưởng nhóm',
    docHint: 'Tải mặt trước và mặt sau vào hai mục riêng. Có thể thêm minh chứng hoạt động; link fanpage nhập ở ô "Trang web / fanpage".',
  },
};

/* Ô hồ sơ xác minh, khớp OrgDocSlot ở backend. part: tên phần multipart gửi lên, cũng là khóa lỗi theo trường.
   Nhãn và hướng dẫn của ô MAIN lấy theo loại tổ chức (ORG_TYPES.docLabel, docHint). */
export const DOC_SLOTS = {
  MAIN: { part: 'documents', multiple: true },
  ID_FRONT: { part: 'idFront', multiple: false, label: 'CCCD/CMND mặt trước của trưởng nhóm', hint: 'Ảnh chụp rõ nét hoặc bản scan mặt trước, 1 tệp.' },
  ID_BACK: { part: 'idBack', multiple: false, label: 'CCCD/CMND mặt sau của trưởng nhóm', hint: 'Ảnh chụp rõ nét hoặc bản scan mặt sau, 1 tệp.' },
  EVIDENCE: { part: 'evidence', multiple: true, label: 'Minh chứng hoạt động', hint: 'Không bắt buộc. Ảnh hoạt động, giấy xác nhận của địa phương, bài báo viết về tổ chức…' },
};

export const POST_REPORT_REASONS = ['Tin giả', 'Nội dung không phù hợp', 'Spam', 'Quấy rối', 'Khác'];
export const CAMPAIGN_REPORT_REASONS = {
  PROFITEERING: 'Có dấu hiệu trục lợi',
  MISINFORMATION: 'Có dấu hiệu thông tin sai lệch',
  OTHER: 'Lý do khác',
};
export const ENDED_REASON = {
  TIME_UP: 'Hết thời gian',
  TARGET_REACHED: 'Đạt mục tiêu',
  ORGANIZATION_ENDED: 'Tổ chức chủ động kết thúc',
  ACCOUNT_LOCKED: 'Tài khoản tổ chức bị khóa',
  ADMIN_STOPPED: 'Admin dừng chiến dịch',
};

export function campaignStatus(c, now = Date.now()) {
  if (c.endedReason || c.status === 'ENDED') return 'ENDED';
  if (now < c.start) return 'UPCOMING';
  if (now > c.end) return 'ENDED';
  return 'ACTIVE';
}
export const CAMPAIGN_STATUS = {
  UPCOMING: { label: 'Sắp diễn ra', tone: 'yellow' },
  ACTIVE: { label: 'Đang diễn ra', tone: 'blue' },
  ENDED: { label: 'Đã kết thúc', tone: 'gray' },
};
/* Ngân hàng nhận tiền của chiến dịch (lưu đúng tên hiển thị). OTHER_BANK: tổ chức tự gõ tên ngân hàng không có trong danh sách */
export const BANKS = ['Vietcombank', 'VietinBank', 'BIDV', 'Agribank', 'MB Bank', 'Techcombank', 'ACB', 'VPBank', 'TPBank', 'Sacombank',
  'HDBank', 'VIB', 'SHB', 'SeABank', 'OCB', 'MSB', 'Eximbank', 'LPBank', 'Nam A Bank', 'Bac A Bank', 'PVcomBank', 'Kienlongbank'];
export const OTHER_BANK = '__OTHER__';

export const DONATION_STATUS = {
  PENDING: { label: 'Đang chờ kết quả thanh toán', tone: 'yellow' },
  SUCCESS: { label: 'Thanh toán thành công', tone: 'green' },
  FAILED: { label: 'Thanh toán thất bại', tone: 'red' },
  EXPIRED: { label: 'Hết hạn thanh toán', tone: 'gray' }, // bỏ dở, quá 30 phút cổng chưa báo kết quả
};

/* Lý do báo cáo bài đăng: nhãn hiển thị <-> mã backend */
export const POST_REASON_CODE = { 'Tin giả': 'FAKE_NEWS', 'Nội dung không phù hợp': 'INAPPROPRIATE', 'Spam': 'SPAM', 'Quấy rối': 'HARASSMENT', 'Khác': 'OTHER' };
export const POST_REASON_LABEL = Object.fromEntries(Object.entries(POST_REASON_CODE).map(([k, v]) => [v, k]));
export const CAMPAIGN_REASON_LABEL = { PROFITEERING: 'Có dấu hiệu trục lợi', MISINFORMATION: 'Có dấu hiệu thông tin sai lệch', OTHER: 'Lý do khác' };
