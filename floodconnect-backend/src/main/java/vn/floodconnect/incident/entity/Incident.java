package vn.floodconnect.incident.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.User;
import java.time.Instant;

@Entity @Table(name = "incidents")
@Getter @Setter @NoArgsConstructor
public class Incident {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String code;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private User citizen;
    @Column(nullable = false, columnDefinition = "text") private String description;
    private String address;
    @Column(nullable = false) private double latitude;
    @Column(nullable = false) private double longitude;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private IncidentStatus status = IncidentStatus.PENDING;
    @Enumerated(EnumType.STRING) @Column(length = 20) private DangerLevel aiDangerLevel;
    private boolean needsVerification;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DangerLevel dangerLevel = DangerLevel.THAP;
    /** Thứ hạng số của dangerLevel, dùng để sắp xếp theo mức nguy hiểm. */
    @Column(nullable = false) private int dangerRank = 1;
    @ManyToOne(fetch = FetchType.LAZY) private RescueOrganization assignedOrg;
    private Instant assignedAt;
    private Instant acceptedAt;
    private Instant completedAt;
    /** Ai đưa ca sang Hoàn thành (null với ca cũ = tổ chức), tài khoản đã bấm và ghi chú/lý do (Admin bắt buộc nhập lý do). */
    @Enumerated(EnumType.STRING) @Column(length = 20) private CompletedBy completedByRole;
    @ManyToOne(fetch = FetchType.LAZY) private User completedBy;
    @Column(length = 500) private String completionNote;
    private boolean pendingTimeoutNotified;
    private boolean assignTimeoutNotified;
    /** Đã nhắc tổ chức và Admin vì ca Đã tiếp nhận quá lâu chưa hoàn thành. Có default để ddl-auto thêm cột được vào bảng đã có dữ liệu. */
    @Column(columnDefinition = "boolean default false not null") private boolean acceptedTimeoutNotified;
    /** Ca INVALID: lý do, Admin kết luận và thời điểm kết luận. */
    @Column(length = 500) private String invalidReason;
    @ManyToOne(fetch = FetchType.LAZY) private User invalidatedBy;
    private Instant invalidatedAt;
    /** Admin đã liên hệ xác minh và gỡ cảnh báo "nên xác minh" (giữ mức hoặc chọn mức mới); xóa khi AI lại gắn cảnh báo. */
    @ManyToOne(fetch = FetchType.LAZY) private User verifiedBy;
    private Instant verifiedAt;
    /** Ca AI xếp KHONG được Admin xác minh và chọn lại mức: thông tin Admin hỏi được khi liên hệ người gửi (bắt buộc), để tổ chức
     *  nắm tình hình khi mô tả gốc quá ít. Mô tả của người dân không bị sửa. */
    @Column(length = 1000) private String verificationNote;
    @CreationTimestamp private Instant createdAt;
    @UpdateTimestamp private Instant updatedAt;

    public void setDangerLevel(DangerLevel level) {
        this.dangerLevel = level;
        this.dangerRank = level.weight;
    }
}
