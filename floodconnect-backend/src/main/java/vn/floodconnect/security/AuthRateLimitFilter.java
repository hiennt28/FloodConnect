package vn.floodconnect.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Giới hạn số yêu cầu tới các API xác thực theo địa chỉ IP, mỗi cửa sổ 1 phút:
 *  - đăng nhập: 20 lần/phút;
 *  - nhập mã OTP (xác thực email, đặt lại mật khẩu): 10 lần/phút — chặn dò mã 6 số;
 *  - gửi mã / đăng ký (quên mật khẩu, gửi lại mã xác thực, đăng ký người dân, đăng ký tổ chức): 5 lần/phút — chặn spam email.
 * Vượt giới hạn: 429 TOO_MANY_REQUESTS, header Retry-After và errors.retryAfterSeconds (số giây đến khi được gửi tiếp).
 *
 * Bộ đếm lưu trong bộ nhớ: khởi động lại server là đếm lại, và chỉ đúng khi chạy một server (đủ cho quy mô hiện tại).
 * Nhiều người dùng chung mạng (wifi trường, 4G) có chung IP nên giới hạn đặt rộng; tắt được bằng AUTH_RATE_LIMIT_ENABLED=false.
 * IP lấy từ kết nối (getRemoteAddr), KHÔNG tin header X-Forwarded-For vì ai cũng tự ghi giả được để né giới hạn;
 * chỉ bật TRUST_PROXY=true khi backend chạy sau reverse proxy (nginx...) có ghi header này.
 */
@Component
@Slf4j
public class AuthRateLimitFilter extends OncePerRequestFilter {
    static final long WINDOW_MS = 60_000;

    enum Group { LOGIN, OTP, SEND }

    private static final Map<String, Group> PATHS = Map.of(
            "/api/auth/login", Group.LOGIN,
            "/api/auth/verify-email", Group.OTP,
            "/api/auth/reset-password", Group.OTP,
            "/api/auth/forgot-password", Group.SEND,
            "/api/auth/resend-verification", Group.SEND,
            "/api/auth/register", Group.SEND,
            "/api/auth/register-org", Group.SEND);

    @Value("${app.security.auth-rate-limit.enabled:true}") private boolean enabled = true;
    @Value("${app.security.auth-rate-limit.login-per-minute:20}") private int loginLimit = 20;
    @Value("${app.security.auth-rate-limit.otp-per-minute:10}") private int otpLimit = 10;
    @Value("${app.security.auth-rate-limit.send-per-minute:5}") private int sendLimit = 5;
    @Value("${app.security.trust-proxy:false}") private boolean trustProxy;

    private final LongSupplier clock;
    /** Khóa = nhóm + IP. Mỗi cửa sổ bắt đầu ở lần gọi đầu tiên và kéo dài WINDOW_MS. */
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    private static final class Window {
        final long start;
        int count;
        Window(long start) { this.start = start; }
    }

    public AuthRateLimitFilter() { this(System::currentTimeMillis); }

    /** Dùng trong test: đồng hồ giả và cấu hình tùy chọn. */
    AuthRateLimitFilter(LongSupplier clock) { this.clock = clock; }

    AuthRateLimitFilter(LongSupplier clock, boolean enabled, int loginLimit, int otpLimit, int sendLimit, boolean trustProxy) {
        this(clock);
        this.enabled = enabled; this.loginLimit = loginLimit; this.otpLimit = otpLimit; this.sendLimit = sendLimit; this.trustProxy = trustProxy;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        return !enabled || !"POST".equalsIgnoreCase(req.getMethod()) || !PATHS.containsKey(req.getRequestURI());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain) throws ServletException, IOException {
        Group g = PATHS.get(req.getRequestURI());
        int limit = switch (g) { case LOGIN -> loginLimit; case OTP -> otpLimit; case SEND -> sendLimit; };
        String ip = clientIp(req);
        long now = clock.getAsLong();
        int[] count = new int[1];
        long[] start = new long[1];
        // compute chạy nguyên tử trên từng khóa: hai yêu cầu cùng IP gửi đồng thời không thể cùng đọc một số đếm cũ
        windows.compute(g + "|" + ip, (k, old) -> {
            Window w = old == null || now - old.start >= WINDOW_MS ? new Window(now) : old;
            w.count++;
            count[0] = w.count;
            start[0] = w.start;
            return w;
        });
        if (count[0] <= limit) {
            chain.doFilter(req, res);
            return;
        }
        long secs = Math.max(1, (start[0] + WINDOW_MS - now + 999) / 1000);
        if (count[0] == limit + 1) log.warn("Tạm chặn {} từ IP {}: vượt {} yêu cầu/phút", req.getRequestURI(), ip, limit);
        res.setStatus(429);
        res.setHeader("Retry-After", String.valueOf(secs));
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"status\":429,\"code\":\"TOO_MANY_REQUESTS\",\"message\":\"Bạn thao tác quá nhiều lần. Vui lòng thử lại sau "
                + secs + " giây.\",\"errors\":{\"retryAfterSeconds\":\"" + secs + "\"}}");
    }

    private String clientIp(HttpServletRequest req) {
        if (trustProxy) {
            String xff = req.getHeader("X-Forwarded-For");
            if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        }
        return req.getRemoteAddr();
    }

    /** Dọn các cửa sổ đã hết hạn để bộ nhớ không phình theo số IP từng gọi. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void purge() {
        long now = clock.getAsLong();
        windows.values().removeIf(w -> now - w.start >= WINDOW_MS);
    }

    int trackedWindows() { return windows.size(); }
}
