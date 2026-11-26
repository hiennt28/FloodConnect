package vn.floodconnect.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "campaign_reports",
        uniqueConstraints = @UniqueConstraint(columnNames = {"campaign_id", "reporter_id"}))
@Getter @Setter @NoArgsConstructor
public class CampaignReport {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private FundraisingCampaign campaign;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User reporter;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CampaignReportReason reason;
    @Column(columnDefinition = "text") private String description;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReportStatus status = ReportStatus.PENDING;
    @CreationTimestamp private Instant createdAt;
}
