package vn.floodconnect.post.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "post_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "reporter_id"}))
@Getter @Setter @NoArgsConstructor
public class PostReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Post post;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User reporter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PostReportReason reason;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReportStatus status = ReportStatus.PENDING;
    @CreationTimestamp private Instant createdAt;
}
