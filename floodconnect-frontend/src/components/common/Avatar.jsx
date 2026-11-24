const COLORS = ['#0e5868', '#7a5a2b', '#5a4a7d', '#2e6b4f', '#8b3d3d', '#3c5a7f'];
export function Avatar({ user, size = 40 }) {
  if (!user) return <span className="avatar" style={{ width: size, height: size }} />;
  if (user.avatar) return <img className="avatar" src={user.avatar} alt="" style={{ width: size, height: size }} />;
  const initials = user.name.split(' ').slice(-2).map((w) => w[0]).join('').toUpperCase();
  const color = COLORS[user.id.charCodeAt(user.id.length - 1) % COLORS.length];
  return <span className="avatar" style={{ width: size, height: size, background: color, fontSize: size * 0.38 }}>{initials}</span>;
}
