package vn.floodconnect.incident.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "incident_danger_adjustments")
@Getter @Setter @NoArgsConstructor
public class IncidentDangerAdjustment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Incident incident;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User admin;
    @Enumerated(EnumType.STRING) @Column(length = 20) private DangerLevel oldLevel;
    @Enumerated(EnumType.STRING) @Column(length = 20) private DangerLevel newLevel;
    @CreationTimestamp private Instant createdAt;
}
