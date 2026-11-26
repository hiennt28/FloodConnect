package vn.floodconnect.campaign.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.floodconnect.organization.entity.RescueOrganization;
import java.time.Instant;

@Entity @Table(name = "fundraising_campaigns")
@Getter @Setter @NoArgsConstructor
public class FundraisingCampaign {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RescueOrganization org;
    @Column(nullable = false, length = 200) private String name;
    private String avatarUrl;
    @Column(columnDefinition = "text") private String purpose;
    @Column(columnDefinition = "text") private String supportArea;
    @Column(columnDefinition = "text") private String targetAudience;
    @Column(columnDefinition = "text") private String orgInfo;
    @Column(nullable = false) private long targetAmount;
    @Column(nullable = false) private long raisedAmount;
    /** Tài khoản ngân hàng của tổ chức nhận tiền ủng hộ (công khai). Người ủng hộ trả qua VNPay vào tài khoản hệ thống. */
    @Column(length = 100) private String bankName;
    private String accountHolder;
    private String accountNumber;
    @Column(nullable = false) private Instant startDate;
    @Column(nullable = false) private Instant endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private CampaignStatus status = CampaignStatus.UPCOMING;
    @Enumerated(EnumType.STRING) @Column(length = 30) private EndReason endReason;
    private Instant endedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
    /**
     * Khóa lạc quan: hai giao dịch cùng sửa một chiến dịch (vd. tổ chức sửa nội dung đúng lúc một khoản ủng hộ được cộng tiền)
     * thì giao dịch sau bị từ chối thay vì ghi đè raisedAmount bằng số cũ. Mặc định 0 để dữ liệu cũ không lỗi khi thêm cột.
     */
    @Version @Column(columnDefinition = "bigint default 0 not null") private long version;
}
