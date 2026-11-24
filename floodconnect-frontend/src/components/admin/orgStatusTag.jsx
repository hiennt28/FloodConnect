import { Tag } from '@/components/common';

export const orgStatusTag = (st) => {
  const m = { PENDING: ['yellow', 'Chờ duyệt'], REJECTED: ['red', 'Bị từ chối'], ACTIVE: ['green', 'Đã duyệt'], LOCKED: ['red', 'Bị khóa'] }[st] || ['gray', st];
  return <Tag tone={m[0]}>{m[1]}</Tag>;
};
