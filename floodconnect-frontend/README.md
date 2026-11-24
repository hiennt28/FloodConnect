# FloodConnect – Frontend (ReactJS)

Giao diện hệ thống cứu trợ lũ lụt cộng đồng với 3 vai trò: **Người dân (Citizen)**, **Tổ chức cứu hộ (Rescue Org)** và **Admin**.
Mọi dữ liệu lấy từ backend Spring Boot (thư mục `floodconnect-backend`), nên phải chạy backend trước.

## Yêu cầu
- Node.js 18 trở lên (khuyến nghị 20 LTS)
- Backend đang chạy (mặc định `http://localhost:8080`), CSDL đã nạp dữ liệu địa giới `admin_wards` (xem README backend).
  Chưa nạp thì form SOS vẫn gửi được, chỉ không tự xác định phường/xã.
- Có kết nối Internet khi chạy (để tải bản đồ Esri; chữ dùng phông có sẵn của hệ điều hành)

## Cài đặt & chạy
```bash
npm install
cp .env.example .env # VITE_API_URL = địa chỉ backend, VITE_MAP_PROVIDER = nhà cung cấp bản đồ
npm run dev          # mở http://localhost:5173
```
Build bản production: `npm run build` → thư mục `dist/` (xem thử bằng `npm run preview`).

## Bản đồ
Bản đồ vẽ bằng **Leaflet** (qua `react-leaflet`), dùng chung một component `components/map/MapView.jsx` (ghim, vòng tròn, bản đồ nhiệt, đường viền phường/xã, ranh giới Việt Nam).
Mặc định dùng bản đồ địa hình **Esri World Topographic Map** (raster, nhãn tiếng Việt, không cần khóa API), phù hợp với giao diện bản đồ trong ứng dụng.
Muốn dùng nhà cung cấp khác, copy `.env.example` thành `.env` rồi điền:
```
VITE_MAP_PROVIDER=openfreemap  # hoặc osm, goong, maptiler
VITE_MAP_API_KEY=khoa_cua_ban
```
Khởi động lại `npm run dev` sau khi sửa `.env`. Nếu bản đồ vector không tải được (mạng chặn, sai khóa), hệ thống tự chuyển sang tile OpenStreetMap. Có thể đặt `VITE_MAP_PROVIDER=osm` hoặc `esri` để dùng thẳng bản đồ raster miễn phí.

## Tài khoản dùng thử
Tài khoản do backend tạo (xem README backend):

| Vai trò | Email | Mật khẩu |
|---|---|---|
| Admin (tạo tự động lần đầu chạy) | `admin@floodconnect.vn` (biến `ADMIN_EMAIL`) | giá trị `ADMIN_PASSWORD` bạn đặt trong `.env` của backend |
| Người dân mẫu | `seed.citizen01@floodconnect.demo` … | `Demo12345` |
| Tổ chức mẫu | `seed.org01@floodconnect.demo` … | `Demo12345` |

Tài khoản mẫu chỉ có khi chạy backend với `SEED_DATA=true`. Trang đăng nhập không có nút đăng nhập nhanh: nhập email (hoặc số điện thoại) và mật khẩu như bình thường.

## Nghiệp vụ SOS theo tài liệu (bản V5)
**Trạng thái SOS:** Chờ xử lý → (Đã phân công) → Đã tiếp nhận → Hoàn thành, hoặc Đã hủy, hoặc Không hợp lệ (Admin xác minh và kết luận không phải yêu cầu cứu hộ, chỉ khi ca đang Chờ xử lý). Nhãn mức độ dùng đúng mã tài liệu: `KHAN_CAP`, `CAO`, `TRUNG_BINH`, `THAP`, `KHONG`.

**Gửi SOS (người dân)** (`components/sos/SosForm.jsx`, phần vị trí ở `components/sos/LocationPicker.jsx`):
- **Vị trí cần cứu hộ**: người dân không nhập vĩ độ, kinh độ mà đặt ghim trên bản đồ, bằng một trong ba cách:
  lấy GPS; chọn Tỉnh → Phường/Xã (bản đồ đưa tới xã, vẽ đường viền) rồi chạm vào đúng chỗ; hoặc dán tọa độ / link Google Maps
  đầy đủ (mục "Tôi có tọa độ hoặc link vị trí", dùng khi gửi hộ người thân). Đã bật vị trí trong Hồ sơ thì form mở sẵn ghim tại đó (kèm nhắc "có thể đã cũ").
  Chạm vào bản đồ hoặc kéo ghim để chỉnh; có nút "Về vị trí GPS"; tọa độ chỉ để xem, có nút Sao chép.
- **Phường/xã tự điền theo ghim** (`GET /api/geo/locate`, bảng địa giới `admin_wards` ở backend, không gọi dịch vụ ngoài).
  Người dân đã tự chọn xã mà ghim nằm ở xã khác → hỏi "Đổi địa chỉ sang xã của ghim / Giữ xã đã chọn". Ghim sát ranh giới → chỉ gợi ý.
  Ghim ngoài mọi xã (ngoài biển) → nhắc kiểm tra. Ghim ngoài lãnh thổ Việt Nam → không gửi được.
- **Xác nhận vị trí** (ô "Đúng vị trí này" ngay dưới bản đồ): không cần khi GPS vừa lấy và sai số ≤ 100 m; bắt buộc khi GPS sai số lớn,
  vị trí lấy từ hồ sơ, chạm/kéo trên bản đồ hoặc dán tọa độ. Dời ghim sau khi xác nhận thì phải xác nhận lại. Bấm gửi khi chưa xác nhận →
  form cuộn tới ô xác nhận và nháy viền. Ghim cách GPS > 2 km (và > 2 lần sai số) → hỏi có phải gửi hộ người khác; chọn gửi hộ thì nhắc ghi tên, SĐT người tại chỗ trong mô tả.
- **Số nhà, ngõ hoặc mốc dễ nhận biết**: không bắt buộc (nhiều người gặp nạn không có số nhà), tối đa 150 ký tự; đã ghi thì phải ≥ 3 ký tự và có chữ hoặc số.
  Để trống thì hỏi **một lần** trước khi gửi ("Ghi thêm" / "Gửi luôn"). Địa chỉ lưu vào ca = mốc + Phường/Xã + Tỉnh.
- **Mô tả tình trạng**: bắt buộc, chỉ chữ, không nhận ảnh. Trước nút gửi có dòng tóm tắt "Đội cứu hộ sẽ tìm tới: …".
- Thứ tự kiểm tra khi gửi: vị trí → mô tả → mốc → nhắc mốc trống → backend. Backend tự lấy giờ gửi, thời tiết tại vị trí, mật độ SOS trong 500 m;
  cảnh báo ca tương tự trong 500 m / 30 phút (không tự gộp, người dân chọn "Vẫn gửi" hoặc "Không gửi nữa" mà vẫn giữ nội dung).
  Mỗi người chỉ có **một ca đang hoạt động** tại một thời điểm.
- Tra cứu địa giới lỗi (chưa nạp dữ liệu, mất mạng: `503 GEO_UNAVAILABLE`) → bỏ qua phần phường/xã, **vẫn đặt ghim và gửi SOS được**.

**Sửa / hủy / báo an toàn** (`components/sos/SosDetail.jsx`):

| Trạng thái ca | Người dân được làm |
|---|---|
| Chờ xử lý | Sửa mô tả (AI chấm lại như ca mới, mức có thể lên hoặc xuống) hoặc Hủy |
| Đã phân công | Chỉ Hủy: nội dung giữ nguyên từ lúc Admin giao ca, để tổ chức quyết định đúng trên mô tả đã dùng khi tính Matching; cần báo thêm tình hình khẩn thì gọi 112 |
| Đã tiếp nhận | Chỉ "Tôi đã an toàn" (đội cứu hộ có thể đang trên đường nên không hủy được) |

Vị trí ca không sửa được sau khi gửi; muốn đổi vị trí thì hủy (khi còn Chờ xử lý / Đã phân công) rồi gửi ca mới.

**Quy tắc AI** (chạy ở backend: dịch vụ AI chung `floodconnect-ai` nếu cấu hình `AI_SERVICE_URL` (PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm ca lân cận + rule engine), không thì bộ quy tắc Java `SosAssessor` dưới đây): AI chỉ đọc text, nhận cả câu gõ không dấu. Có trẻ em, người cao tuổi, người bệnh, thai phụ, bị thương, mắc kẹt: tối thiểu **Cao**. Nội dung mơ hồ/ngắn/độ tin cậy thấp: gắn "Nên xác minh", **không** hạ mức và **không** tự nâng. Thời tiết bất lợi (Open-Meteo) hoặc ≥2 ca trong 500 m: nâng tối đa 1 bậc; không lấy được thời tiết thì không nâng; trời tạnh/ít ca không dùng để hạ. Nhãn **Không** ("Cần xác minh"): chuyển Admin xem xét, chưa gửi tổ chức. Admin xác minh xong chọn một trong hai: mức Thấp..Khẩn cấp kèm **ghi chú xác minh bắt buộc** (thông tin hỏi được khi gọi người gửi; hiện cạnh mô tả gốc cho Admin, tổ chức và người gửi; ca được gửi cho tổ chức) hoặc **Đánh dấu không hợp lệ** kèm lý do (ca kết thúc, người gửi được báo và gửi được ca mới). Admin không chọn được nhãn Không.

**Bản đồ, mục "Gần tôi (10 km)"** (`pages/map/MapPage.jsx`): người dân đã bật vị trí trong Hồ sơ thì lọc quanh vị trí đó. Chưa có vị trí thì
không vẽ chấm "Vị trí của tôi" và không lọc theo điểm giả; bấm "Gần tôi" hoặc "Vị trí của tôi" thì xin quyền vị trí của trình duyệt:
cho phép → dùng tạm trên trang (không lưu vào hồ sơ); từ chối → báo lỗi, giữ khu vực đang xem. Tổ chức: chấm và nút ghi "Trụ sở của tôi". Admin không có mục này.
Người dân khác chỉ thấy vị trí ca SOS của người khác ở dạng làm tròn khoảng 100 m, không có địa chỉ và mô tả.

**Quá thời gian:** ca Chờ xử lý quá 15 phút chưa ai nhận, hoặc tổ chức không phản hồi ca được phân công sau 5 phút: hệ thống **thông báo Admin** (mọi mức độ, kể cả Khẩn cấp). Hệ thống không tự đổi mức độ.

**AI Matching:** có dịch vụ AI thì điểm do `floodconnect-ai` chấm (`POST /v1/match`), trọng số đổi theo mức đầy đủ của tin SOS và được gửi kèm từng điểm (`matching.weights`, giao diện hiển thị đúng trọng số đó); tổ chức AI loại (đang bận, quá xa, tuyên bố không hỗ trợ nhu cầu chính) có điểm 0 kèm lý do. Không có AI: `MatchingScore = 0,25·L + 0,30·S + 0,30·C + 0,15·W` (trọng số mặc định `MATCH_WEIGHTS` trong `utils/validators.js`). S so mô tả ca (kèm ghi chú xác minh của Admin) với mô tả tổ chức; C là năng lực đáp ứng nhu cầu của ca (nhu cầu và năng lực suy ra từ mô tả, nên **mô tả tổ chức bắt buộc** khi đăng ký, gửi lại hồ sơ và sửa thông tin); W xét số ca đang xử lý, số ca gần vị trí ca mới và số lần từ chối/không phản hồi trong 3 giờ gần đây. Danh sách ca đề xuất của tổ chức chỉ gồm ca Chờ xử lý trong bán kính 50 km (`AREA_RADIUS_M`), xếp theo điểm Matching giảm dần, loại ca tổ chức đã từ chối. Tổ chức đăng ký chọn Tỉnh, Phường/Xã, gõ thôn/xóm, số nhà và **đặt ghim trụ sở trên bản đồ** (`components/map/PinPicker.jsx`, không nhập vĩ độ, kinh độ) để AI Matching tính đúng khoảng cách; đặt ghim thì Tỉnh, Phường/Xã tự cập nhật theo xã chứa ghim; backend kiểm tra lại ghim nằm trong xã đã chọn. Form gửi lại hồ sơ cũng có ghim.

**Vị trí bài đăng** (`components/post/PostComposer.jsx`): không nhập vĩ độ, kinh độ. Người đăng chọn Tỉnh → Phường/Xã + ô "Thôn/xóm, số nhà, đường", hoặc bấm "Vị trí GPS" để hệ thống tự điền Tỉnh, Phường/Xã. Backend dùng GPS nếu nằm đúng xã đã chọn, không thì điểm đại diện của xã, và tự ghép địa chỉ hiển thị.

**Liên hệ:** gọi điện hoặc Zalo (`https://zalo.me/<sđt>`).

**Chỉ đường:** tổ chức bấm "Đường đi" để mở Google Maps chỉ đường từ trụ sở tới ca SOS (hệ thống không tự tính đường đi).

## Cập nhật gần nhất
Đợt sửa sau buổi review 11–12/11/2026 (mới nhất trước):
- **Danh sách theo trang thay cho "100 bản ghi mới nhất"** (trước đây ca/báo cáo/chiến dịch thứ 101 trở đi biến mất, kể cả ca còn chờ cứu):
  - Đồng bộ định kỳ lấy **mọi ca đang hoạt động** (`sosApi.active()`); trang Điều phối SOS mặc định "Đang hoạt động", ca đã kết thúc
    xem theo trang (`sos.casesPage`); tab SOS "Tất cả" chia "Đang cần cứu hộ" / "Đã hoàn thành" (Tải thêm); lịch sử của tổ chức Tải thêm.
  - Bảng của Admin dùng chung `components/admin/Pager.jsx` (Trước/Sau): Điều phối SOS, Người dân, hai trang báo cáo vi phạm (trang đang
    xem nằm trên địa chỉ `?tab=&page=`, mở chi tiết rồi quay lại vẫn đúng trang).
  - Chiến dịch, trang Thông báo: nút "Tải thêm". Bình luận: 20 bình luận gốc mỗi lần, phản hồi chỉ tải khi bấm "Xem N phản hồi"
    (trước đây mỗi bình luận có phản hồi tốn thêm một request); mở từ thông báo tải riêng bình luận đó (`load.comment`).
  - Các con số lấy từ backend: số báo cáo chờ ở Tổng quan, "Đội sẵn sàng" ở Bảng tin, "đang chạy / đã quyên góp" ở trang Chiến dịch.
- **Bản đồ điều phối của Admin** lấy ca từ `GET /api/admin/sos/map`: ca đang hoạt động luôn đủ, ca đã kết thúc theo khoảng 24 giờ / 7 / 30 ngày,
  báo khi chạm trần; danh sách hiện mã ca + địa chỉ, mô tả tải khi mở chi tiết. Bản đồ của người dân / tổ chức chỉ lọc các trạng thái chưa xong.
- **Tổ chức tự sửa thông tin** (`components/org/OrgInfoCard.jsx`); người ngoài chỉ thấy khu vực trụ sở (Phường/Xã, Tỉnh).
- **Giấy tờ tổ chức** mở qua `services/fileOpen.js` (tải bằng API có kiểm tra quyền, không còn đường dẫn công khai).
- **Chỉ người dân gửi SOS**: Admin và tổ chức không có mục "Cứu hộ SOS", mở thẳng `/sos` thì bị chuyển đi.
- Admin: tab tổ chức "Đang hoạt động", tab "Tất cả người dân" (tìm theo tên không dấu, email, SĐT); popup bản đồ có liên kết tới trang
  điều phối và trang chi tiết tổ chức; tìm kiếm không phân biệt dấu.
- Đặt lại mật khẩu kiểm tra từng ô; trình duyệt không còn tự điền mật khẩu cũ vào ô "Mật khẩu mới".

Trước đó:
- **Chế độ tối** (Sáng / Tối / Theo máy) và **khung chờ** thay chữ "Đang tải…": xem mục Giao diện.
- **Trang giới thiệu cho khách** (`pages/landing/Landing.jsx`, `/welcome`; khách vào `/` được chuyển tới đây): nút Gửi SOS, số khẩn cấp,
  4 con số từ `GET /api/public/stats` (`services/api/publicApi.js`, không gửi token), cách hoạt động, đối tượng sử dụng.
- **Tổng quan Admin**: biểu đồ "Ca SOS theo ngày" (7/30/90 ngày) kèm 3 thời lượng phản hồi trung bình; ô KPI và biểu đồ trạng thái
  lấy số từ backend thay vì tự đếm trên danh sách đã tải (trước đây sai khi có hơn 100 ca / 100 chiến dịch).
- **Xóa ảnh đại diện / logo tổ chức** trong Hồ sơ của tôi (`profile.removeAvatar`, có hộp xác nhận).
- **Chọn vị trí SOS trên bản đồ**: bỏ hai ô vĩ độ/kinh độ; GPS, chọn Tỉnh → Phường/Xã rồi chạm bản đồ, hoặc dán tọa độ/link
  (`components/sos/LocationPicker.jsx`); phường/xã tự điền qua `services/api/geoApi.js`; cảnh báo lệch xã, cảnh báo gửi hộ khi ghim xa GPS;
  ô "Đúng vị trí này" nổi bật, cuộn tới và nháy khi gửi mà chưa xác nhận; ô mốc nhắc một lần khi trống; tóm tắt trước khi gửi.
  `MapView` thêm `onMapClick`, ghim kéo được (`onDragEnd`) và `outline` (đường viền vùng). Danh sách tỉnh/xã `vn-admin-2025.json`
  sinh lại từ dữ liệu địa giới mới (tên năm 2026, mã Xã Ia Mơ 23938; xem `scripts/geo/build-admin-geo.mjs`).
- **`Field` có tùy chọn `group`**: dùng `<div role="group">` thay `<label>` cho trường chứa cả cụm điều khiển. Lý do: chạm vào chỗ trống
  trong `<label>` thì trình duyệt tự bấm nút đầu tiên bên trong (trước đây chạm bản đồ hoặc thả ghim lại kích hoạt nút GPS).
- **Sửa ca SOS chỉ khi Chờ xử lý**; Đã phân công chỉ hủy được (backend trả `409 SOS_NOT_EDITABLE` nếu cố sửa).
- **Bản đồ "Gần tôi" không còn dùng vị trí giả** ở Đà Nẵng khi người dân chưa có vị trí.
- **Xác thực email & quên mật khẩu bằng OTP**: trang `/verify-email` và `/reset-password` giờ là màn hình nhập mã 6 số
  (`components/common/OtpInput`) thay vì bấm liên kết, có đếm ngược gửi lại (`hooks/useResendCooldown`).
- **Ô mật khẩu có nút hiện/ẩn**: `components/common/PasswordInput`, dùng ở Đăng nhập, Đăng ký, Đặt lại mật khẩu, Đổi mật khẩu.
- **Số tiền mục tiêu khi tạo/sửa chiến dịch** hiển thị có dấu chấm ngăn cách hàng nghìn: `components/common/MoneyInput`.
- **SOS chỉ gồm chữ và vị trí**: không còn ô chọn ảnh, không hiển thị ảnh của ca SOS.
- Sửa lỗi bị đăng xuất khi mở nhiều tab cùng lúc (`services/http.js`, do refresh token bị xoay vòng).

## Chức năng đã có
**Người dân**: đăng ký, xác thực email bằng mã OTP 6 số, đăng nhập (khóa tạm 5 phút sau 5 lần sai), quên/đặt lại mật khẩu bằng OTP, hồ sơ + đổi mật khẩu (ô mật khẩu có nút hiện/ẩn) + đổi/xóa ảnh đại diện, quyền GPS, chọn giao diện sáng/tối; News Feed (Tất cả / Đang theo dõi, tải thêm), đăng/sửa/xóa mềm bài (1–2.000 ký tự, ≤5 ảnh JPG/PNG/WEBP ≤5MB, tọa độ hợp lệ trong lãnh thổ VN), thả tim (gộp thông báo), bình luận 2 cấp + sửa/xóa (20 bình luận gốc mỗi lần, phản hồi chỉ tải khi mở), chia sẻ, báo cáo bài; tìm kiếm (không phân biệt dấu) & theo dõi; thông báo (trang Thông báo có "Tải thêm"); SOS (chỉ tài khoản người dân; gửi với bản đồ xác nhận vị trí như trên, sửa/hủy/báo an toàn theo trạng thái, theo dõi tiến trình và trụ sở tổ chức phụ trách — hệ thống không có vị trí đội cứu hộ theo thời gian thực); tab SOS "Tất cả": ca đang cần cứu hộ luôn đủ, ca đã hoàn thành có "Tải thêm"; bản đồ sự cố; chiến dịch (danh sách/lọc, "Tải thêm" 12 mỗi lần, chi tiết + sao kê + xuất CSV, ủng hộ qua VNPay (sandbox thật khi backend đặt `PAYMENT_MOCK=false`, ngược lại là cổng mô phỏng; trên trang VNPay chọn ngân hàng, quét QR hoặc nhập thẻ), lịch sử đóng góp, báo cáo chiến dịch).

**Tổ chức**: bảng điều phối (ca đề xuất kèm điểm Matching và lý do, tiếp nhận, xác nhận/từ chối ca được phân công với đếm ngược 5 phút, Hoàn thành cứu hộ, chỉ đường qua Google Maps, lịch sử ca đã xong/đã hủy có "Tải thêm"), quản lý trạng thái tổ chức; tự sửa thông tin tổ chức (mô tả, trang web, địa chỉ, ghim trụ sở); tạo/sửa/kết thúc/gia hạn chiến dịch theo quy tắc từng trạng thái.

**Khách**: trang giới thiệu (số liệu công khai, số khẩn cấp), xem bảng tin, bài viết, hồ sơ công khai, tìm kiếm.

**Admin**: tổng quan với 7 biểu đồ (có biểu đồ theo ngày và thời gian phản hồi); điều phối SOS (mặc định mọi ca đang hoạt động, ca đã kết thúc xem theo trang Trước/Sau; lọc, sắp xếp, xác minh, chỉ định và điều phối lại; ca lân cận); bản đồ điều phối có lọc mức độ/trạng thái/khu vực, khoảng thời gian cho ca đã kết thúc và bản đồ nhiệt; duyệt/từ chối hồ sơ tổ chức (giấy tờ tải qua API có kiểm tra quyền); xử lý báo cáo bài đăng và chiến dịch (Trước/Sau); tab tổ chức "Đang hoạt động", tab "Tất cả người dân" có tìm kiếm; vi phạm, khóa/khôi phục người dân và tổ chức.

## Cấu trúc
```
src/
  main.jsx, App.jsx           khởi tạo ứng dụng, định tuyến
  assets/                     styles/ (styles.css, theme.css), geo/ (ranh giới VN), data/ (đơn vị hành chính 2025)
  context/AppContext.jsx      state, đồng bộ dữ liệu theo vai trò, cung cấp useApp()
  context/actions/            nghiệp vụ theo nhóm: auth, profile, load, social, campaign, sos, admin
  context/draft.js            hàm cập nhật bản nháp state dùng chung
  hooks/                      useApp, useNow, useAsync, useCaseParam, useOnceParam, useResendCooldown, useTheme (import từ '@/hooks')
  services/http.js            lớp gọi API: gắn JWT, tự refresh, chuẩn hoá lỗi
  services/realtime.js        thông báo thời gian thực qua WebSocket (STOMP, /ws)
  services/api/*Api.js        định nghĩa endpoint: authApi, userApi, postApi, sosApi, rescueApi, orgApi, campaignApi, adminApi, notificationApi, geoApi, publicApi
  services/api/mappers.js     chuyển DTO backend sang dữ liệu giao diện
  utils/                      constants, format, geo, validators, files, org, campaign, sosFlow, mapConfig, theme (import từ '@/utils')
  components/common/          Avatar, Tag, Modal, Confirm, Field, Tabs, Photo, Progress, Empty, Sev, AddressPicker, Skeleton (khung chờ)
  components/layout|map|post|sos|campaign|rescue|admin   (sos/: SosForm, LocationPicker, SosDetail, …; map/: MapView, BaseMap)
  pages/auth|community|map|sos|campaign|rescue|admin|landing   (landing/: trang giới thiệu cho khách tại /welcome)
```
Alias `@` trỏ tới `src/` (cấu hình trong `vite.config.js` và `jsconfig.json`).

## Kết nối backend
Trang giao diện không gọi API trực tiếp: mọi thao tác dữ liệu đi qua các hàm trong `context/actions/*` (gọi qua `useApp()`, ví dụ `auth.login`,
`social.createPost`, `sos.create`, `sos.locateWard`, `campaigns.startDonation`), các hàm này gọi endpoint khai báo trong `services/api/*Api.js`
qua `services/http.js` (JWT, tự làm mới token). Mỗi 30 giây khi tab đang mở, giao diện đồng bộ hồ sơ, 30 thông báo mới nhất và **mọi ca SOS
đang hoạt động** (`GET /api/sos/active`); thông báo mới nhận ngay qua WebSocket. Dữ liệu tích lũy theo thời gian (ca đã kết thúc, báo cáo,
chiến dịch, bình luận, thông báo cũ) không đồng bộ định kỳ mà tải theo trang khi người dùng xem. Ca đã kết thúc mở từ thông báo được
"ghim" (`pinned`) để các lần đồng bộ sau tải lại riêng ca đó.
Ảnh và hồ sơ tải lên được gửi lên backend dạng multipart; `localStorage` chỉ giữ token đăng nhập.

## Giao diện
`src/assets/styles/theme.css` (theme "Mực nước") được import sau `styles.css` trong `src/main.jsx`. `styles.css` dùng các biến màu khai báo
trong `theme.css`, nên **không bỏ được `theme.css`**.
- **Biến màu:** khai báo ở `:root` đầu `theme.css` (xanh sông `--river` cho thao tác, cam `--rescue` chỉ cho SOS, đỏ/cam/vàng/xanh lá chỉ cho
  mức độ và trạng thái). Màu giao diện dùng biến; còn ghi thẳng mã màu có chủ đích ở ghim/đường viền bản đồ, bảng màu nền ảnh đại diện
  mặc định và chữ sáng trên nền xanh đậm.
- **Chế độ tối:** người dùng chọn Sáng / Tối / Theo máy trong menu người dùng (góc phải trên); khách và lần đầu dùng theo cài đặt của máy.
  `utils/theme.js` lưu lựa chọn vào `localStorage` (`floodconnect-theme`) và đặt `<html data-theme="light|dark">`; một đoạn script nhỏ trong
  `index.html` làm việc này trước khi tải React để không nháy nền trắng. Bộ màu tối là khối `:root[data-theme="dark"]` (chỉ đổi giá trị biến,
  đã kiểm tra độ tương phản; nút màu dùng chữ tối trên nền sáng). Biểu đồ đọc màu bằng `cssVar()` và vẽ lại nhờ `useTheme()`.
  Cố ý giữ sáng: nền bản đồ (ghim màu mức độ thiết kế cho nền sáng), mã QR (máy quét cần nền trắng), nền xanh đậm trang đăng nhập/giới thiệu.
- **Khung chờ (skeleton):** `components/common/Skeleton.jsx` vẽ hình dạng nội dung (bài viết, thẻ chiến dịch, dòng bảng, biểu đồ, cả khung
  ứng dụng lúc khởi động) khi dữ liệu **chưa về**; dữ liệu về mà rỗng thì dùng `Empty`. Có `role="status"` cho trình đọc màn hình.
- **Responsive:** các quy tắc nằm ở cuối `theme.css` (mốc 1100 / 860 / 640 / 400 px). Từ 860 px trở xuống thanh bên thành menu trượt, hộp thoại thành bảng trượt từ đáy, bảng dữ liệu cuộn ngang.

## Kiểm tra code
```
npm run lint    # ESLint
npm test        # Vitest: hợp đồng endpoint, nghiệp vụ trong actions, utils, mappers, smoke test App
npm run check   # lint + test + build
```
Thêm endpoint mới: khai báo trong `services/api/<nhóm>Api.js`, thêm một dòng vào `services/api/endpoints.test.js`, rồi gọi từ action tương ứng.
