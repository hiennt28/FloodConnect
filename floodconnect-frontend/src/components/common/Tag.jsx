export const Tag = ({ tone = 'gray', children, dot }) => (
  <span className={`tag tag-${tone}`}>{dot && <i className="tag-dot" />}{children}</span>
);
