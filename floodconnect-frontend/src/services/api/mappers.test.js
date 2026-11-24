import { describe, expect, it } from 'vitest';
import { mMatching, mNotif, mOrg, mOrgBrief, mOrgPublic, mPost, mProfile, mSos, mUserSummary, orgArea, sid } from './mappers';
import { upBy } from '@/context/draft';

describe('mappers: DTO backend -> dữ liệu giao diện', () => {
  it('id luôn là chuỗi, null giữ null', () => {
    expect(sid(5)).toBe('5');
    expect(sid(null)).toBeNull();
    expect(mUserSummary(null)).toBeNull();
    expect(mUserSummary({ id: 3, fullName: 'An', role: 'CITIZEN' })).toEqual({ id: '3', name: 'An', avatar: null, role: 'CITIZEN', orgName: null });
  });
  it('hồ sơ: trạng thái khóa và tổ chức', () => {
    const p = mProfile({ id: 1, fullName: 'A', locked: true, lockedUntil: '2030-01-01T00:00:00Z', organization: { id: 9, name: 'Đội X' }, createdAt: '2026-01-01T00:00:00Z' });
    expect(p.lock.until).toBe(Date.parse('2030-01-01T00:00:00Z'));
    expect(p).toMatchObject({ orgId: '9', orgName: 'Đội X', rootAdmin: false });
    expect(mProfile({ id: 2, fullName: 'Quản trị viên', role: 'ADMIN', rootAdmin: true }).rootAdmin).toBe(true); // hiện mục Quản lý Admin
  });
  it('tổ chức: trạng thái duyệt/hoạt động và địa chỉ', () => {
    const o = mOrg({ id: 2, name: 'Đội', status: 'LOCKED', operatingStatus: 'AVAILABLE', streetAddress: '1 Phố A', ward: 'Phường B', province: 'Hà Nội' });
    expect(o).toMatchObject({ id: '2', locked: true, opStatus: 'READY', addressText: '1 Phố A, Phường B, Hà Nội' });
    expect(mOrgBrief({ id: 2, operatingStatus: 'UNKNOWN' }).opStatus).toBe('INACTIVE');
    const v = mOrg({ id: 3, orgType: 'VOLUNTEER', websiteUrl: 'https://fb.com/a', documents: [{ id: 1, slot: 'ID_FRONT', docLabel: 'CCCD mặt trước' }, { id: 2, docLabel: 'Hồ sơ cũ' }] });
    expect(v.website).toBe('https://fb.com/a');
    expect(v.docs.map((d) => d.slot)).toEqual(['ID_FRONT', null]); // tệp nộp trước khi tách ô không có slot
  });
  it('bài đăng: media, vị trí, thời điểm sửa', () => {
    const p = mPost({ id: 7, author: { id: 1 }, content: 'c', media: [{ id: 1, type: 'image/png', url: 'u' }], latitude: 21, longitude: 105,
      createdAt: '2026-01-01T00:00:00Z', edited: false });
    expect(p.images[0]).toEqual({ id: 1, url: 'u', name: '' });
    expect(p.location).toEqual({ lat: 21, lng: 105, label: '', wardCode: '', detail: '' });
    expect(p.updatedAt).toBeNull();
  });
  it('ca SOS: không có quyền xem chi tiết thì không lấy tọa độ thay địa chỉ; tổ chức dừng nhận ca thì cần điều phối lại', () => {
    const base = { id: 4, code: 'SOS-1004', latitude: 16.05, longitude: 108.2, dangerLevel: 'CAO', description: null, address: null };
    expect(mSos({ ...base, status: 'PENDING', assignmentStatus: 'CANCELLED' })).toMatchObject({ desc: '', address: '', needsReassign: true });
    // người có quyền xem chi tiết, ca không nhập địa chỉ: hiện tọa độ để còn tìm được chỗ
    expect(mSos({ ...base, description: 'Nhà ngập', status: 'PENDING' }).address).toBe('16.0500, 108.2000');
    expect(mSos({ ...base, status: 'CANCELLED', assignmentStatus: 'CANCELLED' }).needsReassign).toBe(false); // người dân tự hủy ca
    // lý do lần phân công gần nhất (chỉ Admin nhận): giữ nguyên để hiện đúng ai hủy / vì sao; không có thì chuỗi rỗng
    expect(mSos({ ...base, status: 'PENDING', assignmentStatus: 'CANCELLED', assignmentNote: 'Admin hủy phân công: Đội không có xuồng' }).assignmentNote)
      .toBe('Admin hủy phân công: Đội không có xuồng');
    expect(mSos({ ...base, status: 'PENDING' }).assignmentNote).toBe('');
  });
  it('ca SOS: KHONG đang Chờ xử lý là chờ xác minh; INVALID là ca Admin đã kết luận không hợp lệ', () => {
    const base = { id: 4, code: 'SOS-1004', latitude: 16.05, longitude: 108.2, dangerLevel: 'KHONG' };
    expect(mSos({ ...base, status: 'PENDING' })).toMatchObject({ adminReview: true, invalid: false });
    expect(mSos({ ...base, status: 'INVALID', invalidReason: 'Bấm nhầm', invalidatedAt: '2026-11-03T08:00:00Z' }))
      .toMatchObject({ status: 'INVALID', adminReview: false, invalid: true, invalidReason: 'Bấm nhầm', invalidAt: Date.parse('2026-11-03T08:00:00Z') });
  });
  it('ca SOS hoàn thành: giữ ai hoàn thành, ghi chú/lý do và tên người bấm (nếu có)', () => {
    const base = { id: 4, code: 'SOS-1004', latitude: 16.05, longitude: 108.2, dangerLevel: 'CAO', status: 'COMPLETED' };
    expect(mSos({ ...base, completedByRole: 'CITIZEN', completionNote: 'Nước đã rút' }))
      .toMatchObject({ status: 'DONE', doneBy: 'CITIZEN', doneNote: 'Nước đã rút', doneByName: null });
    expect(mSos({ ...base, completedByRole: 'ADMIN', completionNote: 'Đã gọi xác minh', completedByName: 'Admin A' }))
      .toMatchObject({ doneBy: 'ADMIN', doneNote: 'Đã gọi xác minh', doneByName: 'Admin A' });
    expect(mSos({ ...base, status: 'ACCEPTED' })).toMatchObject({ doneBy: null, doneNote: '' });
  });
  it('điểm Matching: có điểm năng lực C; dữ liệu chấm theo công thức cũ (không có C) thì C = null', () => {
    const m = { score: 73, locationScore: 100, semanticScore: 60, capabilityScore: 50, workloadScore: 100, distanceM: 0, activeCases: 0, refusedCount: 0, reason: 'x' };
    expect(mMatching(m)).toMatchObject({ score: 73, L: 100, S: 60, C: 50, W: 100 });
    expect(mMatching({ ...m, capabilityScore: undefined }).C).toBeNull();
  });
  it('điểm Matching từ dịch vụ AI: dùng trọng số AI gửi về; thiếu trọng số thì dùng mặc định 25/30/30/15', () => {
    const m = { score: 85, locationScore: 100, semanticScore: 92, capabilityScore: 100, workloadScore: 50, distanceM: 300, activeCases: 0,
      refusedCount: 0, reason: 'x', weights: { L: 30, S: 20, C: 35, W: 15 }, source: 'AI_SERVICE' };
    expect(mMatching(m)).toMatchObject({ weights: { L: 30, S: 20, C: 35, W: 15 }, byAi: true });
    expect(mMatching({ ...m, weights: null, source: 'RULES' })).toMatchObject({ weights: { L: 25, S: 30, C: 30, W: 15 }, byAi: false });
  });
  it('ca SOS đã xác minh từ nhãn KHONG: giữ mô tả gốc và ghi chú xác minh riêng, kèm thời điểm', () => {
    const base = { id: 4, code: 'SOS-1004', latitude: 16.05, longitude: 108.2, dangerLevel: 'TRUNG_BINH', status: 'PENDING', description: 'hi mn' };
    expect(mSos({ ...base, verificationNote: 'Nhà ngập 1 m', verifiedAt: '2026-11-13T14:40:00Z' }))
      .toMatchObject({ desc: 'hi mn', verifyNote: 'Nhà ngập 1 m', verifiedAt: Date.parse('2026-11-13T14:40:00Z'), adminReview: false });
    expect(mSos(base).verifyNote).toBe('');
  });
  it('thông báo ca SOS: đường dẫn kèm mã ca theo vai trò để trang đích mở đúng ca', () => {
    const n = { id: 1, title: 'Ca SOS được phân công', referenceType: 'INCIDENT', referenceId: 12, read: false, createdAt: '2026-11-06T08:00:00Z' };
    expect(mNotif(n, 'ADMIN').link).toBe('/admin/sos?case=12');
    expect(mNotif(n, 'RESCUE_ORG').link).toBe('/rescue?case=12');
    expect(mNotif(n, 'CITIZEN').link).toBe('/sos?case=12');
  });
  it('thông báo khác: bình luận kèm mã bình luận, bài bị gỡ không dẫn đi đâu, khóa tài khoản, ủng hộ thành công', () => {
    const n = (o) => ({ id: 1, title: 't', read: false, createdAt: '2026-11-06T08:00:00Z', ...o });
    expect(mNotif(n({ type: 'COMMENT', referenceType: 'POST', referenceId: 9, subReferenceId: 55 }), 'CITIZEN').link).toBe('/posts/9?comment=55');
    expect(mNotif(n({ type: 'LIKE', referenceType: 'POST', referenceId: 9 }), 'CITIZEN').link).toBe('/posts/9');
    expect(mNotif(n({ type: 'POST_REMOVED', referenceType: 'POST', referenceId: 9 }), 'CITIZEN').link).toBeNull(); // thông báo cũ còn mã bài
    expect(mNotif(n({ type: 'REPORT_RESULT', referenceType: null, referenceId: null }), 'CITIZEN').link).toBeNull();
    expect(mNotif(n({ type: 'ACCOUNT', referenceType: 'USER', referenceId: 7 }), 'CITIZEN').link).toBe('/profile');
    expect(mNotif(n({ type: 'ACCOUNT', referenceType: 'USER', referenceId: 7 }), 'ADMIN').link).toBe('/admin/citizens?user=7');
    expect(mNotif(n({ type: 'ACCOUNT', referenceType: 'ORG', referenceId: 5 }), 'ADMIN').link).toBe('/admin/orgs/5');
    expect(mNotif(n({ type: 'FOLLOW', referenceType: 'USER', referenceId: 7 }), 'CITIZEN').link).toBe('/users/7');
    expect(mNotif(n({ type: 'DONATION', referenceType: 'DONATION', referenceId: 31 }), 'CITIZEN').link).toBe('/my-donations?donation=31');
  });
  it('Admin nhận thông báo báo cáo mới: mở trang chi tiết báo cáo của bài viết / chiến dịch', () => {
    const n = (o) => ({ id: 1, type: 'REPORT_NEW', title: 't', read: false, createdAt: '2026-11-06T08:00:00Z', ...o });
    expect(mNotif(n({ referenceType: 'POST', referenceId: 9 }), 'ADMIN').link).toBe('/admin/post-reports/9');
    expect(mNotif(n({ referenceType: 'CAMPAIGN', referenceId: 4 }), 'ADMIN').link).toBe('/admin/campaign-reports/4');
  });

  /* Hồ sơ công khai chỉ có trụ sở tới Phường/Xã: tổ chức mở hồ sơ công khai của chính mình không được làm mất số nhà, ghim trụ sở */
  it('mOrgPublic: gộp vào state không xóa dữ liệu trụ sở đầy đủ đã có', () => {
    const full = { id: 3, name: 'Đội A', orgType: 'VOLUNTEER', status: 'ACTIVE', operatingStatus: 'AVAILABLE', province: 'Thành phố Đà Nẵng',
      ward: 'Phường Hải Châu', streetAddress: '5 Kiệt 12', latitude: 16.06, longitude: 108.22, documents: [{ id: 1, fileUrl: 'u' }] };
    const pub = { ...full, streetAddress: null, latitude: null, longitude: null, documents: [], description: 'Mô tả mới' };
    const orgs = [];
    upBy(orgs, mOrg(full));
    upBy(orgs, mOrgPublic(pub));
    expect(orgs).toHaveLength(1);
    expect(orgs[0]).toMatchObject({ street: '5 Kiệt 12', lat: 16.06, lng: 108.22, description: 'Mô tả mới' });
    expect(orgs[0].docs).toHaveLength(1);
    expect(mOrgPublic(pub)).not.toHaveProperty('lat');
    expect(orgArea(mOrgPublic(pub))).toBe('Phường Hải Châu, Thành phố Đà Nẵng');
  });
});
