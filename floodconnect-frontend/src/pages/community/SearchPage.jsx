import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { SearchX } from 'lucide-react';
import { useApp } from '@/hooks';
import { Avatar, Empty, Tabs } from '@/components/common';
import { ORG_TYPES } from '@/utils';
import { FollowButton } from '@/components/user/FollowButton';

/* ================= SEARCH ================= */
export function SearchPage() {
  const { state, me, load } = useApp();
  const [sp] = useSearchParams();
  const q = sp.get('q') || '';
  const [kind, setKind] = useState('all');
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let live = true;
    setLoading(true);
    const types = kind === 'all' ? ['CITIZEN', 'ORG'] : [kind === 'org' ? 'ORG' : 'CITIZEN'];
    Promise.all(types.map((t) => load.search(q, t))).then((r) => { if (live) { setRows(r.flat()); setLoading(false); } });
    return () => { live = false; };
  }, [q, kind]); // eslint-disable-line react-hooks/exhaustive-deps

  return (
    <div className="narrow stack">
      <h1 className="page-title">Kết quả tìm kiếm {q && <span className="muted">“{q}”</span>}</h1>
      <span className="muted small">Tìm theo tên người dân hoặc tên tổ chức; gõ có dấu hay không dấu, chữ hoa hay thường đều được.</span>
      <div className="row gap8 wrap">
        <Tabs value={kind} onChange={setKind} tabs={[{ value: 'all', label: 'Tất cả' }, { value: 'citizen', label: 'Người dân' }, { value: 'org', label: 'Tổ chức' }]} />
      </div>
      {!loading && rows.length === 0 && <Empty icon={<SearchX size={34} />} title="Không tìm thấy kết quả phù hợp" />}
      {rows.map(({ userId, orgId }) => {
        const u = state.users.find((x) => x.id === userId);
        if (!u) return null;
        const org = orgId && state.orgs.find((o) => o.id === orgId);
        return (
          <div key={userId} className="card pad row gap12">
            <Avatar user={u} size={48} />
            <div className="grow">
              <Link to={`/users/${u.id}`}><b>{org ? org.name : u.name}</b></Link>
              <div className="muted small">{org ? `Tổ chức • ${ORG_TYPES[org.type]?.label}` : 'Người dân'}</div>
            </div>
            {me && me.id !== u.id && <FollowButton userId={u.id} name={org ? org.name : u.name} small />}
          </div>
        );
      })}
    </div>
  );
}
