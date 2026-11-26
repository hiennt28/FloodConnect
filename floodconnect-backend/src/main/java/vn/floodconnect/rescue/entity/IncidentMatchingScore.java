package vn.floodconnect.rescue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.organization.entity.RescueOrganization;

@Entity @Table(name = "incident_matching_scores",
        uniqueConstraints = @UniqueConstraint(columnNames = {"incident_id", "org_id"}))
@Getter @Setter @NoArgsConstructor
public class IncidentMatchingScore {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Incident incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RescueOrganization org;
    private double locationScore;
    private double semanticScore;
    /** Điểm năng lực C (V7). NULL ở dòng chấm trước khi có C (công thức cũ). */
    private Double capabilityScore;
    private double workloadScore;
    private double matchingScore;
    private double distanceM;
    @UpdateTimestamp private Instant createdAt;
}
