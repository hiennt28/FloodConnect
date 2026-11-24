import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/services/api', () => {
  const stub = () => new Proxy({}, { get: (t, k) => (t[k] ??= vi.fn(() => Promise.resolve({}))) });
  return { authApi: stub(), userApi: stub(), postApi: stub(), notificationApi: stub(), sosApi: stub(), rescueApi: stub(), orgApi: stub(), campaignApi: stub(), adminApi: stub(), geoApi: stub(), publicApi: stub() };
});
vi.mock('@/services/http', () => ({ jsonPart: (o) => JSON.stringify(o), tokens: { set: vi.fn(), get: vi.fn(() => null), clear: vi.fn() } }));

const apis = await import('@/services/api');
const { empty } = await import('../draft');
const { createAuthActions } = await import('./authActions');
const { createSocialActions } = await import('./socialActions');
const { createCampaignActions } = await import('./campaignActions');
const { createSosActions } = await import('./sosActions');
const { createAdminActions } = await import('./adminActions');
const { createProfileActions } = await import('./profileActions');
const { createLoadActions } = await import('./loadActions');

/* ctx giả lập giống AppProvider: state là object thường, run/get bắt lỗi như bản thật */
function makeCtx(extra = {}) {
  const ref = { current: empty() };
  const mutate = (fn) => fn(ref.current);
  const toast = vi.fn();
  const run = async (fn) => { try { await fn(); return null; } catch (e) { return e.message; } };
  const get = async (request) => { try { return await request(); } catch (e) { toast(e.message, 'err'); return null; } };
  const quiet = async (request) => { try { return await request(); } catch { return null; } };
  const pageOf = async (request) => { const r = await get(request); return r ? r.items : null; };
  return { ref, mutate, toast, run, get, quiet, pageOf, refresh: vi.fn(() => Promise.resolve()), me: null, myOrg: null, ...extra };
}
const apiError = (message, code, errors) => Object.assign(new Error(message), { code, errors });
const lockedUser = { id: '1', lock: { until: Date.now() + 60_000 } };

beforeEach(() => { vi.clearAllMocks(); });

describe('auth', () => {
  it('đăng nhập thành công: lưu token, gắn session, gọi refresh', async () => {
    apis.authApi.login.mockResolvedValueOnce({ accessToken: 'a', refreshToken: 'r', user: { id: 5, fullName: 'An', role: 'CITIZEN' } });
    const ctx = makeCtx();
    expect(await createAuthActions(ctx).login('  an@x.vn ', 'pw')).toBeNull();
    expect(apis.authApi.login).toHaveBeenCalledWith('an@x.vn', 'pw');
    expect(ctx.ref.current.session).toEqual({ userId: '5' });
    expect(ctx.refresh).toHaveBeenCalled();
  });
  it('email chưa xác thực và bị khóa đăng nhập trả về dạng riêng', async () => {
    const a = createAuthActions(makeCtx());
    apis.authApi.login.mockRejectedValueOnce(apiError('x', 'EMAIL_NOT_VERIFIED'));
    expect(await a.login('an@x.vn', 'pw')).toEqual({ unverified: 'an@x.vn' });
    // đăng nhập bằng SĐT: dùng email backend trả về để sang được trang nhập mã
    apis.authApi.login.mockRejectedValueOnce(apiError('x', 'EMAIL_NOT_VERIFIED', { email: 'an@x.vn' }));
    expect(await a.login('0912345678', 'pw')).toEqual({ unverified: 'an@x.vn' });
    apis.authApi.login.mockRejectedValueOnce(apiError('Thử lại sau 3 phút', 'LOGIN_LOCKED'));
    expect(await a.login('an@x.vn', 'pw')).toEqual({ locked: 180 });
    apis.authApi.login.mockRejectedValueOnce(apiError('x', 'LOGIN_LOCKED', { retryAfterSeconds: '42' }));
    expect(await a.login('an@x.vn', 'pw')).toEqual({ locked: 42 });
  });
  it('lỗi field từ backend được đổi sang tên field của form', async () => {
    apis.authApi.register.mockRejectedValueOnce(apiError('x', 'VALIDATION_ERROR', { fullName: 'Thiếu tên', confirmPassword: 'Không khớp' }));
    const r = await createAuthActions(makeCtx()).registerCitizen({ name: 'A', email: 'a@x.vn', phone: '0901234567', password: 'abc12345', confirm: 'abc' });
    expect(r).toEqual({ errors: { name: 'Thiếu tên', confirm: 'Không khớp' } });
  });
  it('đăng ký tổ chức: báo lỗi ở client, không gọi API khi dữ liệu sai', async () => {
    const r = await createAuthActions(makeCtx()).registerOrg(
      { name: '', email: 'sai', phone: '12', password: 'abc', confirm: 'x' },
      { name: '', type: '', provinceCode: '', wardCode: '', street: '', docs: { MAIN: [], ID_FRONT: [], ID_BACK: [], EVIDENCE: [] } });
    // chưa chọn loại tổ chức thì phần hồ sơ chưa hiện nên chưa báo lỗi ô hồ sơ nào
    expect(Object.keys(r.errors).sort()).toEqual(['address', 'cases', 'confirm', 'description', 'email', 'location', 'name', 'orgName', 'password', 'phone', 'radiusKm', 'type']);
    expect(r.errors.cases).toMatch(/số ca/); // bắt buộc: AI dùng cho điểm tải W
    expect(r.errors.radiusKm).toMatch(/bán kính/);
    expect(r.errors.description).toMatch(/mô tả tổ chức/); // bắt buộc: AI Matching suy năng lực từ mô tả
    expect(apis.authApi.registerOrg).not.toHaveBeenCalled();
  });
  it('đăng ký nhóm thiện nguyện: đủ CCCD hai mặt, trang web http(s); gửi tệp theo đúng tên từng ô', async () => {
    const acc = { name: 'Lan', email: 'lan@example.com', phone: '0912345678', password: 'abc12345', confirm: 'abc12345' };
    const file = (n) => new File(['x'], n, { type: 'image/jpeg' });
    const base = { name: 'Nhóm A', type: 'VOLUNTEER', provinceCode: '01', wardCode: '00004', street: 'Số 1', point: { lat: 21.034, lng: 105.83 }, description: 'Có 2 xuồng, tiếp tế lương thực', cases: '3', radiusKm: '15' };
    const a = createAuthActions(makeCtx());
    const bad = await a.registerOrg(acc, { ...base, website: 'facebook.com/a', docs: { MAIN: [], ID_FRONT: [file('t.jpg')], ID_BACK: [], EVIDENCE: [] } });
    expect(bad.errors).toMatchObject({ idBack: expect.stringMatching(/mặt sau/), website: expect.stringMatching(/http/) });
    expect(apis.authApi.registerOrg).not.toHaveBeenCalled();

    await a.registerOrg(acc, { ...base, website: 'https://facebook.com/a', docs: { MAIN: [], ID_FRONT: [file('t.jpg')], ID_BACK: [file('s.jpg')], EVIDENCE: [file('e.jpg')] } });
    const fd = apis.authApi.registerOrg.mock.calls[0][0];
    expect(fd.getAll('idFront').map((f) => f.name)).toEqual(['t.jpg']);
    expect(fd.getAll('idBack').map((f) => f.name)).toEqual(['s.jpg']);
    expect(fd.getAll('evidence').map((f) => f.name)).toEqual(['e.jpg']);
    const data = JSON.parse(fd.get('data'));
    expect(data.websiteUrl).toBe('https://facebook.com/a');
    // gửi mã xã (backend tự lấy tên tỉnh/xã) + tọa độ ghim trụ sở
    expect(data).toMatchObject({ wardCode: '00004', streetAddress: 'Số 1', latitude: 21.034, longitude: 105.83 });
    expect(data).not.toHaveProperty('province');
    expect(data).toMatchObject({ maxConcurrentCases: 3, serviceRadiusKm: 15 }); // gửi dạng số
  });
  it('đăng ký tổ chức: số ca và bán kính ngoài khoảng cho phép bị chặn', async () => {
    const acc = { name: 'Lan', email: 'lan@example.com', phone: '0912345678', password: 'abc12345', confirm: 'abc12345' };
    const base = { name: 'Nhóm A', type: 'STATE', provinceCode: '01', wardCode: '00004', street: 'Số 1', point: { lat: 21.034, lng: 105.83 }, description: 'Có xuồng', docs: { MAIN: [], ID_FRONT: [], ID_BACK: [], EVIDENCE: [] } };
    const a = createAuthActions(makeCtx());
    const r = await a.registerOrg(acc, { ...base, cases: '0', radiusKm: '150' });
    expect(r.errors.cases).toMatch(/từ 1 đến 50/);
    expect(r.errors.radiusKm).toMatch(/từ 1 đến 100/);
    expect((await a.registerOrg(acc, { ...base, cases: '2.5', radiusKm: '10' })).errors.cases).toMatch(/số nguyên/);
  });
});

describe('social', () => {
  it('tài khoản bị khóa không đăng bài, không thích', async () => {
    const s = createSocialActions(makeCtx({ me: lockedUser }));
    expect(await s.createPost({ content: 'x', images: [] })).toMatch(/bị khóa/);
    expect(s.toggleLike('1')).toMatch(/bị khóa/);
    expect(apis.postApi.create).not.toHaveBeenCalled();
  });
  it('kiểm tra nội dung bài đăng', async () => {
    const s = createSocialActions(makeCtx());
    expect(await s.createPost({ content: '   ', images: [] })).toMatch(/không được để trống/);
    expect(await s.createPost({ content: 'x'.repeat(2001), images: [] })).toMatch(/2.000/);
    expect(await s.createPost({ content: 'ok', images: [1, 2, 3, 4, 5, 6] })).toMatch(/5 tệp/);
  });
  it('vị trí bài đăng: bắt chọn đủ Tỉnh, Phường/Xã; chỉ gửi mã xã + chi tiết (+ GPS nếu có), không bắt nhập tọa độ', async () => {
    const s = createSocialActions(makeCtx());
    const loc = { provinceCode: '01', wardCode: '00004', detail: '  Ngõ 5 Đội Cấn ', gps: null };
    expect(await s.createPost({ content: 'ok', images: [], location: { ...loc, wardCode: '' } })).toMatch(/Phường\/Xã/);
    expect(await s.createPost({ content: 'ok', images: [], location: { ...loc, provinceCode: '48' } })).toMatch(/không thuộc/);
    expect(apis.postApi.create).not.toHaveBeenCalled();

    await s.createPost({ content: 'ok', images: [], location: loc });
    expect(JSON.parse(apis.postApi.create.mock.calls[0][0].get('data')))
      .toEqual({ content: 'ok', wardCode: '00004', addressDetail: 'Ngõ 5 Đội Cấn', latitude: null, longitude: null });
    await s.createPost({ content: 'ok', images: [], location: { ...loc, gps: { lat: 21.03, lng: 105.83 } } });
    expect(JSON.parse(apis.postApi.create.mock.calls[1][0].get('data'))).toMatchObject({ latitude: 21.03, longitude: 105.83 });
    await s.createPost({ content: 'ok', images: [] });
    expect(JSON.parse(apis.postApi.create.mock.calls[2][0].get('data'))).toMatchObject({ wardCode: null, latitude: null });
  });
  it('theo dõi / bỏ theo dõi gọi đúng endpoint theo trạng thái hiện tại', async () => {
    const ctx = makeCtx();
    const s = createSocialActions(ctx);
    await s.toggleFollow('9');
    expect(apis.userApi.follow).toHaveBeenCalledWith('9');
    expect(ctx.ref.current.profiles['9']).toMatchObject({ followedByMe: true, followers: 1 });
    await s.toggleFollow('9');
    expect(apis.userApi.unfollow).toHaveBeenCalledWith('9');
  });
});

describe('campaigns', () => {
  const valid = { name: 'Cứu trợ', purpose: 'p', area: 'a', beneficiaries: 'b', orgInfo: 'o', target: '1000000', bank: 'MB Bank',
    accountName: 'X', accountNo: '123456', start: 1, end: 2, coverFile: 'f' };
  it('kiểm tra dữ liệu tạo chiến dịch', async () => {
    const c = createCampaignActions(makeCtx());
    expect(await c.create({ ...valid, coverFile: null })).toMatch(/ảnh đại diện/);
    expect(await c.create({ ...valid, target: '0' })).toMatch(/mục tiêu/);
    expect(await c.create({ ...valid, accountNo: '12' })).toMatch(/tài khoản/);
    expect(await c.create({ ...valid, bank: '  ' })).toMatch(/ngân hàng/);
    expect(await c.create({ ...valid, end: 1 })).toMatch(/kết thúc phải sau/);
    expect(apis.campaignApi.create).not.toHaveBeenCalled();
    expect(await c.create(valid)).toBeNull();
    expect(apis.campaignApi.create).toHaveBeenCalledTimes(1);
  });
  it('gia hạn phải sau thời điểm hiện tại; ủng hộ phải là số nguyên dương', async () => {
    const c = createCampaignActions(makeCtx());
    expect(c.extend('1', Date.now() - 1000)).toMatch(/lớn hơn thời điểm hiện tại/);
    expect(await c.startDonation('1', { amount: '1.5', message: '' })).toEqual({ error: expect.stringMatching(/số nguyên/) });
    apis.campaignApi.donate.mockResolvedValueOnce({ transactionCode: 'TX1', paymentUrl: 'u', mock: true });
    expect(await c.startDonation('1', { amount: '50000', message: ' cảm ơn ', anonymous: 0 }))
      .toEqual({ id: 'TX1', amount: 50000, paymentUrl: 'u', mock: true });
    expect(apis.campaignApi.donate).toHaveBeenCalledWith('1', { amount: 50000, message: 'cảm ơn', paymentMethod: 'VNPAY', anonymous: false });
  });
});

describe('sos', () => {
  it('kiểm tra mô tả và tọa độ trước khi gửi; không còn tham số ảnh', async () => {
    const s = createSosActions(makeCtx());
    expect(await s.create({ desc: ' ', lat: 21, lng: 105.8 })).toEqual({ error: expect.stringMatching(/mô tả/) });
    expect(await s.create({ desc: 'x', lat: 40.7, lng: -74 })).toEqual({ error: expect.stringMatching(/ngoài lãnh thổ/) });
    expect(apis.sosApi.create).not.toHaveBeenCalled();
  });
  it('#12 mở ca từ thông báo: chỉ ghim ca đã kết thúc (ca đang hoạt động đã có trong danh sách đồng bộ)', async () => {
    const ctx = makeCtx({ me: { role: 'RESCUE_ORG' } });
    const s = createSosActions(ctx);
    apis.sosApi.detail.mockResolvedValueOnce({ id: 9, code: 'SOS-9', status: 'COMPLETED', dangerLevel: 'CAO' });
    await s.loadCase('9');
    expect(ctx.refresh).toHaveBeenCalledTimes(1); // vai trò khác Admin: đồng bộ lại trước
    expect(apis.sosApi.detail).toHaveBeenCalledWith('9');
    expect(ctx.ref.current.sos[0]).toMatchObject({ id: '9', status: 'DONE', pinned: true });
    apis.sosApi.detail.mockResolvedValueOnce({ id: 10, code: 'SOS-10', status: 'ACCEPTED', dangerLevel: 'CAO' });
    await s.loadCase('10');
    expect(ctx.ref.current.sos.find((x) => x.id === '10').pinned).toBe(false);
  });
  it('#12 ca theo trang: Admin / công khai / lịch sử tổ chức gọi đúng API và trả { items, total, last }', async () => {
    const s = createSosActions(makeCtx({ me: { role: 'ADMIN' } }));
    const page = { items: [{ id: 1, code: 'SOS-1', status: 'CANCELLED', dangerLevel: 'THAP', citizen: { id: 3, fullName: 'Bình' } }], total: 41, last: false };
    apis.adminApi.sosPage.mockResolvedValueOnce(page);
    expect(await s.casesPage('admin', { status: 'ENDED', page: 2 })).toMatchObject({ items: [{ id: '1', status: 'CANCELLED' }], total: 41, last: false });
    expect(apis.adminApi.sosPage).toHaveBeenCalledWith({ status: 'ENDED', page: 2 });
    apis.sosApi.page.mockResolvedValueOnce({ items: [], total: 0, last: true });
    expect(await s.casesPage('public', { status: 'COMPLETED', page: 0, size: 10 })).toEqual({ items: [], total: 0, last: true });
    apis.rescueApi.history.mockResolvedValueOnce({ items: [], total: 0, last: true });
    await s.casesPage('org', { page: 1, size: 10 });
    expect(apis.rescueApi.history).toHaveBeenCalledWith(1, 10);
    apis.sosApi.page.mockRejectedValueOnce(new Error('mạng'));
    expect(await s.casesPage('public', { status: 'COMPLETED' })).toBeNull();
  });
  it('cảnh báo trùng: trả về danh sách ca gần đó; tạo thành công: refresh và trả mã ca', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    apis.sosApi.create.mockResolvedValueOnce({ duplicateWarning: true, similar: [{ id: 3, code: 'SOS-3', latitude: 21, longitude: 105.8, createdAt: '2026-01-01T00:00:00Z', status: 'ASSIGNED', dangerLevel: 'CAO', assignedOrg: { id: 8, name: 'Đội A' } }] });
    const dup = await s.create({ desc: 'ngập', lat: 21, lng: 105.8 });
    // đủ thông tin công khai để người dân xem chi tiết ca trùng: mức độ, trạng thái, tọa độ, tổ chức phụ trách
    expect(dup.nearby[0]).toMatchObject({ id: '3', code: 'SOS-3', dist: 0, severity: 'CAO', status: 'ASSIGNED', lat: 21, lng: 105.8, org: expect.objectContaining({ name: 'Đội A' }) });
    apis.sosApi.create.mockResolvedValueOnce({ incident: { id: 4, code: 'SOS-4', dangerLevel: 'KHONG' } });
    expect(await s.create({ desc: 'ngập', lat: 21, lng: 105.8 })).toEqual({ id: '4', code: 'SOS-4', adminReview: true });
    expect(ctx.refresh).toHaveBeenCalledTimes(1);
  });
  it('ca SOS chỉ gồm chữ và vị trí: gửi JSON (không FormData); sửa chỉ gửi mô tả đã bỏ khoảng trắng', async () => {
    const s = createSosActions(makeCtx());
    apis.sosApi.create.mockResolvedValueOnce({ incident: { id: 5, code: 'SOS-5', dangerLevel: 'CAO' } });
    await s.create({ desc: ' Nước ngập tới ngực ', lat: 21, lng: 105.8, address: 'Kiệt 1' });
    expect(apis.sosApi.create).toHaveBeenCalledWith({ description: 'Nước ngập tới ngực', latitude: 21, longitude: 105.8, address: 'Kiệt 1', forceCreate: false });
    expect(s.edit('5', { desc: '   ' })).toMatch(/mô tả/);
    expect(await s.edit('5', { desc: ' Ngập sâu hơn ' })).toBeNull();
    expect(apis.sosApi.update).toHaveBeenCalledWith('5', 'Ngập sâu hơn');
  });
  it('địa giới chỉ là tiện ích: lỗi (chưa nạp dữ liệu, mất mạng) trả null, không ném lỗi, không báo lỗi', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    const hit = { ward: { code: '00004', name: 'Phường Ba Đình', provinceCode: '01', provinceName: 'Thành phố Hà Nội' }, approximate: false };
    apis.geoApi.locate.mockResolvedValueOnce(hit);
    expect(await s.locateWard(21.0368, 105.8346)).toEqual(hit);
    expect(apis.geoApi.locate).toHaveBeenCalledWith(21.0368, 105.8346);
    apis.geoApi.locate.mockRejectedValueOnce(apiError('Tạm thời chưa tra cứu được phường/xã.'));
    expect(await s.locateWard(21, 105.8)).toBeNull();
    apis.geoApi.ward.mockRejectedValueOnce(apiError('Không tìm thấy phường/xã.'));
    expect(await s.wardDetail('99999')).toBeNull();
    expect(ctx.refresh).not.toHaveBeenCalled();
  });
  it('tổ chức: từ chối phải có lý do; đổi trạng thái hoạt động cần xác nhận', async () => {
    const s = createSosActions(makeCtx());
    expect(s.rejectAssigned('4', '  ')).toMatch(/lý do/);
    expect(s.updateStatus('4', 'PENDING')).toMatch(/không hợp lệ/);
    apis.orgApi.setOperatingStatus.mockRejectedValueOnce(apiError('x', 'CONFIRM_REQUIRED'));
    expect(await s.setOrgStatus('BUSY', false)).toBe('CONFIRM');
    expect(apis.orgApi.setOperatingStatus).toHaveBeenCalledWith('BUSY', false);
  });
  it('Admin xem ca lân cận: kèm khoảng cách giữa hai ca, lưu người gửi; lỗi thì trả null', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    apis.adminApi.nearby.mockResolvedValueOnce([{ incident: { id: 9, code: 'SOS-9', latitude: 21, longitude: 105.8, status: 'ACCEPTED', dangerLevel: 'CAO', description: 'Nhà ngập', citizen: { id: 7, fullName: 'Bình' }, citizenPhone: '0901' }, distanceM: 120 }]);
    expect(await s.nearby('4')).toEqual([expect.objectContaining({ id: '9', code: 'SOS-9', status: 'ACCEPTED', desc: 'Nhà ngập', distFromCase: 120 })]);
    expect(ctx.ref.current.users).toEqual([expect.objectContaining({ id: '7', phone: '0901' })]);
    apis.adminApi.nearby.mockRejectedValueOnce(apiError('Lỗi mạng'));
    expect(await s.nearby('4')).toBeNull();
  });
  it('Admin đánh dấu không hợp lệ: phải có lý do, gửi lý do đã bỏ khoảng trắng rồi refresh', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    expect(s.markInvalid('4', '  ')).toMatch(/lý do/);
    expect(apis.adminApi.markInvalid).not.toHaveBeenCalled();
    expect(await s.markInvalid('4', '  Người gửi bấm nhầm ')).toBeNull();
    expect(apis.adminApi.markInvalid).toHaveBeenCalledWith('4', 'Người gửi bấm nhầm');
    expect(ctx.refresh).toHaveBeenCalledTimes(1);
  });
  it('Admin xác minh ca KHONG: phải có ghi chú, gửi mức + ghi chú đã bỏ khoảng trắng rồi refresh', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    expect(s.verifyReview('4', 'CAO', '  ')).toMatch(/xác minh/);
    expect(apis.adminApi.adjustLevel).not.toHaveBeenCalled();
    expect(await s.verifyReview('4', 'CAO', ' Nhà ngập 1 m ')).toBeNull();
    expect(apis.adminApi.adjustLevel).toHaveBeenCalledWith('4', 'CAO', 'Nhà ngập 1 m');
    expect(ctx.refresh).toHaveBeenCalledTimes(1);
  });
  it('người dân báo đã an toàn: ghi chú bỏ khoảng trắng, để trống thì gửi null; lỗi backend trả về thông báo', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    expect(await s.markSafe('4', '  Hàng xóm đã chở đi ')).toBeNull();
    expect(apis.sosApi.markSafe).toHaveBeenLastCalledWith('4', 'Hàng xóm đã chở đi');
    expect(await s.markSafe('4', '   ')).toBeNull();
    expect(apis.sosApi.markSafe).toHaveBeenLastCalledWith('4', null);
    expect(ctx.refresh).toHaveBeenCalledTimes(2);
    apis.sosApi.markSafe.mockRejectedValueOnce(apiError('Ca SOS đã kết thúc.', 'SOS_FINISHED'));
    expect(await s.markSafe('4')).toBe('Ca SOS đã kết thúc.');
  });
  it('Admin xác nhận hoàn thành: phải có lý do, gửi lý do đã bỏ khoảng trắng rồi refresh', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    expect(s.adminComplete('4', '  ')).toMatch(/lý do/);
    expect(apis.adminApi.completeSos).not.toHaveBeenCalled();
    expect(await s.adminComplete('4', ' Đã gọi, người dân an toàn ')).toBeNull();
    expect(apis.adminApi.completeSos).toHaveBeenCalledWith('4', 'Đã gọi, người dân an toàn');
    expect(ctx.refresh).toHaveBeenCalledTimes(1);
  });
  it('Admin hủy phân công: phải có lý do, gửi lý do đã bỏ khoảng trắng, báo lỗi 409 khi tổ chức vừa xác nhận', async () => {
    const ctx = makeCtx();
    const s = createSosActions(ctx);
    expect(s.unassign('4', '  ')).toMatch(/lý do/);
    expect(s.unassign('4')).toMatch(/lý do/);
    expect(apis.adminApi.unassign).not.toHaveBeenCalled();
    expect(await s.unassign('4', ' Đội không có xuồng ')).toBeNull();
    expect(apis.adminApi.unassign).toHaveBeenCalledWith('4', 'Đội không có xuồng');
    expect(ctx.refresh).toHaveBeenCalledTimes(1);
    apis.adminApi.unassign.mockRejectedValueOnce(apiError('Tổ chức vừa xác nhận tiếp nhận ca, không hủy phân công được nữa.', 'SOS_ALREADY_ACCEPTED'));
    expect(await s.unassign('4', 'Lý do')).toMatch(/vừa xác nhận/);
  });
});

describe('profile', () => {
  it('đổi ảnh đại diện: kiểm tra JPG/PNG ≤ 5 MB rồi tải lên ngay, cập nhật người dùng trong state', async () => {
    const ctx = makeCtx();
    const p = createProfileActions(ctx);
    expect(await p.updateAvatar(new File(['x'], 'a.gif', { type: 'image/gif' }))).toMatch(/JPG\/PNG/);
    expect(await p.updateAvatar(new File([new Uint8Array(5 * 1024 * 1024 + 1)], 'a.png', { type: 'image/png' }))).toMatch(/5 MB/);
    expect(apis.userApi.updateAvatar).not.toHaveBeenCalled();

    apis.userApi.updateAvatar.mockResolvedValueOnce({ id: 1, fullName: 'Lan', role: 'CITIZEN', avatarUrl: '/files/a.png' });
    expect(await p.updateAvatar(new File(['x'], 'a.png', { type: 'image/png' }))).toBeNull();
    expect(apis.userApi.updateAvatar.mock.calls[0][0].get('file').name).toBe('a.png');
    expect(ctx.ref.current.users.find((u) => u.id === '1').avatar).toBe('/files/a.png');
  });
  it('xóa ảnh đại diện: gọi API xóa, người dùng trong state không còn ảnh', async () => {
    const ctx = makeCtx();
    const p = createProfileActions(ctx);
    apis.userApi.updateAvatar.mockResolvedValueOnce({ id: 1, fullName: 'Lan', role: 'CITIZEN', avatarUrl: '/files/a.png' });
    await p.updateAvatar(new File(['x'], 'a.png', { type: 'image/png' }));
    apis.userApi.removeAvatar.mockResolvedValueOnce({ id: 1, fullName: 'Lan', role: 'CITIZEN', avatarUrl: null });
    expect(await p.removeAvatar()).toBeNull();
    expect(apis.userApi.removeAvatar).toHaveBeenCalledTimes(1);
    expect(ctx.ref.current.users.find((u) => u.id === '1').avatar).toBeNull();
  });
  it('tổ chức sửa thông tin: mô tả bắt buộc, không xóa trắng được; gửi mô tả đã bỏ khoảng trắng', async () => {
    const ctx = makeCtx();
    const p = createProfileActions(ctx);
    const form = { street: 'Số 1', provinceCode: '01', wardCode: '00004', point: { lat: 21.034, lng: 105.83 }, website: '', cases: 4, radiusKm: 20 };
    expect(await p.updateOrg({ ...form, description: '   ' })).toMatch(/mô tả tổ chức/);
    expect(await p.updateOrg({ ...form, description: 'a'.repeat(2001) })).toMatch(/tối đa/);
    expect(apis.orgApi.update).not.toHaveBeenCalled();
    await p.updateOrg({ ...form, description: ' Có 2 ca-nô ' }); // chỉ kiểm tra dữ liệu gửi đi (ctx giả chưa có phiên đăng nhập)
    expect(apis.orgApi.update.mock.calls[0][0]).toMatchObject({ description: 'Có 2 ca-nô', websiteUrl: null, maxConcurrentCases: 4, serviceRadiusKm: 20 });
  });
  it('tổ chức sửa thông tin: số ca xử lý cùng lúc và bán kính hoạt động bắt buộc', async () => {
    const p = createProfileActions(makeCtx());
    const form = { street: 'Số 1', provinceCode: '01', wardCode: '00004', point: { lat: 21.034, lng: 105.83 }, website: '', description: 'Có ca-nô' };
    expect(await p.updateOrg({ ...form, cases: '', radiusKm: 20 })).toMatch(/số ca/);
    expect(await p.updateOrg({ ...form, cases: 3, radiusKm: '' })).toMatch(/bán kính/);
    expect(apis.orgApi.update).not.toHaveBeenCalled();
  });
});

describe('admin', () => {
  it('khóa tài khoản: số tháng phải là số nguyên ≥ 1, bắt buộc nhập lý do', async () => {
    const a = createAdminActions(makeCtx());
    expect(a.lockOrg('6', '0', 'Spam')).toMatch(/≥ 1/);
    expect(a.lockCitizen('7', '1.5', 'Spam')).toMatch(/≥ 1/);
    expect(a.lockOrg('6', '2', '  ')).toMatch(/lý do/);
    expect(apis.adminApi.lockOrg).not.toHaveBeenCalled();
    expect(await a.lockOrg('6', '2', '  Đăng tin giả nhiều lần ')).toBeNull();
    expect(apis.adminApi.lockOrg).toHaveBeenCalledWith('6', { months: 2, reason: 'Đăng tin giả nhiều lần' });
  });
  it('lỗi khi tải danh sách hiện toast và trả về null', async () => {
    const ctx = makeCtx();
    apis.adminApi.orgs.mockRejectedValueOnce(new Error('Mất kết nối'));
    expect(await createAdminActions(ctx).orgs('NEW')).toBeNull();
    expect(ctx.toast).toHaveBeenCalledWith('Mất kết nối', 'err');
  });
  it('thống kê gọi đủ 7 endpoint, cùng bộ lọc thời gian cho mức độ và trạng thái', async () => {
    const r = await createAdminActions(makeCtx()).stats('day');
    expect(Object.keys(r)).toEqual(['citizens', 'sos', 'sosStatus', 'overview', 'orgs', 'topOrgs', 'topDonors']);
    expect(apis.adminApi.sosStats).toHaveBeenCalledWith('day');
    expect(apis.adminApi.sosStatusStats).toHaveBeenCalledWith('day');
    expect(apis.adminApi.overview).toHaveBeenCalled();
  });
  it('biểu đồ theo ngày gọi đúng số ngày', async () => {
    await createAdminActions(makeCtx()).timeline(90);
    expect(apis.adminApi.sosTimeline).toHaveBeenCalledWith(90);
  });
  it('tạo Admin: kiểm tra từng ô trước khi gửi, bỏ khoảng trắng, lỗi của backend hiện đúng ô', async () => {
    const a = createAdminActions(makeCtx());
    const bad = await a.createAdmin({ fullName: ' ', email: 'khong-phai-email', phone: '12' });
    expect(Object.keys(bad.errors).sort()).toEqual(['email', 'fullName', 'phone']);
    expect(apis.adminApi.createAdmin).not.toHaveBeenCalled();

    expect(await a.createAdmin({ fullName: ' Lê Thu Hà ', email: ' thuha@gmail.com ', phone: '0912345678' })).toEqual({ ok: true });
    expect(apis.adminApi.createAdmin).toHaveBeenCalledWith({ fullName: 'Lê Thu Hà', email: 'thuha@gmail.com', phone: '0912345678' });

    apis.adminApi.createAdmin.mockRejectedValueOnce(apiError('Dữ liệu không hợp lệ', 'VALIDATION_ERROR', { email: 'Email đã được sử dụng.' }));
    expect((await a.createAdmin({ fullName: 'A', email: 'a@x.vn', phone: '0912345678' })).errors).toEqual({ email: 'Email đã được sử dụng.' });
  });
  it('vô hiệu hóa Admin: bắt buộc lý do; Admin thường bị backend từ chối thì trả thông báo lỗi', async () => {
    const a = createAdminActions(makeCtx());
    expect(a.disableAdmin(7, '  ')).toMatch(/lý do/);
    expect(apis.adminApi.disableAdmin).not.toHaveBeenCalled();
    expect(await a.disableAdmin(7, ' Hết nhiệm kỳ ')).toBeNull();
    expect(apis.adminApi.disableAdmin).toHaveBeenCalledWith(7, 'Hết nhiệm kỳ');
    apis.adminApi.admins.mockRejectedValueOnce(apiError('Chỉ Admin gốc mới quản lý được tài khoản Admin.', 'ROOT_ADMIN_ONLY'));
    expect(await a.admins()).toBeNull();
  });
});

describe('load (#12 đợt 7c: tải theo trang)', () => {
  const comment = (id, extra = {}) => ({ id, postId: 3, content: 'x', author: { id: 7, fullName: 'An' }, replyCount: 0, createdAt: '2026-01-01T00:00:00Z', ...extra });
  it('bình luận gốc theo trang, KHÔNG tải phản hồi kèm (trước đây mỗi bình luận có phản hồi tốn thêm một request)', async () => {
    const ctx = makeCtx();
    const l = createLoadActions(ctx);
    apis.postApi.comments.mockResolvedValueOnce({ items: [comment(1, { replyCount: 4 }), comment(2)], last: false });
    expect(await l.comments('3', 1)).toEqual({ last: false });
    expect(apis.postApi.comments).toHaveBeenCalledWith('3', 1);
    expect(apis.postApi.replies).not.toHaveBeenCalled();
    expect(ctx.ref.current.comments.find((c) => c.id === '1').replyCount).toBe(4); // "Xem 4 phản hồi" dù chưa tải phản hồi
    apis.postApi.replies.mockResolvedValueOnce({ items: [comment(5, { parentCommentId: 1 })], last: true });
    expect(await l.replies('1', 0)).toEqual({ last: true });
    expect(ctx.ref.current.comments.find((c) => c.id === '5').parentId).toBe('1');
  });
  it('một bình luận theo mã (mở từ thông báo): đưa vào state; không còn thì null', async () => {
    const ctx = makeCtx();
    const l = createLoadActions(ctx);
    apis.postApi.comment.mockResolvedValueOnce(comment(9, { parentCommentId: 1 }));
    expect(await l.comment('9')).toMatchObject({ id: '9', parentId: '1' });
    expect(ctx.ref.current.comments.some((c) => c.id === '9')).toBe(true);
    apis.postApi.comment.mockRejectedValueOnce(new Error('404'));
    expect(await l.comment('10')).toBeNull();
  });
  it('chiến dịch theo trang trả { ids, last, total }; số đội sẵn sàng do máy chủ đếm', async () => {
    const l = createLoadActions(makeCtx());
    apis.campaignApi.list.mockResolvedValueOnce({ items: [{ id: 4, orgId: 1, name: 'A', targetAmount: 1, raisedAmount: 0 }], last: false, total: 30 });
    expect(await l.campaigns({ status: 'ACTIVE', page: 1 })).toEqual({ ids: ['4'], last: false, total: 30 });
    expect(apis.campaignApi.list).toHaveBeenCalledWith('ACTIVE', '', 1, 12);
    apis.publicApi.availableOrgs.mockResolvedValueOnce({ count: 137 });
    expect(await l.readyOrgCount()).toBe(137);
  });
  it('thông báo cũ hơn theo trang, đường dẫn theo vai trò', async () => {
    const l = createLoadActions(makeCtx());
    apis.notificationApi.list.mockResolvedValueOnce({ items: [{ id: 1, type: 'SOS_STATUS', title: 't', content: 'c', referenceType: 'INCIDENT', referenceId: 5, createdAt: '2026-01-01T00:00:00Z' }], last: true });
    const r = await l.notifications(1, 'CITIZEN');
    expect(apis.notificationApi.list).toHaveBeenCalledWith(1);
    expect(r).toMatchObject({ last: true, items: [{ id: '1' }] });
    expect(r.items[0].link).toContain('case=5');
  });
});
