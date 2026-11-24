/* Ảnh minh họa: ảnh thật (dataURL) hoặc khối màu placeholder có chú thích */
const TINTS = {
  1: 'linear-gradient(135deg,#5b7a8c,#2d4a5c 60%,#8a6f4e)',
  2: 'linear-gradient(135deg,#7d6a4f,#4d5f6b 55%,#2f3f4a)',
  3: 'linear-gradient(135deg,#c08a52,#8a5a3a 50%,#4f3a2c)',
  4: 'linear-gradient(135deg,#4e6d7a,#7f9aa3 50%,#c8b48a)',
};
export function Photo({ img, className = '', style, children }) {
  if (img?.url) return <div className={`photo ${className}`} style={style}><img src={img.url} alt={img.caption || img.name || ''} />{children && <span className="photo-more">{children}</span>}</div>;
  return (
    <div className={`photo ph ${className}`} style={{ background: TINTS[img?.tint || 1], ...style }}>
      {children && <span className="photo-more">{children}</span>}
    </div>
  );
}
