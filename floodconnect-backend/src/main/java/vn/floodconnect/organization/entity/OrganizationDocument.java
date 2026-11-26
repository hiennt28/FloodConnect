package vn.floodconnect.organization.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity @Table(name = "organization_documents")
@Getter @Setter @NoArgsConstructor
public class OrganizationDocument {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private RescueOrganization org;
    /** Ô hồ sơ của tệp. Rỗng với tệp nộp trước khi tách ô (xem OrgDocumentService.slotOf). */
    @Enumerated(EnumType.STRING) @Column(length = 20) private OrgDocSlot slot;
    private String docLabel;
    /**
     * Mã tệp nội bộ (lưu riêng tư, Flyway V6): "local:..." hoặc "cloudinary:...". KHÔNG phải đường dẫn công khai, không trả ra API;
     * tải qua GET /api/org-docs/{id}/file. Dữ liệu cũ / ảnh mẫu có thể còn là đường dẫn http(s).
     */
    @Column(nullable = false) private String storageKey;
    @Column(length = 10) private String fileType;
    @CreationTimestamp private Instant createdAt;
}
