/* Lớp gọi API backend FloodConnect: gắn JWT, tự refresh khi hết hạn, chuẩn hoá lỗi. */
const BASE = (import.meta.env.VITE_API_URL || 'http://localhost:8080').replace(/\/$/, '');
const TOKEN_KEY = 'floodconnect-tokens';

export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message || 'Có lỗi xảy ra, vui lòng thử lại.');
    this.status = status;
    this.code = body?.code || null;
    this.errors = body?.errors || null; // { tênField: thông báo } khi lỗi VALIDATION_ERROR
  }
}

/* ---------- token ---------- */
export const tokens = {
  get() { try { return JSON.parse(localStorage.getItem(TOKEN_KEY)) || null; } catch { return null; } },
  set(access, refresh) { localStorage.setItem(TOKEN_KEY, JSON.stringify({ access, refresh })); },
  clear() { localStorage.removeItem(TOKEN_KEY); },
};

/* ---------- gọi mạng ---------- */
async function send(path, { method = 'GET', body, form, token, raw = false } = {}) {
  const headers = {};
  if (token) headers.Authorization = `Bearer ${token}`;
  let payload;
  if (form) payload = form; // FormData: trình duyệt tự đặt Content-Type + boundary
  else if (body !== undefined) { headers['Content-Type'] = 'application/json'; payload = JSON.stringify(body); }

  let res;
  try {
    res = await fetch(BASE + path, { method, headers, body: payload });
  } catch {
    throw new ApiError(0, { code: 'NETWORK', message: 'Không kết nối được máy chủ. Hãy kiểm tra backend đã chạy chưa.' });
  }
  if (raw && res.ok) return res; // tệp (giấy tờ tổ chức...): trả nguyên Response để nơi gọi đọc nội dung nhị phân
  const text = await res.text();
  let data = null;
  if (text) { try { data = JSON.parse(text); } catch { data = null; } }
  if (!res.ok) throw new ApiError(res.status, data);
  return data;
}

/* Chỉ một lần refresh tại một thời điểm (refresh token bị xoay vòng nên không được gọi song song) */
let refreshing = null;
async function refreshTokens() {
  if (!refreshing) {
    refreshing = (async () => {
      const t = tokens.get();
      if (!t?.refresh) throw new ApiError(401, { message: 'Phiên đăng nhập đã hết hạn.' });
      const r = await send('/api/auth/refresh', { method: 'POST', body: { refreshToken: t.refresh } });
      tokens.set(r.accessToken, r.refreshToken);
      return r;
    })().finally(() => { refreshing = null; });
  }
  return refreshing;
}

/**
 * api('/api/posts')                                  -> GET, tự gắn token nếu đã đăng nhập
 * api('/api/auth/login', { method:'POST', body })    -> JSON
 * api('/api/sos', { method:'POST', form: formData }) -> multipart
 * Tuỳ chọn { auth:false } cho endpoint công khai (login, register...).
 */
export async function api(path, { auth = true, ...opts } = {}) {
  const t = auth ? tokens.get() : null;
  try {
    return await send(path, { ...opts, token: t?.access });
  } catch (e) {
    if (!(e instanceof ApiError) || e.status !== 401 || !auth) throw e;
    // Refresh token bị xoay vòng (dùng một lần). Nếu đang mở nhiều tab, một tab khác có thể vừa làm mới token
    // ngay trước request này: thử lại bằng token mới nhất trong localStorage trước khi tự làm mới, để tránh cả hai
    // tab cùng dùng một refresh token đã hết hạn và bị đăng xuất oan.
    const latest = tokens.get();
    if (latest?.access && latest.access !== t?.access) {
      try { return await send(path, { ...opts, token: latest.access }); }
      catch (eRetry) { if (!(eRetry instanceof ApiError) || eRetry.status !== 401) throw eRetry; } // vẫn 401: làm mới như bình thường
    }
    if (!latest?.refresh) { endSession(); throw e; }      // không còn refresh token: phiên đã chết
    let r;
    try {
      r = await refreshTokens();
    } catch (e2) {
      if (!(e2 instanceof ApiError) || e2.status === 0) throw e2; // lỗi mạng: giữ phiên, không đăng xuất
      // Server từ chối refresh token của tab này — có thể một tab khác vừa xoay vòng nó đúng lúc. Kiểm tra lại
      // localStorage lần cuối trước khi kết luận phiên đã thật sự hết hạn.
      const after = tokens.get();
      if (after?.access && after.access !== latest.access) {
        try { return await send(path, { ...opts, token: after.access }); }
        catch (eRetry2) { if (eRetry2 instanceof ApiError && eRetry2.status === 401) endSession(); throw eRetry2; }
      }
      endSession();
      throw e2;
    }
    try {
      return await send(path, { ...opts, token: r.accessToken });
    } catch (e3) {
      if (e3 instanceof ApiError && e3.status === 401) endSession(); // token mới vẫn bị từ chối (tài khoản không còn hợp lệ)
      throw e3;
    }
  }
}

/* Xoá token và báo AppContext xoá session để UI quay về trạng thái đăng xuất */
function endSession() {
  tokens.clear();
  window.dispatchEvent(new Event('floodconnect:logout'));
}

/* Phần JSON trong request multipart (Spring @RequestPart cần Content-Type: application/json) */
export const jsonPart = (obj) => new Blob([JSON.stringify(obj)], { type: 'application/json' });
export const API_BASE = BASE;
