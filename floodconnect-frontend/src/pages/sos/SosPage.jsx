import { useEffect, useState } from 'react';
import { PhoneCall, Siren } from 'lucide-react';
import { useApp, useCaseParam } from '@/hooks';
import { Empty, SkeletonLines, Tabs } from '@/components/common';
import { ACTIVE_SOS, HOTLINE, SOS_STATUS } from '@/utils';
import { SosForm } from '@/components/sos/SosForm';
import { SosDetail } from '@/components/sos/SosDetail';
import { SosPublicCard } from '@/components/sos/SosPublicCard';

const DONE_PAGE = 10; // số ca Hoàn thành mỗi lần "Tải thêm"

export default function SosPage() {
  const { state, me, sos, toast } = useApp();
  const [tab, setTab] = useState('mine');
  const [filter, setFilter] = useState('');
  const [highlight, setHighlight] = useState(null);
  const [focusKey, setFocusKey] = useState(0); // mở lại cùng một ca (bấm thông báo lần nữa) vẫn cuộn tới
  const mine = state.sos.filter((s) => s.mine).sort((a, b) => b.createdAt - a.createdAt);
  const visibleMine = mine;
  /* Tab "Tất cả": trên cùng là MỌI ca chưa xong (Chờ xử lý, Đã phân công, Đã tiếp nhận; luôn đủ, kể cả ca gửi đã lâu),
     bên dưới là phần "Đã hoàn thành" tải theo trang (10 ca mỗi lần "Tải thêm"). Ca đã hủy, không hợp lệ, chờ xác minh không hiện */
  const showActive = !filter || ACTIVE_SOS.includes(filter);
  const showDone = !filter || filter === 'DONE';
  const all = showActive ? state.sos.filter((s) => s.severity !== 'KHONG' && ACTIVE_SOS.includes(s.status) && (!filter || s.status === filter)).sort((a, b) => b.createdAt - a.createdAt) : [];
  const [done, setDone] = useState(null); // { items, total, last } các ca Hoàn thành đã tải; null = chưa tải
  const [doneLoading, setDoneLoading] = useState(false);
  const loadDone = async (reset) => {
    setDoneLoading(true);
    const page = reset || !done ? 0 : Math.ceil(done.items.length / DONE_PAGE);
    const r = await sos.casesPage('public', { status: 'COMPLETED', page, size: DONE_PAGE });
    setDoneLoading(false);
    if (!r) return;
    setDone((d) => {
      const prev = reset || !d ? [] : d.items;
      const seen = new Set(prev.map((x) => x.id));
      return { items: [...prev, ...r.items.filter((x) => !seen.has(x.id))], total: r.total, last: r.last };
    });
  };
  useEffect(() => { if (tab === 'all' && showDone && !done) loadDone(true); }, [tab, showDone]); // eslint-disable-line react-hooks/exhaustive-deps
  const shown = tab === 'mine' ? visibleMine.filter((s) => !filter || s.status === filter) : all;
  const doneShown = tab === 'all' && showDone ? done?.items || [] : [];
  /* Ca vừa gửi hoặc vừa mở từ tab "Tất cả": cuộn tới thẻ đó */
  useEffect(() => { if (highlight) document.getElementById(`sos-${highlight}`)?.scrollIntoView?.({ behavior: 'smooth', block: 'center' }); }, [highlight, tab, focusKey]);
  /* Mở từ thông báo (?case=): tab "SOS của tôi", bỏ bộ lọc trạng thái, cuộn tới và làm nổi bật ca */
  useCaseParam((id) => {
    if (!mine.some((s) => s.id === id)) return false;
    setTab('mine'); setFilter(''); setHighlight(id); setFocusKey((k) => k + 1);
    return true;
  }, () => toast('Không tìm thấy ca SOS này.', 'err'));

  return (
    <div className="narrow stack">
      <div className="sos-bar">
        <span className="live-dot" /> <b>Kênh tiếp nhận khẩn cấp trực tuyến 24/7</b>
        <span className="muted small">Ưu tiên người cao tuổi, trẻ nhỏ và khu vực cô lập</span>
        <a className="tag tag-red" href={`tel:${HOTLINE.number}`}><PhoneCall size={13} /> {HOTLINE.number}</a>
      </div>
      <SosForm onCreated={(id) => { setTab('mine'); setHighlight(id); }} />

      <div className="row between wrap gap8">
        <h2 className="h2">Yêu cầu cứu trợ & trạng thái xử lý</h2>
        <div className="row gap8">
          <Tabs value={tab} onChange={setTab} tabs={[{ value: 'mine', label: 'SOS của tôi', count: visibleMine.length }, { value: 'all', label: 'Tất cả ca SOS' }]} />
          {/* Tab "Tất cả" chỉ có ca chưa xong và ca Hoàn thành (ca đã hủy / không hợp lệ không công khai) */}
          <select className="input sm" value={filter} onChange={(e) => setFilter(e.target.value)} aria-label="Trạng thái"><option value="">Mọi trạng thái</option>{Object.entries(SOS_STATUS).filter(([k]) => tab === 'mine' || ACTIVE_SOS.includes(k) || k === 'DONE').map(([k, v]) => <option key={k} value={k}>{v.label}</option>)}</select>
        </div>
      </div>
      {tab === 'mine' && visibleMine.length === 0 && <Empty icon={<Siren size={34} />} title="Bạn chưa có ca SOS" />}
      {tab === 'all' && (shown.length > 0 || doneShown.length > 0) && <span className="muted small">Ca của người khác chỉ hiện mã ca, mức độ, trạng thái, khoảng cách và tổ chức phụ trách. Mô tả và địa chỉ chỉ người gửi, tổ chức cứu hộ và Admin xem được.</span>}
      {tab === 'mine' && shown.length === 0 && visibleMine.length > 0 && <Empty title="Không tìm thấy ca SOS phù hợp" />}
      {tab === 'all' && showActive && <h3 className="h3">Đang cần cứu hộ{shown.length > 0 ? ` (${shown.length})` : ''}</h3>}
      {tab === 'all' && showActive && shown.length === 0 && <span className="muted small">Hiện không có ca nào đang chờ hoặc đang được cứu hộ.</span>}
      {/* Tab "SOS của tôi": thẻ đầy đủ; tab "Tất cả": mọi ca (kể cả ca của mình) dùng thẻ rút gọn để dễ so sánh */}
      {shown.map((s) => <div key={s.id} id={`sos-${s.id}`} className={highlight === s.id ? 'flash' : ''}>{tab === 'mine' ? <SosDetail s={s} /> : <SosPublicCard s={s} onOpenMine={(id) => { setTab('mine'); setHighlight(id); }} />}</div>)}
      {tab === 'all' && showDone && (
        <>
          <h3 className="h3">Đã hoàn thành{done ? ` (${done.total})` : ''}</h3>
          {!done && <SkeletonLines lines={2} label="Đang tải ca đã hoàn thành…" />}
          {done && done.items.length === 0 && <span className="muted small">Chưa có ca nào hoàn thành.</span>}
          {doneShown.map((s) => <div key={s.id}><SosPublicCard s={s} onOpenMine={(id) => { setTab('mine'); setHighlight(id); }} /></div>)}
          {done && !done.last && <button className="btn btn-ghost" disabled={doneLoading} onClick={() => loadDone(false)}>{doneLoading ? 'Đang tải…' : 'Tải thêm'}</button>}
        </>
      )}
    </div>
  );
}
