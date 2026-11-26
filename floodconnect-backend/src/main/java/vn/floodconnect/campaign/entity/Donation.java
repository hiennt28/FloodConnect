package vn.floodconnect.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "donations")
@Getter @Setter @NoArgsConstructor
public class Donation {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private FundraisingCampaign campaign;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User donor;
    @Column(nullable = false) private long amount;
    @Column(length = 500) private String message;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private PaymentMethod paymentMethod;
    private boolean anonymous;
    @Column(unique = true) private String transactionCode;
    private String gatewayRef;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DonationStatus status = DonationStatus.PENDING;
    @CreationTimestamp private Instant createdAt;
    private Instant completedAt;
}
