/* Chuyển DTO của backend (id kiểu số, ENUM tiếng Anh) sang dạng dữ liệu mà giao diện đang dùng (id kiểu chuỗi). */
import { adminOrgLink, dispatchLink, MATCH_WEIGHTS, PROVINCES } from '@/utils';

export const sid = (x) => (x == null ? null : String(x));
export const ts = (x) => (x ? Date.parse(x) : null);
const norm = (s) => String(s || '').trim().toLowerCase();

const OP = { AVAILABLE: 'READY', BUSY: 'BUSY', INACTIVE: 'INACTIVE' };
export const OP_TO_API = { READY: 'AVAILABLE', BUSY: 'BUSY', INACTIVE: 'INACTIVE' };
const APPROVAL = { PENDING: 'PENDING', ACTIVE: 'APPROVED', LOCKED: 'APPROVED', REJECTED: 'REJECTED' };
const SOS_STATUS = { PENDING: 'PENDING', ASSIGNED: 'ASSIGNED', ACCEPTED: 'ACCEPTED', COMPLETED: 'DONE', CANCELLED: 'CANCELLED', INVALID: 'INVALID' };
export const SOS_STATUS_TO_API = { DONE: 'COMPLETED' };

/* Tên Tỉnh/Phường backend lưu dạng chữ; tra ngược mã để AddressPicker hiển thị đúng */
export function codesOf(provinceName, wardName) {
  const p = PROVINCES.find((x) => norm(x.name) === norm(provinceName));
  const w = p?.wards.find(([, n]) => norm(n) === norm(wardName));
  return { provinceCode: p?.code || '', wardCode: w?.[0] || '' };
}

export const mMedia = (m) => ({ id: m.id, url: m.url, name: '' }); // bài đăng chỉ đính kèm ảnh (JPEG/PNG/WebP)

/* ---------- người dùng ---------- */
export const mUserSummary = (u) => (u ? {
  id: sid(u.id), name: u.fullName, avatar: u.avatarUrl || null, role: u.role, orgName: u.organizationName || null,
} : null);

export const mProfile = (p) => ({
  id: sid(p.id), name: p.fullName, email: p.email, phone: p.phone, role: p.role, avatar: p.avatarUrl || null,
  emailVerified: p.emailVerified, active: true, status: p.status,
  gps: p.gpsPermission, lat: p.latitude ?? undefined, lng: p.longitude ?? undefined,
  createdAt: ts(p.createdAt) || Date.now(),
  /* Lý do khóa không có trong hồ sơ: người bị khóa nhận lý do qua thông báo và email lúc bị khóa */
  lock: p.locked && p.lockedUntil ? { until: ts(p.lockedUntil) } : null,
  orgId: p.organization ? sid(p.organization.id) : null,
  orgName: p.organization?.name || null,
  /* Admin gốc: hiện mục "Quản lý Admin" (backend vẫn tự kiểm tra quyền) */
  rootAdmin: !!p.rootAdmin,
});

/* ---------- tổ chức ---------- */
export function mOrg(o, extra = {}) {
  if (!o) return null;
  const { provinceCode, wardCode } = codesOf(o.province, o.ward);
  return {
    id: sid(o.id), name: o.name, type: o.orgType, province: o.province, district: o.district, ward: o.ward,
    street: o.streetAddress || '', provinceCode, wardCode,
    addressText: [o.streetAddress, o.ward, o.province].filter(Boolean).join(', '),
    description: o.description || '', lat: o.latitude, lng: o.longitude, serviceAreas: o.serviceAreas,
    approval: APPROVAL[o.status] || 'PENDING', backendStatus: o.status, locked: o.status === 'LOCKED',
    opStatus: OP[o.operatingStatus] || 'INACTIVE', rejectReason: o.rejectionReason || '',
    /* Giấy tờ lưu riêng tư: không có đường dẫn, mở bằng DocButton (API kèm token, chỉ Admin và chính tổ chức) */
    docs: (o.documents || []).map((d) => ({ id: d.id, slot: d.slot || null, label: d.docLabel, type: d.fileType })),
    website: o.websiteUrl || '',
    cases: o.maxConcurrentCases ?? null, radiusKm: o.serviceRadiusKm ?? null, // năng lực tiếp nhận (bắt buộc từ V10)
    ...extra,
  };
}
/* Tổ chức lấy từ API công khai (hồ sơ công khai, tìm kiếm): backend chỉ trả trụ sở tới Phường/Xã, Tỉnh, không có số nhà, tọa độ,
   giấy tờ. Chỉ mang các trường công khai, để khi gộp vào state (upBy) không xóa mất dữ liệu đầy đủ đã có, vd. tổ chức mở hồ sơ
   công khai của chính mình thì vẫn giữ số nhà và ghim trụ sở. */
export function mOrgPublic(o, extra = {}) {
  if (!o) return null;
  const { provinceCode, wardCode } = codesOf(o.province, o.ward);
  return {
    id: sid(o.id), name: o.name, type: o.orgType, province: o.province, ward: o.ward, provinceCode, wardCode,
    description: o.description || '', serviceAreas: o.serviceAreas,
    approval: APPROVAL[o.status] || 'PENDING', backendStatus: o.status, locked: o.status === 'LOCKED',
    opStatus: OP[o.operatingStatus] || 'INACTIVE', website: o.websiteUrl || '',
    ...extra,
  };
}
/* Khu vực trụ sở hiển thị công khai: "Phường X, Tỉnh Y" */
export const orgArea = (o) => [o?.ward, o?.province].filter(Boolean).join(', ');

export const mOrgBrief = (o) => (o ? {
  id: sid(o.id), name: o.name, type: o.orgType, opStatus: OP[o.operatingStatus] || 'INACTIVE', lat: o.latitude, lng: o.longitude, phone: o.phone || null,
} : null);

/* ---------- bài đăng / bình luận ---------- */
export const mPost = (p) => ({
  id: sid(p.id), authorId: sid(p.author?.id), content: p.content,
  images: (p.media || []).map(mMedia),
  /* Vị trí: địa chỉ do backend ghép (label) + mã xã, phần chi tiết để sửa bài điền lại. Bài cũ chỉ có tọa độ thì wardCode rỗng */
  location: p.latitude != null && p.longitude != null
    ? { lat: p.latitude, lng: p.longitude, label: p.locationName || '', wardCode: p.wardCode || '', detail: p.addressDetail || '' }
    : null,
  status: p.status, createdAt: ts(p.createdAt), updatedAt: p.edited ? (ts(p.updatedAt) || ts(p.createdAt)) : null,
  likeCount: p.likeCount, commentCount: p.commentCount, likedByMe: p.likedByMe,
});

export const mComment = (c) => ({
  id: sid(c.id), postId: sid(c.postId), parentId: sid(c.parentCommentId), authorId: sid(c.author?.id),
  replyToId: sid(c.replyTo?.id), content: c.content, status: c.deleted ? 'DELETED' : 'VISIBLE',
  createdAt: ts(c.createdAt), updatedAt: c.edited ? ts(c.createdAt) : null, replyCount: c.replyCount,
});

/* ---------- SOS ---------- */
export const mMatching = (m) => (m ? {
  /* Điểm = wL·L (vị trí) + wS·S (mô tả tương đồng) + wC·C (năng lực) + wW·W (tình trạng tiếp nhận).
     Dịch vụ AI (source = AI_SERVICE) đổi trọng số theo mức đầy đủ của tin SOS; quy tắc dự phòng (RULES) luôn 25/30/30/15.
     weights = null: AI loại tổ chức này (điểm 0, lý do ở reason) → hiển thị trọng số mặc định. */
  score: m.score, L: m.locationScore, S: m.semanticScore, C: m.capabilityScore ?? null, W: m.workloadScore,
  dist: m.distanceM, active: m.activeCases, refused: m.refusedCount, reason: m.reason,
  weights: m.weights || MATCH_WEIGHTS, byAi: m.source === 'AI_SERVICE',
} : null);

export function mSos(i) {
  const status = SOS_STATUS[i.status] || i.status;
  /* Mô tả là bắt buộc nên null nghĩa là người xem không có quyền xem chi tiết: khi đó không lấy tọa độ (đã làm tròn) thay cho địa chỉ */
  const detail = i.description != null;
  return {
    id: sid(i.id), code: i.code, citizenId: sid(i.citizen?.id), citizenPhone: i.citizenPhone || null,
    desc: i.description || '', lat: i.latitude, lng: i.longitude,
    address: i.address || (detail ? `${Number(i.latitude).toFixed(4)}, ${Number(i.longitude).toFixed(4)}` : ''),
    aiSeverity: i.aiDangerLevel, severity: i.dangerLevel || 'KHONG', needsVerify: !!i.needsVerification,
    /* Nhãn KHONG chỉ do AI gán: ca Chờ xử lý chờ Admin xác minh. Admin chọn mức khác thì ca được gửi cho tổ chức,
       hoặc đánh dấu Không hợp lệ (status INVALID, kèm lý do) để đóng ca */
    adminReview: i.dangerLevel === 'KHONG' && i.status === 'PENDING', invalid: i.status === 'INVALID',
    invalidReason: i.invalidReason || '', invalidAt: ts(i.invalidatedAt),
    /* Ai đã liên hệ xác minh: tên chỉ Admin nhận; thời điểm Admin luôn nhận, người khác chỉ nhận khi ca có ghi chú xác minh */
    verifiedBy: i.verifiedByName || null, verifiedAt: ts(i.verifiedAt),
    /* Ghi chú xác minh của Admin (ca AI xếp KHONG được xác minh): cùng quyền xem với mô tả, mô tả gốc giữ nguyên */
    verifyNote: i.verificationNote || '',
    reasons: i.aiReasons || [],
    weather: i.weather ? { rainMm: i.weather.rainMm, forecast: i.weather.forecast, alert: i.weather.alert, severe: i.weather.severe, source: i.weather.source } : null,
    status, orgId: sid(i.assignedOrg?.id), org: mOrgBrief(i.assignedOrg),
    createdAt: ts(i.createdAt), assignedAt: ts(i.assignedAt), acceptedAt: ts(i.acceptedAt), doneAt: ts(i.completedAt), updatedAt: null,
    /* Ai đưa ca sang Hoàn thành: ORG | CITIZEN (báo đã an toàn) | ADMIN; ghi chú/lý do; tên người bấm chỉ Admin nhận */
    doneBy: i.completedByRole || null, doneNote: i.completionNote || '', doneByName: i.completedByName || null,
    rejectLog: [], assignmentStatus: i.assignmentStatus,
    /* Lý do của lần phân công gần nhất (tổ chức từ chối, Admin hủy phân công, tổ chức bị khóa / chuyển Không hoạt động); chỉ Admin nhận */
    assignmentNote: i.assignmentNote || '',
    /* CANCELLED: phân công bị hủy không do lỗi tổ chức (Admin hủy, tổ chức bị khóa / chuyển Không hoạt động), Admin cần điều phối lại */
    needsReassign: status === 'PENDING' && ['REJECTED', 'TIMEOUT', 'CANCELLED'].includes(i.assignmentStatus),
    needsAttention: !!i.needsAttention, matching: mMatching(i.matching), distanceFromMe: i.distanceFromMeM ?? null, mine: !!i.mine,
  };
}

/* ---------- chiến dịch / ủng hộ ---------- */
const END_REASON = { EXPIRED: null, ORGANIZATION_ENDED: 'ORGANIZATION_ENDED', TARGET_REACHED: 'TARGET_REACHED', ACCOUNT_LOCKED: 'ACCOUNT_LOCKED', ADMIN_STOPPED: 'ADMIN_STOPPED' };

export const mCampaign = (c) => ({
  id: sid(c.id), orgId: sid(c.orgId), orgName: c.orgName, name: c.name, cover: c.avatarUrl || null,
  gallery: (c.media || []).map((m) => ({ id: m.id, kind: 'image', url: m.url })),
  purpose: c.purpose, area: c.supportArea, beneficiaries: c.targetAudience, orgInfo: c.orgInfo,
  target: c.targetAmount, raised: c.raisedAmount, bank: c.bankName || '', accountName: c.accountHolder, accountNo: c.accountNumber,
  start: ts(c.startDate), end: ts(c.endDate), status: c.status, endedReason: c.endReason ? END_REASON[c.endReason] ?? null : null,
  canDonate: c.canDonate, reportedByMe: c.reportedByMe, createdAt: ts(c.createdAt), tint: (Number(c.id) % 4) + 1,
});

export const mDonation = (d) => ({
  id: sid(d.id), campaignId: sid(d.campaignId), campaignName: d.campaignName, donorName: d.donorName, amount: d.amount,
  message: d.message || '', method: d.paymentMethod, txCode: d.transactionCode, status: d.status, createdAt: ts(d.createdAt),
  anonymous: d.donorName === 'Ẩn danh',
});

/* ---------- thông báo ---------- */
export function mNotif(n, role) {
  let link = null;
  const id = n.referenceId;
  /* Bài viết / chiến dịch bị báo cáo (Admin nhận): mở trang chi tiết báo cáo để kết luận */
  if (n.type === 'REPORT_NEW') {
    link = n.referenceType === 'POST' ? `/admin/post-reports/${id}` : n.referenceType === 'CAMPAIGN' ? `/admin/campaign-reports/${id}` : null;
    return { id: sid(n.id), text: n.content || n.title, title: n.title, link, read: n.read, createdAt: ts(n.createdAt) };
  }
  switch (n.referenceType) {
    /* Bài bị gỡ: không còn trang để mở (thông báo cũ vẫn kèm mã bài). Bình luận/phản hồi: kèm mã bình luận để cuộn tới đúng chỗ */
    case 'POST': link = n.type === 'POST_REMOVED' ? null : `/posts/${id}${n.subReferenceId ? `?comment=${n.subReferenceId}` : ''}`; break;
    /* Khóa / mở khóa tài khoản: người bị khóa xem trang cá nhân (có dải báo lý do, thời hạn); Admin mở hồ sơ người dân đó */
    case 'USER': link = n.type === 'ACCOUNT' ? (role === 'ADMIN' ? `/admin/citizens?user=${id}` : '/profile') : `/users/${id}`; break;
    case 'CAMPAIGN': link = `/campaigns/${id}`; break;
    case 'DONATION': link = `/my-donations?donation=${id}`; break;
    /* Kèm mã ca: trang đích tự mở đúng ca (Admin: hộp chi tiết; tổ chức: đúng tab + bảng chi tiết; người dân: làm nổi bật thẻ ca) */
    case 'INCIDENT': link = role === 'ADMIN' ? dispatchLink('ADMIN', id) : role === 'RESCUE_ORG' ? dispatchLink('ORG', id) : `/sos?case=${id}`; break;
    case 'ORG': link = role === 'ADMIN' ? adminOrgLink(id) : '/profile'; break;
    default: link = null;
  }
  return { id: sid(n.id), text: n.content || n.title, title: n.title, link, read: n.read, createdAt: ts(n.createdAt) };
}
