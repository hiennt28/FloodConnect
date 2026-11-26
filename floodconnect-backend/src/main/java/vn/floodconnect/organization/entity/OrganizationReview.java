package vn.floodconnect.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "organization_reviews")
@Getter @Setter @NoArgsConstructor
public class OrganizationReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RescueOrganization org;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User admin;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private ReviewDecision decision;
    @Column(columnDefinition = "text") private String reason;
    @CreationTimestamp private Instant createdAt;
}
