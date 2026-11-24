import { adminApi, campaignApi, notificationApi, postApi, publicApi, sosApi, userApi } from '@/services/api';
import { mComment, mDonation, mNotif, mOrgPublic, mSos, mUserSummary, sid, SOS_STATUS_TO_API } from '@/services/api/mappers';
import { upBy, upUser, putPost, putComment, putCampaign } from '../draft';

/** Tải dữ liệu theo trang (bảng tin, hồ sơ công khai, tìm kiếm, chiến dịch, bản đồ) và gộp vào state. */
export function createLoadActions({ mutate, refresh, quiet }) {
  return {
    refresh,
    async feed(filter = 'all', page = 0, size = 10) {
      const r = await quiet(() => postApi.feed(filter, page, size));
      if (!r) return { ids: [], last: true };
      mutate((d) => { r.items.forEach((p) => putPost(d, p)); });
      return { ids: r.items.map((p) => sid(p.id)), last: r.last };
    },
    async post(id) {
      const p = await quiet(() => postApi.get(id));
      if (p) mutate((d) => { putPost(d, p); });
      return !!p;
    },
    async userPosts(id, page = 0) {
      const r = await quiet(() => userApi.posts(id, page));
      if (!r) return { ids: [], last: true };
      mutate((d) => { r.items.forEach((p) => putPost(d, p)); });
      return { ids: r.items.map((p) => sid(p.id)), last: r.last };
    },
    /* Bình luận gốc theo trang (20 mới nhất trước). Phản hồi KHÔNG tải kèm (trước đây mỗi bình luận có phản hồi tốn thêm một
       request): chỉ tải khi người dùng bấm "Xem N phản hồi" (replies). Trả { last } hoặc null nếu lỗi */
    async comments(postId, page = 0) {
      const r = await quiet(() => postApi.comments(postId, page));
      if (!r) return null;
      mutate((d) => { r.items.forEach((c) => putComment(d, c)); });
      return { last: r.last };
    },
    async replies(commentId, page = 0) {
      const r = await quiet(() => postApi.replies(commentId, page));
      if (!r) return null;
      mutate((d) => { r.items.forEach((c) => putComment(d, c)); });
      return { last: r.last };
    },
    /* Một bình luận theo mã (mở từ thông báo); null nếu không còn (bài bị gỡ, bình luận không tồn tại) */
    async comment(id) {
      const c = await quiet(() => postApi.comment(id));
      if (!c) return null;
      mutate((d) => { putComment(d, c); });
      return mComment(c);
    },
    async profile(id) {
      const p = await quiet(() => userApi.profile(id));
      if (!p) return null;
      mutate((d) => {
        const orgId = p.organization ? sid(p.organization.id) : null;
        upUser(d, { ...mUserSummary(p.user), orgId, createdAt: Date.parse(p.joinedAt) });
        if (p.organization) upBy(d.orgs, mOrgPublic(p.organization, { userId: sid(p.user.id) }));
        d.profiles[sid(p.user.id)] = { followers: p.followers, following: p.following, followedByMe: p.followedByMe };
      });
      return p;
    },
    async search(q, type) {
      const r = await quiet(() => userApi.search(q, type, 30));
      if (!r) return [];
      mutate((d) => {
        r.items.forEach((x) => {
          upUser(d, { ...mUserSummary(x.user), orgId: x.organization ? sid(x.organization.id) : null });
          if (x.organization) upBy(d.orgs, mOrgPublic(x.organization, { userId: sid(x.user.id) }));
          d.profiles[sid(x.user.id)] = { ...(d.profiles[sid(x.user.id)] || {}), followedByMe: x.followedByMe };
        });
      });
      return r.items.map((x) => ({ userId: sid(x.user.id), orgId: x.organization ? sid(x.organization.id) : null }));
    },
    /* Máy chủ đếm (trước đây đếm trong 100 tổ chức đầu tiên của danh bạ nên sai khi có hơn 100 tổ chức) */
    async readyOrgCount() {
      const r = await quiet(() => publicApi.availableOrgs());
      return r ? r.count : 0;
    },
    /* Danh sách chiến dịch theo trang ("Tải thêm", 12 mỗi lần): { ids, last, total } hoặc null nếu lỗi */
    async campaigns({ status = 'ACTIVE', q = '', page = 0, size = 12 } = {}) {
      const r = await quiet(() => campaignApi.list(status, q, page, size));
      if (!r) return null;
      mutate((d) => { r.items.forEach((c) => putCampaign(d, c)); });
      return { ids: r.items.map((c) => sid(c.id)), last: r.last, total: r.total };
    },
    /* Trang thông báo cũ hơn (trang 0 = 30 thông báo mới nhất đã đồng bộ cho chuông). role: để tạo đường dẫn đúng vai trò */
    async notifications(page, role) {
      const r = await quiet(() => notificationApi.list(page));
      return r ? { items: r.items.map((n) => mNotif(n, role)), last: r.last } : null;
    },
    async campaign(id) {
      const c = await quiet(() => campaignApi.get(id));
      if (c) mutate((d) => { putCampaign(d, c); });
      return !!c;
    },
    async statement(id, page = 0, size = 8) {
      const r = await quiet(() => campaignApi.donations(id, page, size));
      return r ? { items: r.items.map(mDonation), total: r.total } : { items: [], total: 0 };
    },
    async markers() { return quiet(() => sosApi.markers()); },
    /* Trang giới thiệu: lỗi thì trả null và ẩn khối số liệu, không hiện thông báo lỗi cho khách */
    async publicStats() { return quiet(() => publicApi.stats()); },
    async heatmap() { return (await quiet(() => adminApi.heatmap())) || []; },
    /* Bản đồ điều phối của Admin. status theo giao diện: 'ACTIVE' | '' (mọi trạng thái) | một trạng thái (DONE = COMPLETED).
       Ca chỉ có dữ liệu để vẽ điểm, không có mô tả: mở chi tiết thì dùng adminCase. Lỗi thì trả null (bản đồ giữ dữ liệu cũ) */
    async adminMap({ status = 'ACTIVE', level = '', days = 7 } = {}) {
      const r = await quiet(() => adminApi.sosMap({ status: status ? (SOS_STATUS_TO_API[status] || status) : 'ALL', level, days }));
      return r ? { cases: r.cases.map(mSos), total: r.total, limit: r.limit, days: r.days } : null;
    },
    async adminCase(id) {
      const r = await quiet(() => adminApi.sosDetail(id));
      return r ? mSos(r) : null;
    },
  };
}
