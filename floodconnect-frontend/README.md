# FloodConnect – Frontend (ReactJS)

Giao diện hệ thống cứu trợ lũ lụt cộng đồng với 3 vai trò: **Người dân (Citizen)**, **Tổ chức cứu hộ (Rescue Org)** và **Admin**, cùng trang giới thiệu cho **khách** chưa đăng nhập.
Mọi dữ liệu lấy từ backend Spring Boot (thư mục `floodconnect-backend`), nên phải chạy backend trước.

## Công nghệ
- React 18 + Vite 5, định tuyến bằng React Router 6
- Bản đồ: Leaflet (`react-leaflet`), MapLibre GL cho bản đồ vector
- Biểu đồ: Recharts; biểu tượng: lucide-react
- Thông báo thời gian thực: WebSocket (STOMP)
- Kiểm thử: Vitest + Testing Library; kiểm tra mã: ESLint

## Yêu cầu
- Node.js 18 trở lên (khuyến nghị 20 LTS)
- Backend đang chạy (mặc định `http://localhost:8080`), CSDL đã nạp dữ liệu địa giới `admin_wards` (xem README backend).
  Chưa nạp thì form SOS vẫn gửi được, chỉ không tự xác định phường/xã.
- Có kết nối Internet khi chạy (để tải bản đồ; chữ dùng phông có sẵn của hệ điều hành)

## Cài đặt & chạy
```bash
npm install
cp .env.example .env   # Windows: copy .env.example .env
npm run dev            # mở http://localhost:5173
```
Các biến trong `.env`:

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `VITE_API_URL` | Địa chỉ backend | `http://localhost:8080` |
| `VITE_MAP_PROVIDER` | Nhà cung cấp bản đồ: `esri`, `openfreemap`, `osm` (miễn phí) hoặc `goong`, `maptiler` (cần khóa) | `esri` |
| `VITE_MAP_API_KEY` | Khóa API, bắt buộc với `goong` / `maptiler` | trống |

Build bản production: `npm run build` → thư mục `dist/` (xem thử bằng `npm run preview`).

## Bản đồ
Bản đồ dùng chung một component `components/map/MapView.jsx` (ghim, vòng tròn, bản đồ nhiệt, đường viền phường/xã, ranh giới Việt Nam).
Mặc định là bản đồ địa hình **Esri World Topographic Map** (raster, nhãn tiếng Việt, không cần khóa API).
Đổi nhà cung cấp bằng `VITE_MAP_PROVIDER` trong `.env` rồi khởi động lại `npm run dev`. Nếu bản đồ vector không tải được (mạng chặn, sai khóa),
hệ thống tự chuyển sang tile OpenStreetMap.

## Tài khoản dùng thử
Tài khoản do backend tạo (xem README backend):

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin (tạo tự động lần đầu chạy) | `admin@floodconnect.vn` (biến `ADMIN_EMAIL`) | giá trị `ADMIN_PASSWORD` trong `.env` của backend |
| Người dân mẫu | `seed.citizen01@floodconnect.demo` … | `Demo12345` |
| Tổ chức mẫu | `seed.org01@floodconnect.demo` … | `Demo12345` |

Tài khoản mẫu chỉ có khi chạy backend với `SEED_DATA=true`. Đăng nhập bằng email (hoặc số điện thoại) và mật khẩu.

## Nghiệp vụ SOS
**Trạng thái SOS:** Chờ xử lý → (Đã phân công) → Đã tiếp nhận → Hoàn thành, hoặc Đã hủy, hoặc Không hợp lệ (Admin xác minh và kết luận không phải yêu cầu cứu hộ, chỉ khi ca đang Chờ xử lý). Nhãn mức độ: `KHAN_CAP`, `CAO`, `TRUNG_BINH`, `THAP`, `KHONG`.

Chỉ tài khoản **người dân** gửi được SOS; tổ chức và Admin không có mục "Cứu hộ SOS" (mở thẳng `/sos` thì bị chuyển đi).

**Gửi SOS** (`components/sos/SosForm.jsx`, phần vị trí ở `components/sos/LocationPicker.jsx`):
- **Vị trí cần cứu hộ**: không nhập vĩ độ, kinh độ mà đặt ghim trên bản đồ, bằng một trong ba cách:
  lấy GPS; chọn Tỉnh → Phường/Xã (bản đồ đưa tới xã, vẽ đường viền) rồi chạm vào đúng chỗ; hoặc dán tọa độ / link Google Maps
  (dùng khi gửi hộ người thân). Đã bật vị trí trong Hồ sơ thì form mở sẵn ghim tại đó (kèm nhắc "có thể đã cũ").
  Chạm bản đồ hoặc kéo ghim để chỉnh; có nút "Về vị trí GPS"; tọa độ chỉ để xem, có nút Sao chép.
- **Phường/xã tự điền theo ghim** (`GET /api/geo/locate`, tra bảng địa giới ở backend, không gọi dịch vụ ngoài).
  Đã tự chọn xã mà ghim nằm ở xã khác → hỏi đổi theo ghim hay giữ xã đã chọn. Ghim sát ranh giới → chỉ gợi ý.
  Ghim ngoài mọi xã (ngoài biển) → nhắc kiểm tra. Ghim ngoài lãnh thổ Việt Nam → không gửi được.
- **Xác nhận vị trí** (ô "Đúng vị trí này" dưới bản đồ): không cần khi GPS vừa lấy và sai số ≤ 100 m; bắt buộc khi GPS sai số lớn,
  vị trí lấy từ hồ sơ, chạm/kéo trên bản đồ hoặc dán tọa độ. Dời ghim sau khi xác nhận thì phải xác nhận lại. Bấm gửi khi chưa xác nhận →
  form cuộn tới ô xác nhận và nháy viền. Ghim cách GPS > 2 km (và > 2 lần sai số) → hỏi có phải gửi hộ người khác; chọn gửi hộ thì nhắc ghi tên, SĐT người tại chỗ trong mô tả.
- **Số nhà, ngõ hoặc mốc dễ nhận biết**: không bắt buộc, tối đa 150 ký tự; đã ghi thì phải ≥ 3 ký tự và có chữ hoặc số.
  Để trống thì hỏi **một lần** trước khi gửi ("Ghi thêm" / "Gửi luôn"). Địa chỉ lưu vào ca = mốc + Phường/Xã + Tỉnh.
- **Mô tả tình trạng**: bắt buộc, chỉ chữ, không nhận ảnh. Trước nút gửi có dòng tóm tắt "Đội cứu hộ sẽ tìm tới: …".
- Thứ tự kiểm tra khi gửi: vị trí → mô tả → mốc → nhắc mốc trống → backend. Backend tự lấy giờ gửi, thời tiết tại vị trí, mật độ SOS trong 500 m;
  cảnh báo ca tương tự trong 500 m / 30 phút (không tự gộp; người dân chọn "Vẫn gửi" hoặc "Không gửi nữa" mà vẫn giữ nội dung).
  Mỗi người chỉ có **một ca đang hoạt động** tại một thời điểm.
- Tra cứu địa giới lỗi (`503 GEO_UNAVAILABLE`) → bỏ qua phần phường/xã, **vẫn đặt ghim và gửi SOS được**.

**Sửa / hủy / báo an toàn** (`components/sos/SosDetail.jsx`):

| Trạng thái ca | Người dân được làm |
|---|---|
| Chờ xử lý | Sửa mô tả (AI chấm lại như ca mới, mức có thể lên hoặc xuống) hoặc Hủy |
| Đã phân công | Chỉ Hủy: nội dung giữ nguyên từ lúc Admin giao ca để tổ chức quyết định trên đúng mô tả đã dùng khi tính Matching (backend trả `409 SOS_NOT_EDITABLE` nếu cố sửa); cần báo thêm tình hình khẩn thì gọi 112 |
| Đã tiếp nhận | Chỉ "Tôi đã an toàn" (đội cứu hộ có thể đang trên đường nên không hủy được) |

Vị trí ca không sửa được sau khi gửi; muốn đổi vị trí thì hủy (khi còn Chờ xử lý / Đã phân công) rồi gửi ca mới.

**Đánh giá mức độ bằng AI** (chạy ở backend): dùng dịch vụ `floodconnect-ai` nếu backend cấu hình `AI_SERVICE_URL` (PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm ca lân cận + rule engine), không thì dùng bộ quy tắc Java `SosAssessor`. AI chỉ đọc text, nhận cả câu gõ không dấu.
- Có trẻ em, người cao tuổi, người bệnh, thai phụ, bị thương, mắc kẹt: tối thiểu **Cao**.
- Nội dung mơ hồ/ngắn/độ tin cậy thấp: gắn "Nên xác minh", **không** hạ mức và **không** tự nâng.
- Thời tiết bất lợi (Open-Meteo) hoặc ≥ 2 ca trong 500 m: nâng tối đa 1 bậc; không lấy được thời tiết thì không nâng; trời tạnh/ít ca không dùng để hạ.
- Nhãn **Không** ("Cần xác minh"): chuyển Admin xem xét, chưa gửi tổ chức. Admin xác minh xong chọn một trong hai: mức Thấp..Khẩn cấp kèm **ghi chú xác minh bắt buộc** (hiện cạnh mô tả gốc cho Admin, tổ chức và người gửi; ca được gửi cho tổ chức) hoặc **Đánh dấu không hợp lệ** kèm lý do (ca kết thúc, người gửi được báo và gửi được ca mới). Admin không chọn được nhãn Không.

**Quá thời gian:** ca Chờ xử lý quá 15 phút chưa ai nhận, hoặc tổ chức không phản hồi ca được phân công sau 5 phút: hệ thống **thông báo Admin** (mọi mức độ, kể cả Khẩn cấp). Hệ thống không tự đổi mức độ.

**AI Matching:** có dịch vụ AI thì điểm do `floodconnect-ai` chấm (`POST /v1/match`); trọng số đổi theo mức đầy đủ của tin SOS và được gửi kèm từng điểm (`matching.weights`), giao diện hiển thị đúng trọng số đó; tổ chức bị loại (đang bận, quá xa, không hỗ trợ nhu cầu chính) có điểm 0 kèm lý do. Không có AI: `MatchingScore = 0,25·L + 0,30·S + 0,30·C + 0,15·W` (trọng số mặc định `MATCH_WEIGHTS` trong `utils/validators.js`).
- **L**: khoảng cách từ trụ sở tổ chức tới ca.
- **S**: độ tương đồng giữa mô tả ca (kèm ghi chú xác minh của Admin) và mô tả tổ chức.
- **C**: năng lực đáp ứng nhu cầu của ca (suy ra từ mô tả, nên **mô tả tổ chức bắt buộc** khi đăng ký, gửi lại hồ sơ và sửa thông tin).
- **W**: khối lượng công việc — số ca đang xử lý, số ca gần vị trí ca mới, số lần từ chối/không phản hồi trong 3 giờ gần đây.

Danh sách ca đề xuất của tổ chức chỉ gồm ca Chờ xử lý trong bán kính 50 km (`AREA_RADIUS_M`), xếp theo điểm Matching giảm dần, loại ca tổ chức đã từ chối.

**Trụ sở tổ chức** (`components/map/PinPicker.jsx`): khi đăng ký và gửi lại hồ sơ, tổ chức chọn Tỉnh, Phường/Xã, gõ thôn/xóm, số nhà và **đặt ghim trụ sở trên bản đồ** (không nhập vĩ độ, kinh độ); đặt ghim thì Tỉnh, Phường/Xã tự cập nhật theo xã chứa ghim; backend kiểm tra lại ghim nằm trong xã đã chọn.

**Vị trí bài đăng** (`components/post/PostComposer.jsx`): chọn Tỉnh → Phường/Xã + ô "Thôn/xóm, số nhà, đường", hoặc bấm "Vị trí GPS" để tự điền. Backend dùng GPS nếu nằm đúng xã đã chọn, không thì dùng điểm đại diện của xã, và tự ghép địa chỉ hiển thị.

**Bản đồ, mục "Gần tôi (10 km)"** (`pages/map/MapPage.jsx`): người dân đã bật vị trí trong Hồ sơ thì lọc quanh vị trí đó. Chưa có vị trí thì
bấm "Gần tôi" hoặc "Vị trí của tôi" sẽ xin quyền vị trí của trình duyệt: cho phép → dùng tạm trên trang (không lưu vào hồ sơ); từ chối → báo lỗi, giữ khu vực đang xem.
Tổ chức: chấm và nút ghi "Trụ sở của tôi". Admin không có mục này.
Người dân khác chỉ thấy vị trí ca SOS của người khác ở dạng làm tròn khoảng 100 m, không có địa chỉ và mô tả.

**Liên hệ:** gọi điện hoặc Zalo (`https://zalo.me/<sđt>`).

**Chỉ đường:** tổ chức bấm "Đường đi" để mở Google Maps chỉ đường từ trụ sở tới ca SOS (hệ thống không tự tính đường đi).

## Chức năng
**Khách**: trang giới thiệu `/welcome` (số liệu công khai từ `GET /api/public/stats`, nút Gửi SOS, số khẩn cấp, cách hoạt động); xem bảng tin, bài viết, hồ sơ công khai, tìm kiếm.

**Người dân**:
- Tài khoản: đăng ký, xác thực email bằng mã OTP 6 số (có đếm ngược gửi lại), đăng nhập (khóa tạm 5 phút sau 5 lần sai), quên/đặt lại mật khẩu bằng OTP; hồ sơ, đổi mật khẩu (ô mật khẩu có nút hiện/ẩn), đổi/xóa ảnh đại diện, quyền GPS, chọn giao diện Sáng/Tối/Theo máy.
- Cộng đồng: News Feed (Tất cả / Đang theo dõi, tải thêm); đăng/sửa/xóa mềm bài (1–2.000 ký tự, ≤ 5 ảnh JPG/PNG/WEBP ≤ 5 MB, vị trí trong lãnh thổ VN); thả tim (gộp thông báo); bình luận 2 cấp + sửa/xóa (20 bình luận gốc mỗi lần, phản hồi chỉ tải khi bấm "Xem N phản hồi"); chia sẻ; báo cáo bài; tìm kiếm không phân biệt dấu và theo dõi người dùng; thông báo (trang Thông báo có "Tải thêm").
- SOS: gửi với bản đồ xác nhận vị trí, sửa/hủy/báo an toàn theo trạng thái, theo dõi tiến trình và trụ sở tổ chức phụ trách (hệ thống không theo dõi vị trí đội cứu hộ theo thời gian thực); tab SOS "Tất cả" chia "Đang cần cứu hộ" / "Đã hoàn thành" (Tải thêm); bản đồ sự cố.
- Chiến dịch: danh sách/lọc ("Tải thêm" 12 mỗi lần), chi tiết + sao kê + xuất CSV, ủng hộ qua VNPay (sandbox thật khi backend đặt `PAYMENT_MOCK=false`, ngược lại là cổng mô phỏng), lịch sử đóng góp, báo cáo chiến dịch.

**Tổ chức**:
- Bảng điều phối: ca đề xuất kèm điểm Matching và lý do, tiếp nhận, xác nhận/từ chối ca được phân công với đếm ngược 5 phút, Hoàn thành cứu hộ, chỉ đường qua Google Maps, lịch sử ca đã xong/đã hủy có "Tải thêm".
- Quản lý trạng thái tổ chức; tự sửa thông tin (mô tả, trang web, địa chỉ, ghim trụ sở) tại `components/org/OrgInfoCard.jsx` — tên, loại tổ chức và giấy tờ gắn với hồ sơ đã duyệt nên không tự đổi; người ngoài chỉ thấy trụ sở tới Phường/Xã, Tỉnh.
- Tạo/sửa/kết thúc/gia hạn chiến dịch theo quy tắc từng trạng thái (số tiền mục tiêu có dấu chấm ngăn cách hàng nghìn).

**Admin**:
- Tổng quan với 7 biểu đồ (có biểu đồ "Ca SOS theo ngày" 7/30/90 ngày và thời gian phản hồi trung bình); số liệu lấy từ backend.
- Điều phối SOS: mặc định mọi ca đang hoạt động, ca đã kết thúc xem theo trang Trước/Sau; lọc, sắp xếp, xác minh, chỉ định và điều phối lại; ca lân cận.
- Bản đồ điều phối (`GET /api/admin/sos/map`): lọc mức độ/trạng thái/khu vực, khoảng thời gian 24 giờ / 7 / 30 ngày cho ca đã kết thúc, bản đồ nhiệt.
- Duyệt/từ chối hồ sơ tổ chức (giấy tờ mở qua `services/fileOpen.js`, tải bằng API có kiểm tra quyền, không có đường dẫn công khai).
- Xử lý báo cáo bài đăng và chiến dịch; quản lý người dân (tìm theo tên không dấu, email, SĐT) và tổ chức; vi phạm, khóa/khôi phục tài khoản.

## Cấu trúc
```
src/
  main.jsx, App.jsx           khởi tạo ứng dụng, định tuyến
  assets/                     styles/ (styles.css, theme.css), geo/ (ranh giới VN), data/ (đơn vị hành chính)
  context/AppContext.jsx      state, đồng bộ dữ liệu theo vai trò, cung cấp useApp()
  context/actions/            nghiệp vụ theo nhóm: auth, profile, load, social, campaign, sos, admin
  context/draft.js            hàm cập nhật bản nháp state dùng chung
  hooks/                      useApp, useNow, useAsync, useCaseParam, useOnceParam, useResendCooldown, useTheme (import từ '@/hooks')
  services/http.js            lớp gọi API: gắn JWT, tự refresh, chuẩn hoá lỗi
  services/realtime.js        thông báo thời gian thực qua WebSocket (STOMP, /ws)
  services/fileOpen.js        mở giấy tờ riêng tư qua API có kiểm tra quyền
  services/api/*Api.js        định nghĩa endpoint: authApi, userApi, postApi, sosApi, rescueApi, orgApi, campaignApi, adminApi,
                              notificationApi, geoApi, publicApi
  services/api/mappers.js     chuyển DTO backend sang dữ liệu giao diện
  utils/                      constants, format, geo, validators, files, org, campaign, sosFlow, mapConfig, theme (import từ '@/utils')
  components/common/          Avatar, AvatarZoom, Tag, Modal, Confirm, Field, Tabs, Photo, Progress, Empty, Sev, AddressPicker,
                              OtpInput, PasswordInput, MoneyInput, Skeleton
  components/layout|map|post|sos|campaign|rescue|admin|org|user
  pages/auth|community|map|sos|campaign|rescue|admin|landing
```
Alias `@` trỏ tới `src/` (cấu hình trong `vite.config.js` và `jsconfig.json`).

Ghi chú: `Field` có tùy chọn `group` để dùng `<div role="group">` thay `<label>` cho trường chứa cả cụm điều khiển (chạm vào chỗ trống trong
`<label>` thì trình duyệt tự bấm nút đầu tiên bên trong, ví dụ chạm bản đồ lại kích hoạt nút GPS).

## Kết nối backend
Trang giao diện không gọi API trực tiếp: mọi thao tác dữ liệu đi qua các hàm trong `context/actions/*` (gọi qua `useApp()`, ví dụ `auth.login`,
`social.createPost`, `sos.create`, `sos.locateWard`, `campaigns.startDonation`); các hàm này gọi endpoint khai báo trong `services/api/*Api.js`
qua `services/http.js` (JWT, tự làm mới token, xử lý đúng khi mở nhiều tab cùng lúc).

- Mỗi 30 giây khi tab đang mở, giao diện đồng bộ hồ sơ, 30 thông báo mới nhất và **mọi ca SOS đang hoạt động** (`GET /api/sos/active`);
  thông báo mới nhận ngay qua WebSocket.
- Dữ liệu tích lũy theo thời gian (ca đã kết thúc, báo cáo, chiến dịch, bình luận, thông báo cũ) không đồng bộ định kỳ mà tải theo trang
  khi người dùng xem. Bảng của Admin dùng chung `components/admin/Pager.jsx`; trang đang xem nằm trên địa chỉ (`?tab=&page=`) nên mở chi tiết
  rồi quay lại vẫn đúng trang.
- Ca đã kết thúc mở từ thông báo được "ghim" (`pinned`) để các lần đồng bộ sau tải lại riêng ca đó.
- Ảnh và hồ sơ tải lên được gửi lên backend dạng multipart; `localStorage` chỉ giữ token đăng nhập và lựa chọn giao diện.

## Giao diện
`src/assets/styles/theme.css` (theme v4 "Mực nước, bo tròn") được import sau `styles.css` trong `src/main.jsx`. `styles.css` dùng các biến màu
khai báo trong `theme.css`, nên **không bỏ được `theme.css`**.
- **Phong cách:** khối bo góc lớn nổi trên nền bằng bóng đổ mềm; nút, nhãn, tab, ô tìm kiếm dạng viên thuốc. Mức độ nguy hiểm hiển thị thêm
  bằng vạch "thước mực nước", đọc được cả khi không phân biệt màu.
- **Biến màu:** khai báo ở `:root` đầu `theme.css` (xanh sông `--river` cho thao tác, cam `--rescue` chỉ cho SOS, đỏ/cam/vàng/xanh lá chỉ cho
  mức độ và trạng thái). Chỉ ghi thẳng mã màu ở ghim/đường viền bản đồ, bảng màu nền ảnh đại diện mặc định và chữ sáng trên nền xanh đậm.
- **Chế độ tối:** người dùng chọn Sáng / Tối / Theo máy trong menu người dùng (góc phải trên); khách và lần đầu dùng theo cài đặt của máy.
  `utils/theme.js` lưu lựa chọn vào `localStorage` (`floodconnect-theme`) và đặt `<html data-theme="light|dark">`; một đoạn script nhỏ trong
  `index.html` làm việc này trước khi tải React để không nháy nền trắng. Bộ màu tối là khối `:root[data-theme="dark"]` (chỉ đổi giá trị biến).
  Biểu đồ đọc màu bằng `cssVar()` và vẽ lại nhờ `useTheme()`.
  Cố ý giữ sáng: nền bản đồ (ghim màu mức độ thiết kế cho nền sáng), mã QR (máy quét cần nền trắng), nền xanh đậm trang đăng nhập/giới thiệu.
- **Khung chờ (skeleton):** `components/common/Skeleton.jsx` vẽ hình dạng nội dung (bài viết, thẻ chiến dịch, dòng bảng, biểu đồ, khung ứng dụng
  lúc khởi động) khi dữ liệu **chưa về**; dữ liệu về mà rỗng thì dùng `Empty`. Có `role="status"` cho trình đọc màn hình.
- **Responsive:** các quy tắc nằm ở cuối `theme.css` (mốc 1100 / 860 / 640 / 400 px). Từ 860 px trở xuống thanh bên thành menu trượt,
  hộp thoại thành bảng trượt từ đáy, bảng dữ liệu cuộn ngang.

## Kiểm tra code
```bash
npm run lint    # ESLint
npm test        # Vitest: hợp đồng endpoint, nghiệp vụ trong actions, utils, mappers, smoke test App
npm run check   # lint + test + build
```
Thêm endpoint mới: khai báo trong `services/api/<nhóm>Api.js`, thêm một dòng vào `services/api/endpoints.test.js`, rồi gọi từ action tương ứng.
