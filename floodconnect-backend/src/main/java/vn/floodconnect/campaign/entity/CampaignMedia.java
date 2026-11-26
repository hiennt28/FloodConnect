package vn.floodconnect.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity @Table(name = "campaign_media")
@Getter @Setter @NoArgsConstructor
public class CampaignMedia {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private FundraisingCampaign campaign;
    @Column(nullable = false) private String mediaUrl;
    private int sortOrder;
    @CreationTimestamp private Instant createdAt;
}
