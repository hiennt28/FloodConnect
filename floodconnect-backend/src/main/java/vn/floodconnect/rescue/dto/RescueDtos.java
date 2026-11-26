package vn.floodconnect.rescue.dto;

import jakarta.validation.constraints.*;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgType;
import java.time.Instant;
import java.util.List;

public final class RescueDtos {
    private RescueDtos() {}

    public record AssignRequest(@NotNull Long orgId) {}
    public record RejectRequest(String reason) {}
    /** Điểm AI Matching = 0,25·L + 0,30·S + 0,30·C + 0,15·W; capabilityScore = C (năng lực). */
    /**
     * weights: trọng số (%) của L, S, C, W dùng cho điểm này. Dịch vụ AI đổi trọng số theo mức đầy đủ của tin SOS (tin càng ít thông
     * tin càng dựa vào vị trí, tải); quy tắc dự phòng luôn 25/30/30/15; null khi AI loại tổ chức (điểm 0, xem reason). source: AI_SERVICE | RULES.
     */
    public record MatchingDto(int score, int locationScore, int semanticScore, int capabilityScore, int workloadScore,
                              double distanceM, int activeCases, int refusedCount, String reason,
                              java.util.Map<String, Integer> weights, String source) {}
    public record OrgBrief(Long id, String name, OrgType orgType, OperatingStatus operatingStatus,
                           Double latitude, Double longitude, String phone) {}
    /**
     * nearbyCases: ca tổ chức đang xử lý trong 5 km quanh ca đang điều phối (tổ chức đang có mặt tại khu vực).
     * busyIfAssigned: nhận thêm ca này thì tổ chức đạt >= 70% số ca xử lý cùng lúc; khi đó các ca gần mới bị trừ điểm tải.
     */
    public record CandidateOrgDto(OrgBrief org, MatchingDto matching, boolean canTake, List<NearbyActiveCase> nearbyCases,
                                  boolean busyIfAssigned, Integer maxConcurrentCases, Double serviceRadiusKm) {}
    public record NearbyActiveCase(Long id, String code, double distanceM) {}
    /** Ca lân cận của một ca (Admin xem khi xác minh/điều phối): distanceM là khoảng cách giữa hai ca. */
    public record NearbyCaseDto(IncidentDto incident, double distanceM) {}

    /**
     * Một ca trên bản đồ điều phối của Admin: chỉ dữ liệu để vẽ điểm và dòng danh sách (không có mô tả, người gửi, đánh giá AI;
     * mở chi tiết thì gọi /api/admin/sos/{id}). Tên trường giống IncidentDto để giao diện dùng chung bộ chuyển dữ liệu ca SOS.
     */
    public record AdminMapCase(Long id, String code, double latitude, double longitude, IncidentStatus status, DangerLevel dangerLevel,
                               boolean needsVerification, String address, OrgRef assignedOrg,
                               Instant createdAt, Instant completedAt, Instant invalidatedAt) {}
    public record OrgRef(Long id, String name) {}
    /**
     * Bản đồ điều phối của Admin. total = số ca khớp bộ lọc; cases có tối đa limit ca (ca đang hoạt động trước, rồi ca kết thúc
     * gần nhất); total > cases.size() nghĩa là đã chạm trần. days = khoảng thời gian đã áp dụng cho ca đã kết thúc.
     */
    public record AdminSosMap(List<AdminMapCase> cases, long total, int limit, int days) {}
}
