package vn.floodconnect.incident.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import java.time.Instant;

@Entity @Table(name = "incident_ai_assessments")
@Getter @Setter @NoArgsConstructor
public class IncidentAiAssessment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) private Incident incident;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private DangerLevel predictedLevel;
    /** Mức chỉ theo nội dung (trước thời tiết / ca lân cận). Dịch vụ AI dùng mức này của các ca lân cận để tính cụm,
     *  không dùng mức cuối đã bị nâng, để các ca không nâng mức qua lại cho nhau. NULL: bản đánh giá trước Flyway V9. */
    @Enumerated(EnumType.STRING) @Column(length = 20) private DangerLevel contentLevel;
    /** Nguồn đánh giá: AI_SERVICE (dịch vụ floodconnect-ai) hoặc RULES (bộ quy tắc dự phòng trong Java). NULL: trước V9. */
    @Column(length = 20) private String source;
    private Double confidence;
    private boolean needsVerification;
    /** JSON: lượng mưa, dự báo, cảnh báo thiên tai. */
    @Column(columnDefinition = "text") private String weatherData;
    private int nearbySosCount;
    /** Lý do đánh giá (mỗi dòng một lý do). */
    @Column(columnDefinition = "text") private String reasons;
    @CreationTimestamp private Instant createdAt;
}
