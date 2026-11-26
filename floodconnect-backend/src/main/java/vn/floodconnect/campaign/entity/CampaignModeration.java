package vn.floodconnect.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

/** decision: KEEP = giữ chiến dịch, REMOVE = dừng chiến dịch. */
@Entity @Table(name = "campaign_moderations")
@Getter @Setter @NoArgsConstructor
public class CampaignModeration {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private FundraisingCampaign campaign;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User admin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ModerationDecision decision;
    @Column(columnDefinition = "text") private String reason;
    @Column(columnDefinition = "text") private String description;
    @CreationTimestamp private Instant createdAt;
}
