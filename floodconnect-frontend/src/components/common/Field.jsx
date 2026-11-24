/*
 * Mặc định bọc trong <label> để bấm vào nhãn là đưa con trỏ vào ô nhập.
 * group: bọc trong <div role="group"> khi bên trong là cả một cụm điều khiển (nút, bản đồ...). Lý do: bấm vào chỗ trống
 * bất kỳ trong <label> thì trình duyệt tự "bấm hộ" phần tử điều khiển đầu tiên bên trong nó (VD chạm bản đồ lại bấm nút GPS).
 */
export function Field({ label, error, hint, children, required, group = false }) {
  const Tag = group ? 'div' : 'label';
  return (
    <Tag className="field" role={group ? 'group' : undefined} aria-label={group && typeof label === 'string' ? label : undefined}>
      {label && <span className="field-label">{label}{required && <b className="req"> *</b>}</span>}
      {children}
      {hint && !error && <span className="field-hint">{hint}</span>}
      {error && <span className="field-error">{error}</span>}
    </Tag>
  );
}
