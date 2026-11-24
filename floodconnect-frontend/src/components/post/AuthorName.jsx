import { Link } from 'react-router-dom';
import { Tag } from '@/components/common';
import { ROLE_LABEL } from '@/utils';

export function AuthorName({ user }) {
  const org = user?.role === 'RESCUE_ORG' && user.orgName;
  if (!user) return null;
  return (
    <span className="author">
      <Link to={`/users/${user.id}`}><b>{org || user.name}</b></Link>
      {org ? <Tag tone="blue">Tổ chức</Tag> : user.role === 'ADMIN' ? <Tag tone="gray">{ROLE_LABEL.ADMIN}</Tag> : null}
    </span>
  );
}
