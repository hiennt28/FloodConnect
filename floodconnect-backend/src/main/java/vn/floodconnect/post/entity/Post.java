package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "posts")
@Getter @Setter @NoArgsConstructor
public class Post {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User author;
    @Column(nullable = false, length = 2000) private String content;
    /** Tọa độ của vị trí: GPS của người đăng (nếu bấm "Vị trí GPS" và nằm đúng xã đã chọn), không thì điểm đại diện của xã. */
    private Double latitude;
    private Double longitude;
    /** Địa chỉ người đăng chọn: mã phường/xã (cũng cho biết tỉnh) và phần chi tiết tự gõ (thôn/xóm, số nhà, đường). */
    @Column(length = 5) private String wardCode;
    @Column(length = 100) private String addressDetail;
    /** Địa chỉ đầy đủ để hiển thị, server ghép sẵn: "Thôn 3, Xã Hòa Vang, Thành phố Đà Nẵng". */
    @Column(length = 200) private String locationName;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ContentStatus status = ContentStatus.VISIBLE;
    private boolean edited;
    private Instant deletedAt;
    @ManyToOne(fetch = FetchType.LAZY) private User deletedBy;
    @Column(columnDefinition = "text") private String deleteReason;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
