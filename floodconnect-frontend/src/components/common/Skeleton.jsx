/* Khung chờ (skeleton): vẽ trước hình dạng nội dung trong lúc dữ liệu CHƯA về.
   Dữ liệu đã về mà rỗng thì dùng <Empty>, không dùng khung chờ: hai trạng thái này khác nhau.
   Mỗi khung có role="status" + chữ ẩn cho trình đọc màn hình; các thanh mờ chỉ để nhìn (aria-hidden). */

const WIDTHS = ['92%', '78%', '85%', '64%', '88%', '70%'];

/* Một thanh mờ */
export const Bone = ({ w = '100%', h = 12, round = false, style }) => (
  <span className={`sk${round ? ' sk-round' : ''}`} style={{ width: w, height: h, ...style }} aria-hidden="true" />
);

/* Vỏ chung: thông báo "đang tải" cho trình đọc màn hình.
   label="" cho các khung lặp lại (khung thứ 2, 3…) để trình đọc màn hình chỉ đọc một lần. */
export const Skeleton = ({ label = 'Đang tải…', className = '', children }) => (label ? (
  <div className={`sk-wrap ${className}`} role="status" aria-busy="true" aria-live="polite">
    <span className="sr-only">{label}</span>
    {children}
  </div>
) : <div className={`sk-wrap ${className}`} aria-hidden="true">{children}</div>);

/* Vài dòng chữ; height lớn hơn dùng cho danh sách dạng thẻ (vd. gợi ý tổ chức) */
export const SkeletonLines = ({ lines = 3, height = 12, label }) => (
  <Skeleton label={label} className="sk-stack">
    {Array.from({ length: lines }, (_, i) => <Bone key={i} w={height > 20 ? '100%' : WIDTHS[i % WIDTHS.length]} h={height} />)}
  </Skeleton>
);

/* Hình dạng một bài đăng trên bảng tin */
export const SkeletonPost = ({ label = 'Đang tải bài viết…' }) => (
  <Skeleton label={label} className="card sk-post">
    <div className="sk-head"><Bone w={42} h={42} round /><div className="sk-stack grow"><Bone w="38%" h={13} /><Bone w="22%" h={11} /></div></div>
    <Bone w="94%" /><Bone w="86%" /><Bone w="58%" />
    <Bone h={180} />
  </Skeleton>
);

/* Hình dạng thẻ chiến dịch (ảnh bìa 16:9 + thân thẻ) */
export const SkeletonCampaignCard = ({ label = 'Đang tải chiến dịch…' }) => (
  <Skeleton label={label} className="card camp-card">
    <Bone h="auto" style={{ aspectRatio: '16 / 9', borderRadius: 0 }} />
    <div className="camp-body">
      <Bone w="40%" h={11} /><Bone w="85%" h={16} /><Bone w="55%" h={11} />
      <Bone w="95%" h={11} /><Bone w="70%" h={11} />
      <Bone h={8} />
      <div className="row gap8"><Bone h={32} /><Bone h={32} /></div>
    </div>
  </Skeleton>
);

/* Trang chi tiết: tiêu đề + vài đoạn; avatar=true cho trang hồ sơ */
export const SkeletonDetail = ({ label, avatar = false }) => (
  <Skeleton label={label} className="card pad sk-stack">
    <div className="sk-head">{avatar && <Bone w={96} h={96} round />}<div className="sk-stack grow"><Bone w="45%" h={22} /><Bone w="30%" h={12} /></div></div>
    <Bone w="92%" /><Bone w="84%" /><Bone w="66%" />
    <Bone h={120} />
  </Skeleton>
);

/* Các dòng của bảng: mỗi dòng là một ô trải hết số cột, bên trong là các thanh mờ.
   Một ô duy nhất nên vẫn đúng khi bảng đổi sang dạng thẻ trên điện thoại. */
export const SkeletonRows = ({ cols, rows = 4, label = 'Đang tải…' }) => (
  <>
    {Array.from({ length: rows }, (_, i) => (
      <tr key={i} className="sk-row" aria-busy="true">
        <td colSpan={cols}>
          {i === 0 && <span className="sr-only" role="status">{label}</span>}
          <div className="sk-cells">{Array.from({ length: Math.min(cols, 5) }, (_, j) => <Bone key={j} h={12} />)}</div>
        </td>
      </tr>
    ))}
  </>
);

/* Khung chờ toàn trang lúc khởi động (F5 khi đã đăng nhập): dùng lại đúng các lớp .app/.sidebar/.header/.content
   của Layout nên có cùng bố cục, kể cả trên điện thoại (thanh menu tự ẩn theo CSS sẵn có).
   Chỉ logo là thật; menu, ô tìm kiếm, nội dung là thanh mờ vì chưa biết vai trò người dùng. */
export const SkeletonApp = ({ brand }) => (
  <div className="app">
    <aside className="sidebar" aria-hidden="true">
      {brand}
      <div className="sk-stack sk-nav">{Array.from({ length: 6 }, (_, i) => <Bone key={i} h={36} />)}</div>
    </aside>
    <div className="main">
      <header className="header" aria-hidden="true"><Bone w="min(420px, 60%)" h={38} style={{ flex: 'none' }} /><span className="grow" /><Bone w={36} h={36} round /><Bone w={120} h={36} style={{ flex: 'none' }} /></header>
      <main className="content">
        <Skeleton label="Đang kết nối máy chủ…" className="sk-stack">
          <Bone w="34%" h={26} />
          <div className="sk-cells"><Bone h={84} /><Bone h={84} /><Bone h={84} /></div>
          <Bone h={220} />
          <Bone h={140} />
        </Skeleton>
      </main>
    </div>
  </div>
);

/* Vùng biểu đồ */
export const SkeletonChart = ({ height = 260, label = 'Đang tải biểu đồ…' }) => (
  <Skeleton label={label}><Bone h={height} /></Skeleton>
);
