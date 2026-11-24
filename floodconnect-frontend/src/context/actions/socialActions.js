import { jsonPart } from '@/services/http';
import { notificationApi, postApi, userApi } from '@/services/api';
import { POST_REASON_CODE, validateCoords, wardName } from '@/utils';
import { putPost, putComment, isLocked, lockedMsg } from '../draft';

/*
 * Vị trí bài đăng (không bắt buộc): { provinceCode, wardCode, detail (thôn/xóm, số nhà, đường), gps: { lat, lng } | null }.
 * Người dân không nhập tọa độ: backend tự lấy điểm đại diện của xã, hoặc GPS nếu người đăng bấm "Vị trí GPS" và GPS nằm đúng xã.
 */
const MAX_DETAIL = 100;
function locationError(loc) {
  if (!loc) return null;
  if (!loc.provinceCode || !loc.wardCode) return 'Vui lòng chọn Tỉnh/Thành phố và Phường/Xã cho vị trí, hoặc bấm × để bỏ vị trí.';
  if (!wardName(loc.provinceCode, loc.wardCode)) return 'Phường/Xã không thuộc Tỉnh/Thành phố đã chọn.';
  if ((loc.detail || '').trim().length > MAX_DETAIL) return `Thôn/xóm, số nhà, đường tối đa ${MAX_DETAIL} ký tự.`;
  return null;
}
/* GPS sai định dạng thì bỏ, backend dùng điểm đại diện của xã */
const locationPart = (loc) => {
  const gps = loc?.gps && !validateCoords(loc.gps.lat, loc.gps.lng) ? loc.gps : null;
  return { wardCode: loc?.wardCode || null, addressDetail: loc?.detail?.trim() || null, latitude: gps?.lat ?? null, longitude: gps?.lng ?? null };
};

/** Mạng xã hội: bài đăng, thích, bình luận, báo cáo, theo dõi, đánh dấu đã đọc thông báo. */
export function createSocialActions({ mutate, run, ref, me }) {
  return {
    async createPost({ content, images, location }) {
      if (isLocked(me)) return lockedMsg;
      const c = content.trim();
      if (c.length < 1) return 'Nội dung bài đăng không được để trống.';
      if (c.length > 2000) return 'Nội dung tối đa 2.000 ký tự.';
      if (images.length > 5) return 'Tối đa 5 tệp đính kèm.';
      const le = locationError(location);
      if (le) return le;
      return run(async () => {
        const fd = new FormData();
        fd.append('data', jsonPart({ content: c, ...locationPart(location) }));
        images.forEach((im) => im.file && fd.append('media', im.file));
        const p = await postApi.create(fd);
        mutate((d) => { putPost(d, p); });
      });
    },
    async editPost(id, { content, images, location }) {
      const c = content.trim();
      if (!c) return 'Nội dung bài đăng không được để trống.';
      if (c.length > 2000) return 'Nội dung tối đa 2.000 ký tự.';
      if (images.length > 5) return 'Tối đa 5 tệp đính kèm sau chỉnh sửa.';
      const le = locationError(location);
      if (le) return le;
      const old = ref.current.posts.find((p) => p.id === id);
      const keep = new Set(images.filter((im) => im.id && !im.file).map((im) => im.id));
      const removeMediaIds = (old?.images || []).filter((im) => !keep.has(im.id)).map((im) => im.id);
      return run(async () => {
        const fd = new FormData();
        fd.append('data', jsonPart({ content: c, ...locationPart(location), removeMediaIds }));
        images.forEach((im) => im.file && fd.append('media', im.file));
        const p = await postApi.update(id, fd);
        mutate((d) => { putPost(d, p); });
      });
    },
    deletePost(id) {
      return run(async () => {
        await postApi.remove(id);
        mutate((d) => { const p = d.posts.find((x) => x.id === id); if (p) p.status = 'DELETED'; });
      });
    },
    toggleLike(postId) {
      if (isLocked(me)) return lockedMsg;
      return run(async () => {
        const r = await postApi.like(postId);
        mutate((d) => { const p = d.posts.find((x) => x.id === postId); if (p) { p.likedByMe = r.liked; p.likeCount = r.likeCount; } });
      });
    },
    async addComment(postId, parentId, content) {
      if (isLocked(me)) return lockedMsg;
      const c = content.trim();
      if (!c) return 'Bình luận không được để trống.';
      if (c.length > 1000) return 'Bình luận tối đa 1.000 ký tự.';
      return run(async () => {
        const r = await postApi.addComment(postId, { content: c, parentCommentId: parentId ? Number(parentId) : null });
        mutate((d) => {
          putComment(d, r);
          const p = d.posts.find((x) => x.id === postId); if (p) p.commentCount += 1;
          // số phản hồi của bình luận gốc dùng cho "Xem N phản hồi" (phản hồi chỉ tải khi mở nên không đếm được trên danh sách đã tải)
          const parent = parentId && d.comments.find((x) => x.id === parentId); if (parent) parent.replyCount = (parent.replyCount || 0) + 1;
        });
      });
    },
    async editComment(id, content) {
      const t = content.trim();
      if (!t) return 'Bình luận không được để trống.';
      if (t.length > 1000) return 'Bình luận tối đa 1.000 ký tự.';
      return run(async () => {
        const r = await postApi.editComment(id, { content: t });
        mutate((d) => { putComment(d, r); });
      });
    },
    deleteComment(id) {
      return run(async () => {
        await postApi.deleteComment(id);
        mutate((d) => {
          const c = d.comments.find((x) => x.id === id);
          if (c) {
            c.status = 'DELETED'; const p = d.posts.find((x) => x.id === c.postId); if (p && p.commentCount > 0) p.commentCount -= 1;
            const parent = c.parentId && d.comments.find((x) => x.id === c.parentId); if (parent && parent.replyCount > 0) parent.replyCount -= 1;
          }
        });
      });
    },
    async reportPost(postId, reasonLabel, desc) {
      if (isLocked(me)) return lockedMsg;
      try {
        await postApi.report(postId, { reason: POST_REASON_CODE[reasonLabel] || 'OTHER', description: (desc || '').trim() });
        mutate((d) => { const p = d.posts.find((x) => x.id === postId); if (p) p.reportedByMe = true; });
        return null;
      } catch (e) {
        if (e.code === 'ALREADY_REPORTED') mutate((d) => { const p = d.posts.find((x) => x.id === postId); if (p) p.reportedByMe = true; });
        return e.message;
      }
    },
    toggleFollow(targetId) {
      const cur = ref.current.profiles[targetId]?.followedByMe;
      return run(async () => {
        await (cur ? userApi.unfollow(targetId) : userApi.follow(targetId));
        mutate((d) => {
          const p = d.profiles[targetId] || {};
          d.profiles[targetId] = { ...p, followedByMe: !cur, followers: Math.max(0, (p.followers || 0) + (cur ? -1 : 1)) };
        });
      });
    },
    markRead(id) {
      mutate((d) => { const n = d.notifications.find((x) => x.id === id); if (n) n.read = true; });
      notificationApi.markRead(id).catch(() => {});
    },
    markAllRead() {
      mutate((d) => { d.notifications.forEach((n) => { n.read = true; }); });
      notificationApi.markAllRead().catch(() => {});
    },
  };
}
