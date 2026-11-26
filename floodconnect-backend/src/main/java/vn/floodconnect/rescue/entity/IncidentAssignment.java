package vn.floodconnect.rescue.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "incident_assignments")
@Getter @Setter @NoArgsConstructor
public class IncidentAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Incident incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RescueOrganization org;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AssignmentType type;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private AssignmentStatus status;
    @ManyToOne(fetch = FetchType.LAZY) private User assignedBy;
    @Column(columnDefinition = "text") private String rejectReason;
    @CreationTimestamp private Instant createdAt;
    private Instant respondedAt;
}
