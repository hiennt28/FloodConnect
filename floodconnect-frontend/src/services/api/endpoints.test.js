import { beforeEach, describe, expect, it, vi } from 'vitest';

vi.mock('@/services/http', () => ({ api: vi.fn(() => Promise.resolve({})) }));
const { api } = await import('@/services/http');
const { authApi, userApi, postApi, notificationApi, sosApi, rescueApi, orgApi, campaignApi, adminApi, geoApi, publicApi } = await import('@/services/api');

/* Mỗi dòng: [mô tả, lời gọi, đường dẫn mong đợi, tùy chọn mong đợi]. Đường dẫn và tùy chọn khớp đúng với code gọi API trước khi tách. */
const form = new FormData();
const CASES = [
  ['auth.login', () => authApi.login('a@b.vn', 'pw1'), '/api/auth/login', { method: 'POST', auth: false, body: { identifier: 'a@b.vn', password: 'pw1' } }],
  ['auth.logout', () => authApi.logout('rt'), '/api/auth/logout', { method: 'POST', auth: false, body: { refreshToken: 'rt' } }],
  ['auth.register', () => authApi.register({ fullName: 'A' }), '/api/auth/register', { method: 'POST', auth: false, body: { fullName: 'A' } }],
  ['auth.registerOrg', () => authApi.registerOrg(form), '/api/auth/register-org', { method: 'POST', auth: false, form }],
  ['auth.verifyEmail', () => authApi.verifyEmail('a@b.vn', '123456'), '/api/auth/verify-email', { method: 'POST', auth: false, body: { email: 'a@b.vn', code: '123456' } }],
  ['auth.resend', () => authApi.resendVerification('e'), '/api/auth/resend-verification', { method: 'POST', auth: false, body: { email: 'e' } }],
  ['auth.forgot', () => authApi.forgotPassword('e'), '/api/auth/forgot-password', { method: 'POST', auth: false, body: { email: 'e' } }],
  ['auth.reset', () => authApi.resetPassword('a@b.vn', '123456', 'p', 'c'), '/api/auth/reset-password', { method: 'POST', auth: false, body: { email: 'a@b.vn', code: '123456', newPassword: 'p', confirmPassword: 'c' } }],

  ['public.stats', () => publicApi.stats(), '/api/public/stats', { auth: false }],
  ['public.availableOrgs', () => publicApi.availableOrgs(), '/api/public/available-orgs', { auth: false }],

  ['user.me', () => userApi.me(), '/api/users/me', undefined],
  ['user.updateMe', () => userApi.updateMe({ fullName: 'A', phone: '0901234567' }), '/api/users/me', { method: 'PUT', body: { fullName: 'A', phone: '0901234567' } }],
  ['user.avatar', () => userApi.updateAvatar(form), '/api/users/me/avatar', { method: 'PUT', form }],
  ['user.removeAvatar', () => userApi.removeAvatar(), '/api/users/me/avatar', { method: 'DELETE' }],
  ['user.password', () => userApi.changePassword({ currentPassword: 'a' }), '/api/users/me/password', { method: 'PUT', body: { currentPassword: 'a' } }],
  ['user.gps', () => userApi.updateGps({ permission: 'ALLOWED' }), '/api/users/me/gps', { method: 'PUT', body: { permission: 'ALLOWED' } }],
  ['user.profile', () => userApi.profile('7'), '/api/users/7', undefined],
  ['user.posts', () => userApi.posts('7', 2), '/api/users/7/posts?page=2&size=10', undefined],
  ['user.search', () => userApi.search('Hà Nội', 'ALL', 30), `/api/users/search?q=${encodeURIComponent('Hà Nội')}&type=ALL&size=30`, undefined],
  ['user.search org', () => userApi.search('', 'ORG', 100), '/api/users/search?q=&type=ORG&size=100', undefined],
  ['user.follow', () => userApi.follow('7'), '/api/users/7/follow', { method: 'POST' }],
  ['user.unfollow', () => userApi.unfollow('7'), '/api/users/7/follow', { method: 'DELETE' }],

  ['post.feed', () => postApi.feed('all', 0, 10), '/api/posts?filter=all&page=0&size=10', undefined],
  ['post.get', () => postApi.get('3'), '/api/posts/3', undefined],
  ['post.create', () => postApi.create(form), '/api/posts', { method: 'POST', form }],
  ['post.update', () => postApi.update('3', form), '/api/posts/3', { method: 'PUT', form }],
  ['post.remove', () => postApi.remove('3'), '/api/posts/3', { method: 'DELETE' }],
  ['post.like', () => postApi.like('3'), '/api/posts/3/like', { method: 'POST' }],
  ['post.report', () => postApi.report('3', { reason: 'SPAM' }), '/api/posts/3/report', { method: 'POST', body: { reason: 'SPAM' } }],
  ['post.comments', () => postApi.comments('3', 1), '/api/posts/3/comments?page=1&size=20', undefined],
  ['post.comment', () => postApi.comment('9'), '/api/comments/9', undefined],
  ['post.replies', () => postApi.replies('9'), '/api/comments/9/replies?page=0&size=10', undefined],
  ['post.addComment', () => postApi.addComment('3', { content: 'x' }), '/api/posts/3/comments', { method: 'POST', body: { content: 'x' } }],
  ['post.editComment', () => postApi.editComment('9', { content: 'y' }), '/api/comments/9', { method: 'PUT', body: { content: 'y' } }],
  ['post.deleteComment', () => postApi.deleteComment('9'), '/api/comments/9', { method: 'DELETE' }],

  ['notif.list', () => notificationApi.list(), '/api/notifications?page=0&size=30', undefined],
  ['notif.list page', () => notificationApi.list(2), '/api/notifications?page=2&size=30', undefined],
  ['notif.read', () => notificationApi.markRead('5'), '/api/notifications/5/read', { method: 'PUT' }],
  ['notif.readAll', () => notificationApi.markAllRead(), '/api/notifications/read-all', { method: 'PUT' }],

  ['sos.mine', () => sosApi.mine(), '/api/sos/mine', undefined],
  ['sos.active', () => sosApi.active(), '/api/sos/active', undefined],
  ['sos.page', () => sosApi.page({ status: 'COMPLETED', page: 2 }), '/api/sos?status=COMPLETED&page=2&size=10', undefined],
  ['sos.detail', () => sosApi.detail('4'), '/api/sos/4', undefined],
  ['sos.create', () => sosApi.create({ description: 'Ngập' }), '/api/sos', { method: 'POST', body: { description: 'Ngập' } }],
  ['sos.update', () => sosApi.update('4', 'Ngập sâu'), '/api/sos/4', { method: 'PUT', body: { description: 'Ngập sâu' } }],
  ['sos.cancel', () => sosApi.cancel('4'), '/api/sos/4/cancel', { method: 'POST' }],
  ['sos.markSafe', () => sosApi.markSafe('4', 'Đã được đưa đi'), '/api/sos/4/safe', { method: 'POST', body: { note: 'Đã được đưa đi' } }],
  ['sos.markers', () => sosApi.markers(), '/api/map/markers', undefined],

  ['rescue.cases', () => rescueApi.cases(), '/api/org/sos?filter=ACTIVE', undefined],
  ['rescue.history', () => rescueApi.history(1), '/api/org/sos/history?page=1&size=10', undefined],
  ['rescue.suggested', () => rescueApi.suggested(), '/api/org/sos/suggested', undefined],
  ['rescue.claim', () => rescueApi.claim('4'), '/api/org/sos/4/claim', { method: 'POST' }],
  ['rescue.accept', () => rescueApi.accept('4'), '/api/org/sos/4/accept', { method: 'POST' }],
  ['rescue.reject', () => rescueApi.reject('4', 'xa'), '/api/org/sos/4/reject', { method: 'POST', body: { reason: 'xa' } }],
  ['rescue.complete', () => rescueApi.complete('4'), '/api/org/sos/4/complete', { method: 'POST' }],

  ['org.me', () => orgApi.me(), '/api/org/me', undefined],
  ['org.resubmit', () => orgApi.resubmit(form), '/api/org/me/resubmit', { method: 'PUT', form }],
  ['org.status', () => orgApi.setOperatingStatus('AVAILABLE', true), '/api/org/me/operating-status', { method: 'PUT', body: { status: 'AVAILABLE', confirm: true } }],

  ['campaign.list', () => campaignApi.list('ACTIVE', 'lũ', 0, 50), `/api/campaigns?status=ACTIVE&q=${encodeURIComponent('lũ')}&page=0&size=50`, undefined],
  ['campaign.get', () => campaignApi.get('2'), '/api/campaigns/2', undefined],
  ['campaign.donations', () => campaignApi.donations('2', 1, 8), '/api/campaigns/2/donations?page=1&size=8', undefined],
  ['campaign.donate', () => campaignApi.donate('2', { amount: 1 }), '/api/campaigns/2/donate', { method: 'POST', body: { amount: 1 } }],
  ['campaign.report', () => campaignApi.report('2', { reason: 'OTHER' }), '/api/campaigns/2/report', { method: 'POST', body: { reason: 'OTHER' } }],
  ['campaign.myDonations', () => campaignApi.myDonations(), '/api/donations/mine', undefined],
  ['campaign.callback', () => campaignApi.paymentCallback({ success: true }), '/api/payments/callback', { method: 'POST', body: { success: true } }],
  ['campaign.mine', () => campaignApi.mine(), '/api/org/campaigns', undefined],
  ['campaign.create', () => campaignApi.create(form), '/api/org/campaigns', { method: 'POST', form }],
  ['campaign.update', () => campaignApi.update('2', { name: 'n' }), '/api/org/campaigns/2', { method: 'PUT', body: { name: 'n' } }],
  ['campaign.avatar', () => campaignApi.setAvatar('2', form), '/api/org/campaigns/2/avatar', { method: 'PUT', form }],
  ['campaign.removeImage', () => campaignApi.removeImage('2', '8'), '/api/org/campaigns/2/images/8', { method: 'DELETE' }],
  ['campaign.addImages', () => campaignApi.addImages('2', form), '/api/org/campaigns/2/images', { method: 'POST', form }],
  ['campaign.end', () => campaignApi.end('2'), '/api/org/campaigns/2/end', { method: 'POST' }],
  ['campaign.extend', () => campaignApi.extend('2', '2026-12-01T00:00:00.000Z'), '/api/org/campaigns/2/extend', { method: 'POST', body: { endDate: '2026-12-01T00:00:00.000Z' } }],

  ['admin.sosPage', () => adminApi.sosPage({ status: 'ENDED', level: '', sortBy: 'time', dir: 'desc', page: 1 }), '/api/admin/sos?status=ENDED&sortBy=time&dir=desc&page=1&size=20', undefined],
  ['admin.heatmap', () => adminApi.heatmap(), '/api/admin/sos/heatmap', undefined],
  ['admin.sosMap', () => adminApi.sosMap({ status: 'ALL', level: '', days: 7 }), '/api/admin/sos/map?status=ALL&days=7', undefined],
  ['admin.sosMap level', () => adminApi.sosMap({ status: 'ACTIVE', level: 'CAO', days: 30 }), '/api/admin/sos/map?status=ACTIVE&level=CAO&days=30', undefined],
  ['admin.sosDetail', () => adminApi.sosDetail('4'), '/api/admin/sos/4', undefined],
  ['admin.candidates', () => adminApi.candidates('4'), '/api/admin/sos/4/candidates', undefined],
  ['admin.nearby', () => adminApi.nearby('4'), '/api/admin/sos/4/nearby', undefined],
  ['admin.assign', () => adminApi.assign('4', 11), '/api/admin/sos/4/assign', { method: 'POST', body: { orgId: 11 } }],
  ['admin.adjust', () => adminApi.adjustLevel('4', 'CAO'), '/api/admin/sos/4/adjust-level', { method: 'POST', body: { newLevel: 'CAO' } }],
  ['admin.adjust + ghi chú xác minh', () => adminApi.adjustLevel('4', 'CAO', 'Đã gọi, nhà ngập 1 m'), '/api/admin/sos/4/adjust-level', { method: 'POST', body: { newLevel: 'CAO', note: 'Đã gọi, nhà ngập 1 m' } }],
  ['admin.confirmVerification', () => adminApi.confirmVerification('4'), '/api/admin/sos/4/confirm-verification', { method: 'POST' }],
  ['admin.markInvalid', () => adminApi.markInvalid('4', 'Tin rác'), '/api/admin/sos/4/mark-invalid', { method: 'POST', body: { reason: 'Tin rác' } }],
  ['admin.completeSos', () => adminApi.completeSos('4', 'Người dân đã an toàn'), '/api/admin/sos/4/complete', { method: 'POST', body: { reason: 'Người dân đã an toàn' } }],
  ['admin.admins', () => adminApi.admins(), '/api/admin/admins', undefined],
  ['admin.createAdmin', () => adminApi.createAdmin({ fullName: 'A', email: 'a@x.vn', phone: '0912345678' }), '/api/admin/admins',
    { method: 'POST', body: { fullName: 'A', email: 'a@x.vn', phone: '0912345678' } }],
  ['admin.resendAdminInvite', () => adminApi.resendAdminInvite(7), '/api/admin/admins/7/resend-invite', { method: 'POST' }],
  ['admin.disableAdmin', () => adminApi.disableAdmin(7, 'Hết nhiệm kỳ'), '/api/admin/admins/7/disable', { method: 'POST', body: { reason: 'Hết nhiệm kỳ' } }],
  ['admin.enableAdmin', () => adminApi.enableAdmin(7), '/api/admin/admins/7/enable', { method: 'POST' }],
  ['admin.unassign', () => adminApi.unassign('4', 'Đội không có xuồng'), '/api/admin/sos/4/unassign', { method: 'POST', body: { reason: 'Đội không có xuồng' } }],
  ['admin.citizenStats', () => adminApi.citizenStats(), '/api/admin/stats/citizens', undefined],
  ['admin.sosStats', () => adminApi.sosStats('month'), '/api/admin/stats/sos?period=month', undefined],
  ['admin.sosStatusStats', () => adminApi.sosStatusStats('year'), '/api/admin/stats/sos-status?period=year', undefined],
  ['admin.sosTimeline', () => adminApi.sosTimeline(30), '/api/admin/stats/sos-timeline?days=30', undefined],
  ['admin.overview', () => adminApi.overview(), '/api/admin/stats/overview', undefined],
  ['admin.orgStats', () => adminApi.orgStats(), '/api/admin/stats/orgs', undefined],
  ['admin.topOrgs', () => adminApi.topOrgs(), '/api/admin/stats/top-orgs', undefined],
  ['admin.topDonors', () => adminApi.topDonors(), '/api/admin/stats/top-donors', undefined],
  ['admin.orgs', () => adminApi.orgs('NEW'), '/api/admin/orgs?filter=NEW', undefined],
  ['admin.orgDetail', () => adminApi.orgDetail('6'), '/api/admin/orgs/6', undefined],
  ['admin.reviewOrg', () => adminApi.reviewOrg('6', { decision: 'APPROVED' }), '/api/admin/orgs/6/review', { method: 'POST', body: { decision: 'APPROVED' } }],
  ['admin.lockOrg', () => adminApi.lockOrg('6', { months: 1 }), '/api/admin/orgs/6/lock', { method: 'POST', body: { months: 1 } }],
  ['admin.unlockOrg', () => adminApi.unlockOrg('6'), '/api/admin/orgs/6/unlock', { method: 'POST' }],
  ['admin.citizens', () => adminApi.citizens('LOCKED'), '/api/admin/citizens?filter=LOCKED', undefined],
  ['admin.citizenDetail', () => adminApi.citizenDetail('7'), '/api/admin/citizens/7', undefined],
  ['admin.lockCitizen', () => adminApi.lockCitizen('7', { months: 2 }), '/api/admin/citizens/7/lock', { method: 'POST', body: { months: 2 } }],
  ['admin.unlockCitizen', () => adminApi.unlockCitizen('7'), '/api/admin/citizens/7/unlock', { method: 'POST' }],
  ['admin.reportedPosts', () => adminApi.reportedPosts(2), '/api/admin/post-reports/posts?page=2&size=20', undefined],
  ['admin.postReports', () => adminApi.postReports(), '/api/admin/post-reports?page=0&size=20', undefined],
  ['admin.postReportDetail', () => adminApi.postReportDetail('3'), '/api/admin/posts/3/reports', undefined],
  ['admin.moderatePost', () => adminApi.moderatePost('3', { decision: 'KEEP' }), '/api/admin/posts/3/moderate', { method: 'POST', body: { decision: 'KEEP' } }],
  ['admin.reportedCampaigns', () => adminApi.reportedCampaigns(1), '/api/admin/campaign-reports/campaigns?page=1&size=20', undefined],
  ['admin.campaignReports', () => adminApi.campaignReports(), '/api/admin/campaign-reports?page=0&size=20', undefined],
  ['admin.campaignReportDetail', () => adminApi.campaignReportDetail('2'), '/api/admin/campaigns/2/reports', undefined],
  ['admin.moderateCampaign', () => adminApi.moderateCampaign('2', { decision: 'REMOVE' }), '/api/admin/campaigns/2/moderate', { method: 'POST', body: { decision: 'REMOVE' } }],

  ['geo.locate', () => geoApi.locate(21.0368, 105.8346), '/api/geo/locate?lat=21.0368&lng=105.8346', { auth: false }],
  ['geo.ward', () => geoApi.ward('00004'), '/api/geo/wards/00004', { auth: false }],
];

describe('services/api: đường dẫn và tham số gửi tới backend', () => {
  beforeEach(() => api.mockClear());
  it.each(CASES)('%s', async (_name, call, path, opts) => {
    await call();
    expect(api).toHaveBeenCalledTimes(1);
    if (opts === undefined) expect(api.mock.calls[0]).toEqual([path]);
    else expect(api.mock.calls[0]).toEqual([path, opts]);
  });
});
