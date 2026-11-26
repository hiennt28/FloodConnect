package vn.floodconnect.user.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;

@Entity @Table(name = "users")
@Getter @Setter @NoArgsConstructor
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false) private String fullName;
    @Column(nullable = false, unique = true) private String email;
    @Column(nullable = false, unique = true, length = 20) private String phone;
    @Column(nullable = false) private String passwordHash;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private Role role;
    /** Admin gốc (Flyway V11): Admin duy nhất được tạo, vô hiệu hóa và mở lại tài khoản Admin khác; vẫn làm mọi việc của Admin thường. */
    @Column(name = "root_admin", nullable = false) private boolean rootAdmin;
    private String avatarUrl;
    private boolean emailVerified;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private UserStatus status = UserStatus.ACTIVE;
    private Instant lockedUntil;
    private int failedLoginCount;
    private Instant loginLockedUntil;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private GpsPermission gpsPermission = GpsPermission.UNKNOWN;
    private Double latitude;
    private Double longitude;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    /** Tài khoản đang trong thời gian khóa (bị hạn chế đăng bài, tương tác, nhận ca...). */
    public boolean isLocked() {
        return status == UserStatus.LOCKED && (lockedUntil == null || lockedUntil.isAfter(Instant.now()));
    }
}
