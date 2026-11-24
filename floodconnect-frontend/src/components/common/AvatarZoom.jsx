import { useState } from 'react';
import { Avatar } from './Avatar';
import { Modal } from './Modal';

/* Ảnh đại diện bấm được để xem ảnh lớn. Chưa có ảnh (chỉ hiện chữ cái đầu) thì không bấm được */
export function AvatarZoom({ user, size, title = 'Ảnh đại diện' }) {
  const [open, setOpen] = useState(false);
  if (!user?.avatar) return <Avatar user={user} size={size} />;
  const label = `Xem ${title.toLowerCase()}`;
  return (
    <>
      <button type="button" className="avatar-zoom" onClick={() => setOpen(true)} aria-label={label} title={label}><Avatar user={user} size={size} /></button>
      {open && <Modal title={title} onClose={() => setOpen(false)}><img className="avatar-full" src={user.avatar} alt={title} /></Modal>}
    </>
  );
}
