export function Progress({ value, max }) {
  const pct = Math.min(100, Math.round((value / max) * 100) || 0);
  return <div className="progress" aria-label={`${pct}%`}><span style={{ width: `${pct}%` }} /></div>;
}
