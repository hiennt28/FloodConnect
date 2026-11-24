export const Empty = ({ icon, title, children }) => (
  <div className="empty">
    {icon}
    <strong>{title}</strong>
    {children && <div className="muted">{children}</div>}
  </div>
);
