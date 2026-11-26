package vn.floodconnect.notification.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "notifications")
@Getter @Setter @NoArgsConstructor
public class Notification {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User user;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private NotificationType type;
    private String title;
    @Column(columnDefinition = "text") private String content;
    /** Loại đối tượng liên quan: POST, INCIDENT, CAMPAIGN, DONATION, ORG, USER (null: không có trang nào để mở, vd. bài đã bị xóa). */
    @Column(length = 20) private String referenceType;
    private Long referenceId;
    /** Đối tượng con bên trong referenceId để mở đúng chỗ, vd. mã bình luận/phản hồi trong bài viết (POST). */
    private Long subReferenceId;
    /** Dùng để gộp thông báo (ví dụ "A và 5 người khác đã thích bài viết"). */
    private String groupKey;
    private int groupCount = 1;
    @Column(name = "is_read", nullable = false) private Boolean read = false;
    @CreationTimestamp private Instant createdAt;
}
