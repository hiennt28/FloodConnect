import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useApp } from '@/hooks';
import PostCard from '@/components/post/PostCard';
import { AvatarZoom, Empty, SkeletonDetail, Tag } from '@/components/common';
import { FollowButton } from '@/components/user/FollowButton';
import { ORG_TYPES, ROLE_LABEL } from '@/utils';
import { orgArea } from '@/services/api/mappers';

/* ================= PUBLIC PROFILE ================= */
export function UserProfile() {
  const { id } = useParams();
  const { state, me, load } = useApp();
  const [loading, setLoading] = useState(true);
  useEffect(() => { setLoading(true); Promise.all([load.profile(id), load.userPosts(id)]).then(() => setLoading(false)); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  const u = state.users.find((x) => x.id === id);
  if (!u) return loading ? <div className="narrow"><SkeletonDetail avatar label="Đang tải hồ sơ…" /></div> : <Empty title="Không tìm thấy người dùng" />;
  const org = u.orgId && state.orgs.find((o) => o.id === u.orgId);
  const pr = state.profiles[u.id] || {};
  const posts = state.posts.filter((p) => p.authorId === u.id && p.status === 'VISIBLE').sort((a, b) => b.createdAt - a.createdAt);
  return (
    <div className="narrow stack">
      <div className="card profile-hero">
        <AvatarZoom user={u} size={96} title={org ? 'Logo tổ chức' : 'Ảnh đại diện'} />
        <div className="grow">
          <h1>{org ? org.name : u.name}</h1>
          <div className="muted">{org ? `Tổ chức • ${ORG_TYPES[org.type]?.label}` : ROLE_LABEL[u.role]} • {pr.followers ?? 0} người theo dõi • {posts.length} bài đăng</div>
          {org && (
            <div className="org-info">
              {orgArea(org) && <span><b>Khu vực trụ sở:</b> {orgArea(org)}</span>}
              {org.description && <span><b>Mô tả:</b> {org.description}</span>}
              {/^https?:\/\//i.test(org.website || '') && <span><b>Trang web:</b> <a href={org.website} target="_blank" rel="noopener noreferrer nofollow">{org.website}</a></span>}
              <span><b>Trạng thái:</b> <Tag tone={org.opStatus === 'READY' ? 'green' : org.opStatus === 'BUSY' ? 'orange' : 'gray'}>{{ READY: 'Sẵn sàng', BUSY: 'Đang bận', INACTIVE: 'Không hoạt động' }[org.opStatus]}</Tag></span>
            </div>
          )}
        </div>
        {me && me.id !== u.id && <FollowButton userId={u.id} name={org ? org.name : u.name} />}
        {me?.id === u.id && <Link className="btn btn-soft" to="/profile">Chỉnh sửa hồ sơ</Link>}
      </div>
      {posts.length === 0 ? <Empty title="Chưa có bài đăng nào" /> : posts.map((p) => <PostCard key={p.id} post={p} />)}
    </div>
  );
}
