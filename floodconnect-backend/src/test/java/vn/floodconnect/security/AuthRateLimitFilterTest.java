package vn.floodconnect.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

/** Giới hạn tần suất API xác thực theo IP: 20/10/5 yêu cầu mỗi phút cho đăng nhập / nhập mã / gửi mã, đăng ký. */
class AuthRateLimitFilterTest {
    private final AtomicLong now = new AtomicLong(1_000_000);
    private final AuthRateLimitFilter filter = new AuthRateLimitFilter(now::get, true, 20, 10, 5, false);

    private MockHttpServletResponse call(String method, String path, String ip, String xff) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest(method, path);
        req.setRemoteAddr(ip);
        if (xff != null) req.addHeader("X-Forwarded-For", xff);
        MockHttpServletResponse res = new MockHttpServletResponse();
        filter.doFilter(req, res, new MockFilterChain());
        return res;
    }

    private MockHttpServletResponse post(String path, String ip) throws Exception { return call("POST", path, ip, null); }

    @Test void nhapMaQuaMuoiLanMotPhutThiBi429() throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(200, post("/api/auth/reset-password", "1.1.1.1").getStatus());
        MockHttpServletResponse blocked = post("/api/auth/reset-password", "1.1.1.1");
        assertEquals(429, blocked.getStatus());
        assertEquals("60", blocked.getHeader("Retry-After"));
        assertTrue(blocked.getContentAsString().contains("\"code\":\"TOO_MANY_REQUESTS\""));
        assertTrue(blocked.getContentAsString().contains("\"retryAfterSeconds\":\"60\""));
    }

    @Test void xacThucEmailVaDatLaiMatKhauChungMotBoDem() throws Exception {
        for (int i = 0; i < 5; i++) post("/api/auth/verify-email", "1.1.1.1");
        for (int i = 0; i < 5; i++) assertEquals(200, post("/api/auth/reset-password", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/verify-email", "1.1.1.1").getStatus());
    }

    @Test void dangNhapChoHaiMuoiLan() throws Exception {
        for (int i = 0; i < 20; i++) assertEquals(200, post("/api/auth/login", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/login", "1.1.1.1").getStatus());
    }

    @Test void guiMaVaDangKyChoNamLan() throws Exception {
        for (int i = 0; i < 5; i++) assertEquals(200, post("/api/auth/forgot-password", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/register", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/resend-verification", "1.1.1.1").getStatus());
        assertEquals(429, post("/api/auth/register-org", "1.1.1.1").getStatus());
    }

    @Test void sangPhutMoiThiDemLai() throws Exception {
        for (int i = 0; i < 11; i++) post("/api/auth/verify-email", "1.1.1.1");
        now.addAndGet(30_000);
        MockHttpServletResponse stillBlocked = post("/api/auth/verify-email", "1.1.1.1");
        assertEquals(429, stillBlocked.getStatus());
        assertEquals("30", stillBlocked.getHeader("Retry-After"));
        now.addAndGet(30_000);
        assertEquals(200, post("/api/auth/verify-email", "1.1.1.1").getStatus());
    }

    @Test void moiIpMotBoDemRieng() throws Exception {
        for (int i = 0; i < 11; i++) post("/api/auth/verify-email", "1.1.1.1");
        assertEquals(200, post("/api/auth/verify-email", "2.2.2.2").getStatus());
    }

    @Test void khongTinXForwardedForNeuChuaBatTrustProxy() throws Exception {
        for (int i = 0; i < 10; i++) call("POST", "/api/auth/verify-email", "1.1.1.1", "9.9.9." + i);
        assertEquals(429, call("POST", "/api/auth/verify-email", "1.1.1.1", "8.8.8.8").getStatus(), "đổi header giả không né được giới hạn");
    }

    @Test void sauProxyThiDungIpTrongXForwardedFor() throws Exception {
        AuthRateLimitFilter proxied = new AuthRateLimitFilter(now::get, true, 20, 10, 5, true);
        for (int i = 0; i < 11; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/verify-email");
            req.setRemoteAddr("10.0.0.1"); // địa chỉ của proxy
            req.addHeader("X-Forwarded-For", "5.5.5.5, 10.0.0.1");
            proxied.doFilter(req, new MockHttpServletResponse(), new MockFilterChain());
        }
        MockHttpServletRequest other = new MockHttpServletRequest("POST", "/api/auth/verify-email");
        other.setRemoteAddr("10.0.0.1");
        other.addHeader("X-Forwarded-For", "6.6.6.6");
        MockHttpServletResponse res = new MockHttpServletResponse();
        proxied.doFilter(other, res, new MockFilterChain());
        assertEquals(200, res.getStatus(), "người dùng khác sau cùng proxy không bị chặn theo");
    }

    @Test void apiKhacVaPhuongThucKhacKhongBiGioiHan() throws Exception {
        for (int i = 0; i < 50; i++) {
            assertEquals(200, post("/api/auth/refresh", "1.1.1.1").getStatus());
            assertEquals(200, call("GET", "/api/auth/login", "1.1.1.1", null).getStatus());
        }
    }

    @Test void tatCauHinhThiKhongGioiHan() throws Exception {
        AuthRateLimitFilter off = new AuthRateLimitFilter(now::get, false, 20, 10, 5, false);
        for (int i = 0; i < 30; i++) {
            MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/verify-email");
            req.setRemoteAddr("1.1.1.1");
            MockHttpServletResponse res = new MockHttpServletResponse();
            off.doFilter(req, res, new MockFilterChain());
            assertEquals(200, res.getStatus());
        }
    }

    @Test void donCuaSoHetHan() throws Exception {
        post("/api/auth/login", "1.1.1.1");
        post("/api/auth/login", "2.2.2.2");
        assertEquals(2, filter.trackedWindows());
        now.addAndGet(60_000);
        filter.purge();
        assertEquals(0, filter.trackedWindows());
    }
}
