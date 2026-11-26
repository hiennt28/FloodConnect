package vn.floodconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.junit.jupiter.api.Assertions.*;

/** Khóa ký JWT: không khởi động với khóa trống / mẫu công khai / quá ngắn; token ký bằng khóa khác bị từ chối. */
class JwtServiceTest {
    private static final String GOOD = "Zr8m3Qx1vK9pL2sT7wY4bN6cD0fH5jA8eG1iU3oR9tV2xZ7q";

    private static JwtService service(String secret) {
        JwtService s = new JwtService();
        ReflectionTestUtils.setField(s, "secret", secret);
        ReflectionTestUtils.setField(s, "accessMinutes", 30L);
        return s;
    }

    @Test void khoaTrongThiKhongKhoiDong() {
        assertThrows(IllegalStateException.class, () -> service("").init());
        assertThrows(IllegalStateException.class, () -> service("   ").init());
        assertThrows(IllegalStateException.class, () -> service(null).init());
    }

    @Test void khoaMauCongKhaiCuThiKhongKhoiDong() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> service("change-me-please-use-a-long-random-secret-at-least-32-bytes").init());
        assertTrue(e.getMessage().contains("JWT_SECRET"));
    }

    @Test void khoaNganHon32ByteThiKhongKhoiDong() {
        assertThrows(IllegalStateException.class, () -> service("a".repeat(31)).init());
        assertDoesNotThrow(() -> service("a".repeat(32)).init());
    }

    @Test void taoVaDocLaiToken() {
        JwtService s = service(GOOD);
        s.init();
        User u = new User();
        u.setId(7L); u.setRole(Role.ADMIN);
        Claims c = s.parse(s.generate(u));
        assertEquals("7", c.getSubject());
        assertEquals("ADMIN", c.get("role", String.class));
    }

    /** Lỗ hổng gốc của #2: ai biết khóa mẫu cũ cũng tự ký được token ADMIN. Nay token đó bị từ chối. */
    @Test void tokenGiaKyBangKhoaMauCuBiTuChoi() {
        JwtService s = service(GOOD);
        s.init();
        String forged = Jwts.builder().subject("1").claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("change-me-please-use-a-long-random-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        assertThrows(SignatureException.class, () -> s.parse(forged));
    }
}
