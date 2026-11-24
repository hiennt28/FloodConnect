import { SEVERITY } from '@/utils';

/* Mức độ nguy hiểm dạng thước mực nước: số vạch tô = mức (Không = 0), đọc được cả khi không phân biệt màu */
export function Sev({ level, size }) {
  const v = SEVERITY[level];
  if (!v) return null;
  return (
    <span className={`sev ${size === 'lg' ? 'lg' : ''}`} style={{ '--sev': `var(--sev-${level}, ${v.color})` }} title={`Mức độ: ${v.label}`}>
      <span className="sev-bars" aria-hidden="true">{[1, 2, 3, 4].map((n) => <i key={n} className={n <= v.w ? 'on' : ''} />)}</span>
      {v.label}
    </span>
  );
}
