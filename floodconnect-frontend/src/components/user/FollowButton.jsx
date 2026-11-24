import { useState } from 'react';
import { useApp } from '@/hooks';
import { Confirm } from '@/components/common';

/* Theo dõi: bấm là theo dõi ngay. Bỏ theo dõi: hỏi xác nhận trước (tránh bấm nhầm).
   Đang theo dõi thì khi rê chuột/focus nút đổi chữ thành "Bỏ theo dõi" để người dùng biết bấm sẽ làm gì */
export function FollowButton({ userId, name, small }) {
  const { state, social, toast } = useApp();
  const [confirm, setConfirm] = useState(false);
  const following = !!state.profiles[userId]?.followedByMe;
  const toggle = async () => { const r = await social.toggleFollow(userId); if (r) toast(r, 'err'); };
  return (
    <>
      <button className={`btn ${small ? 'btn-sm' : ''} ${following ? 'btn-ghost follow-on' : 'btn-primary'}`} onClick={() => (following ? setConfirm(true) : toggle())}>
        {following ? <><span className="follow-label">Đang theo dõi</span><span className="unfollow-label">Bỏ theo dõi</span></> : 'Theo dõi'}
      </button>
      {confirm && <Confirm title="Bỏ theo dõi?" okText="Bỏ theo dõi" danger onClose={() => setConfirm(false)} onOk={toggle}
        text={`Bạn có chắc muốn bỏ theo dõi ${name}? Bài viết của ${name} sẽ không còn hiện trong tab “Đang theo dõi”.`} />}
    </>
  );
}
