package vn.floodconnect.user.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "account_locks")
@Getter @Setter @NoArgsConstructor
public class AccountLock {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    @ManyToOne(fetch = FetchType.LAZY) private User lockedBy;
    @Column(columnDefinition = "text") private String reason;
    private int durationDays;
    @Column(nullable = false) private Instant lockedAt;
    private Instant lockedUntil;
    private Instant unlockedAt;
    @ManyToOne(fetch = FetchType.LAZY) private User unlockedBy;
}
