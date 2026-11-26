package vn.floodconnect.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "rescue_organizations")
@Getter @Setter @NoArgsConstructor
public class RescueOrganization {
    public static final int MIN_CONCURRENT_CASES = 1, MAX_CONCURRENT_CASES = 50, DEFAULT_MAX_CONCURRENT_CASES = 3;
    public static final double MIN_SERVICE_RADIUS_KM = 1, MAX_SERVICE_RADIUS_KM = 100, DEFAULT_SERVICE_RADIUS_KM = 35;
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @OneToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(unique = true) private User user;
    @Column(nullable = false) private String name;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrgType orgType;
    private String province;
    private String district;
    private String ward;
    private String streetAddress;
    @Column(columnDefinition = "text") private String description;
    private Double latitude;
    private Double longitude;
    @Column(columnDefinition = "text") private String serviceAreas;
    /** Số ca tổ chức xử lý được cùng lúc (tự khai báo, bắt buộc). AI Matching: điểm tải W và ngưỡng quá tải. */
    @Column(name = "max_concurrent_cases", nullable = false) private Integer maxConcurrentCases = DEFAULT_MAX_CONCURRENT_CASES;
    /** Bán kính hoạt động tính từ trụ sở, km (tự khai báo, bắt buộc). AI Matching: vùng phục vụ của điểm vị trí L. */
    @Column(name = "service_radius_km", nullable = false) private Double serviceRadiusKm = DEFAULT_SERVICE_RADIUS_KM;
    /** Trang web / fanpage (không bắt buộc, công khai trên hồ sơ tổ chức); luôn bắt đầu bằng http:// hoặc https://. */
    @Column(length = 500) private String websiteUrl;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OrgStatus status = OrgStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private OperatingStatus operatingStatus = OperatingStatus.INACTIVE;
    @Column(columnDefinition = "text") private String rejectionReason;
    private Instant lockedAt;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;
}
