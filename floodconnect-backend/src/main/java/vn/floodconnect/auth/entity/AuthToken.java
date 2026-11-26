package vn.floodconnect.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "auth_tokens")
@Getter @Setter @NoArgsConstructor
public class AuthToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    @Column(nullable = false) private String tokenHash;   // OTP: không còn unique toàn cục (mã 6 số có thể trùng ngẫu nhiên giữa 2 người)
    private int failedAttempts;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private AuthTokenType type;
    @Column(nullable = false) private Instant expiresAt;
    private Instant usedAt;
    @CreationTimestamp private Instant createdAt;
}
