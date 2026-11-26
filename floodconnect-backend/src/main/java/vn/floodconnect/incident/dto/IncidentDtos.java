package vn.floodconnect.incident.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.dto.RescueDtos.OrgBrief;
import vn.floodconnect.user.dto.UserDtos.UserSummary;

public final class IncidentDtos {
    private IncidentDtos() {}

    public record SosCreateRequest(String description, Double latitude, Double longitude, String address, Boolean forceCreate) {}
    /** Người dân chỉ sửa được mô tả (ca SOS không có ảnh, vị trí không đổi). */
    public record SosUpdateRequest(String description) {}
    /** note: ghi chú xác minh, bắt buộc khi ca đang mang nhãn KHONG (AI xếp, chờ Admin xác minh), không nhận với ca khác. */
    public record AdjustLevelRequest(@NotNull DangerLevel newLevel,
                                     @Size(max = 1000, message = "Ghi chú xác minh tối đa 1000 ký tự.") String note) {}
    public record MarkInvalidRequest(@NotBlank(message = "Vui lòng nhập lý do.") @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {}
    /** Người dân báo "Tôi đã an toàn": ghi chú không bắt buộc. */
    public record MarkSafeRequest(@Size(max = 500, message = "Ghi chú tối đa 500 ký tự.") String note) {}
    /** Admin xác nhận hoàn thành ca Đã tiếp nhận sau khi liên hệ: bắt buộc nhập lý do. */
    public record AdminCompleteRequest(@NotBlank(message = "Vui lòng nhập lý do.") @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {}
    /** Admin hủy phân công ca Đã phân công (tổ chức chưa xác nhận): bắt buộc nhập lý do, được gửi cho tổ chức bị hủy. */
    public record UnassignRequest(@NotBlank(message = "Vui lòng nhập lý do hủy phân công.") @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {}
    public record IncidentDto(
            Long id, String code, String description, String address, double latitude, double longitude,
            IncidentStatus status, DangerLevel aiDangerLevel, DangerLevel dangerLevel,
            boolean needsVerification, List<String> aiReasons, WeatherInfo weather, int nearbySosCount,
            UserSummary citizen, String citizenPhone, OrgBrief assignedOrg,
            Instant createdAt, Instant assignedAt, Instant acceptedAt, Instant completedAt,
            long waitingMinutes, boolean needsAttention, String assignmentStatus,
            MatchingDto matching, Double distanceFromMeM, boolean mine, String invalidReason, Instant invalidatedAt,
            String verifiedByName, Instant verifiedAt, String verificationNote,
            CompletedBy completedByRole, String completionNote, String completedByName,
            /* Lý do của lần phân công gần nhất (tổ chức từ chối, Admin hủy phân công, tổ chức bị khóa / chuyển Không hoạt động,
               người dân hủy ca). Chỉ Admin nhận: lý do từ chối của tổ chức là thông tin nội bộ */
            String assignmentNote) {}
    /** Mọi ca đang hoạt động (ActiveSosService): total = số ca thật, items có tối đa limit ca (mới nhất trước). */
    public record ActiveSosList(List<IncidentDto> items, long total, int limit) {}
    public record CreateSosResponse(boolean duplicateWarning, List<IncidentDto> similar, IncidentDto incident) {}
    public record HeatPoint(double lat, double lng, double weight) {}
    public record MapMarker(String kind, Long id, String label, double lat, double lng, String status, String level, String extra) {}
    public record MapData(List<MapMarker> markers, Double myLat, Double myLng) {}
}
