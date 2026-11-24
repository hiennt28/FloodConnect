import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { SearchX } from 'lucide-react';
import { useApp, useOnceParam } from '@/hooks';
import PostCard from '@/components/post/PostCard';
import { Empty, SkeletonPost } from '@/components/common';

export function PostDetail() {
  const { id } = useParams();
  const { state, load } = useApp();
  const [loading, setLoading] = useState(true);
  const focus = useOnceParam('comment'); // mở từ thông báo bình luận/phản hồi
  useEffect(() => { setLoading(true); load.post(id).then(() => setLoading(false)); }, [id]); // eslint-disable-line react-hooks/exhaustive-deps
  const post = state.posts.find((p) => p.id === id);
  if (loading && !post) return <div className="narrow"><SkeletonPost /></div>;
  if (!post || post.status !== 'VISIBLE') return <Empty icon={<SearchX size={34} />} title="Bài viết không tồn tại hoặc đã bị gỡ"><Link to="/feed">Về bảng tin</Link></Empty>;
  return <div className="narrow"><PostCard post={post} expanded focus={focus} /></div>;
}
