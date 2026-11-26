package vn.floodconnect.auth.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;
import vn.floodconnect.user.entity.User;

@Entity @Table(name = "refresh_tokens")
@Getter @Setter @NoArgsConstructor
public class RefreshToken {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    @Column(nullable = false, unique = true) private String tokenHash;
    @Column(nullable = false) private Instant expiresAt;
    private Instant revokedAt;
    @CreationTimestamp private Instant createdAt;
}
