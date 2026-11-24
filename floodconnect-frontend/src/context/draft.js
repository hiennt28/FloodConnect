import { mCampaign, mComment, mPost, mUserSummary } from '@/services/api/mappers';

/* Trạng thái ban đầu và các hàm chạy trên bản nháp state (dùng trong mutate). */
export const empty = () => ({ session: null, users: [], orgs: [], posts: [], comments: [], sos: [], sosActive: null, campaigns: [], donations: [], notifications: [], profiles: {} });

/* ===== helpers chạy trên bản nháp state ===== */
export const clean = (o) => Object.fromEntries(Object.entries(o).filter(([, v]) => v !== undefined));
export const upBy = (arr, item) => { const i = arr.findIndex((x) => x.id === item.id); if (i >= 0) arr[i] = { ...arr[i], ...item }; else arr.push(item); };
export const upUser = (d, u) => { if (u?.id != null) upBy(d.users, clean(u)); };
export const putPost = (d, dto) => { upUser(d, mUserSummary(dto.author)); upBy(d.posts, mPost(dto)); };
export const putComment = (d, dto) => { upUser(d, mUserSummary(dto.author)); upUser(d, mUserSummary(dto.replyTo)); upBy(d.comments, mComment(dto)); };
export const putCampaign = (d, dto) => upBy(d.campaigns, mCampaign(dto));
export const isLocked = (u) => !!(u && u.lock && u.lock.until > Date.now());
export const errs = (map = {}) => ({ errors: map });

/* Tên field backend (kể cả dạng account.email) -> tên field trong form */
const FIELD_MAP = {
  fullName: 'name', confirmPassword: 'confirm', orgName: 'orgName', orgType: 'type', wardCode: 'address',
  streetAddress: 'address', latitude: 'location', longitude: 'location', websiteUrl: 'website',
  maxConcurrentCases: 'cases', serviceRadiusKm: 'radiusKm',
  /* lỗi hồ sơ giữ nguyên tên ô (documents, idFront, idBack, evidence), khớp DOC_SLOTS[ô].part */
};
export const mapErrors = (e) => Object.fromEntries(Object.entries(e || {}).map(([k, v]) => { const last = k.split('.').pop(); return [FIELD_MAP[last] || last, v]; }));

export const lockedMsg = 'Tài khoản của bạn đang bị khóa nên không thể thực hiện thao tác này.';
