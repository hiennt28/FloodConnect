package vn.floodconnect.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import vn.floodconnect.user.entity.User;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Set;

/**
 * Tạo và kiểm tra access token (JWT ký HMAC-SHA256 bằng JWT_SECRET).
 * Khóa ký là thứ duy nhất ngăn người khác tự tạo token mang role ADMIN, nên ứng dụng KHÔNG khởi động khi khóa trống,
 * quá ngắn hoặc là chuỗi mẫu từng nằm công khai trong mã nguồn (trước đây application.yml có giá trị mặc định).
 */
@Service
public class JwtService {
    /** Số byte tối thiểu của khóa HMAC-SHA256 (256 bit). */
    static final int MIN_SECRET_BYTES = 32;
    /** Các chuỗi mẫu đã công khai (mặc định cũ trong application.yml, giá trị ví dụ): dùng chúng thì ai cũng ký được token. */
    static final Set<String> KNOWN_PUBLIC_SECRETS = Set.of(
            "change-me-please-use-a-long-random-secret-at-least-32-bytes",
            "your_jwt_secret");

    @Value("${app.jwt.secret:}") private String secret;
    @Value("${app.jwt.access-minutes}") private long accessMinutes;
    private SecretKey key;

    @PostConstruct
    void init() {
        String s = secret == null ? "" : secret.trim();
        if (s.isEmpty() || KNOWN_PUBLIC_SECRETS.contains(s) || s.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES)
            throw new IllegalStateException("JWT_SECRET chưa được cấu hình an toàn (trống, là chuỗi mẫu công khai hoặc ngắn hơn "
                    + MIN_SECRET_BYTES + " byte). Hãy đặt JWT_SECRET là một chuỗi ngẫu nhiên dài trong file .env, ví dụ tạo bằng PowerShell: "
                    + "$b = New-Object byte[] 48; [Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($b); [Convert]::ToBase64String($b)");
        key = Keys.hmacShaKeyFor(s.getBytes(StandardCharsets.UTF_8));
    }

    public long accessSeconds() { return accessMinutes * 60; }

    public String generate(User u) {
        Instant now = Instant.now();
        return Jwts.builder().subject(String.valueOf(u.getId())).claim("role", u.getRole().name())
                .issuedAt(Date.from(now)).expiration(Date.from(now.plusSeconds(accessSeconds())))
                .signWith(key).compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
    }
}
