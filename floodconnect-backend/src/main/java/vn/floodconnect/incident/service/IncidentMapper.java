package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.user.service.UserMapper;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.dto.RescueDtos.OrgBrief;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import org.springframework.stereotype.Component;
import static vn.floodconnect.incident.service.SosService.ASSIGN_TIMEOUT_MIN;
import static vn.floodconnect.incident.service.SosService.PENDING_TIMEOUT_MIN;

/**
 * Chuyển ca SOS sang DTO, ẩn nội dung chi tiết, thông tin người gửi và SĐT tổ chức theo quyền của người xem.
 *
 * Danh sách ca dùng {@link #toDtos}: dữ liệu ngoài bảng incidents (đánh giá AI mới nhất, lần phân công gần nhất, tổ chức của
 * người xem, tên tổ chức của người gửi là tài khoản tổ chức) được tải MỘT LẦN cho cả danh sách, thay vì vài truy vấn cho mỗi ca
 * (lỗi N+1). Người gửi, tổ chức phụ trách... là quan hệ lazy, được Hibernate tải theo lô (hibernate.default_batch_fetch_size).
 * Một ca lẻ dùng {@link #toDto}, đi chung đường với danh sách một phần tử nên hai cách không thể cho kết quả khác nhau.
 */
@Component
@RequiredArgsConstructor
public class IncidentMapper {
    private final IncidentAiAssessmentRepository assessments;
    private final IncidentAssignmentRepository assignments;
    private final RescueOrganizationRepository orgs;
    private final UserMapper userMapper;
    private final ObjectMapper json;

    /** Dữ liệu dùng chung của một lần chuyển danh sách, tải một lần cho cả danh sách. */
    private record Batch(boolean viewerActiveOrg, Map<Long, IncidentAiAssessment> latestAi,
                         Map<Long, IncidentAssignment> lastAssignment, Map<Long, String> senderOrgNames) {}

    private Batch batch(List<Incident> list, User viewer) {
        // Tổ chức đang hoạt động được xem chi tiết mọi ca (để quyết định tiếp nhận): chỉ phụ thuộc người xem, tra một lần
        boolean activeOrg = viewer != null && viewer.getRole() == Role.RESCUE_ORG
                && orgs.findByUserId(viewer.getId()).map(o -> o.getStatus() == OrgStatus.ACTIVE).orElse(false);
        List<Long> ids = list.stream().map(Incident::getId).filter(Objects::nonNull).toList();
        Map<Long, IncidentAiAssessment> ai = new HashMap<>();
        Map<Long, IncidentAssignment> asg = new HashMap<>();
        if (!ids.isEmpty()) {
            // Truy vấn trả mới nhất trước: giữ bản đầu tiên gặp của mỗi ca
            for (IncidentAiAssessment a : assessments.findByIncidentIdsLatestFirst(ids)) ai.putIfAbsent(a.getIncident().getId(), a);
            for (IncidentAssignment a : assignments.findByIncidentIdsLatestFirst(ids)) asg.putIfAbsent(a.getIncident().getId(), a);
        }
        Map<Long, String> orgNames = userMapper.orgNames(list.stream().map(Incident::getCitizen).toList());
        return new Batch(activeOrg, ai, asg, orgNames);
    }

    @Transactional(readOnly = true)
    public List<IncidentDto> toDtos(List<Incident> list, User viewer) { return toDtos(list, viewer, x -> null, x -> null); }

    /** matching / distMe: điểm Matching và khoảng cách tới người xem của từng ca (ca đề xuất của tổ chức), null nếu không có. */
    @Transactional(readOnly = true)
    public List<IncidentDto> toDtos(List<Incident> list, User viewer, Function<Incident, MatchingDto> matching, Function<Incident, Double> distMe) {
        if (list.isEmpty()) return List.of();
        Batch b = batch(list, viewer);
        return list.stream().map(i -> build(i, viewer, matching.apply(i), distMe.apply(i), b)).toList();
    }

    @Transactional(readOnly = true)
    public IncidentDto toDto(Incident i, User viewer, MatchingDto m, Double distMe) { return build(i, viewer, m, distMe, batch(List.of(i), viewer)); }

    public IncidentDto toDto(Incident i, User viewer) { return toDto(i, viewer, null, null); }

    private IncidentDto build(Incident i, User viewer, MatchingDto m, Double distMe, Batch b) {
        boolean admin = viewer != null && viewer.getRole() == Role.ADMIN;
        boolean owner = viewer != null && i.getCitizen().getId().equals(viewer.getId());
        boolean assignedOrgUser = viewer != null && i.getAssignedOrg() != null && i.getAssignedOrg().getUser().getId().equals(viewer.getId());
        // Mô tả, địa chỉ, lý do AI và người gửi: chỉ Admin, chủ ca, tổ chức phụ trách ca và tổ chức đang hoạt động
        // (cần xem để quyết định tiếp nhận). Tổ chức bị khóa chỉ còn thấy ca mình đang phụ trách. Người khác chỉ thấy loại,
        // mức độ, trạng thái và tọa độ đã làm tròn (bản đồ công khai cần tọa độ).
        boolean showDetail = admin || owner || assignedOrgUser || b.viewerActiveOrg();
        boolean showOrgPhone = admin || owner || assignedOrgUser;

        IncidentAiAssessment ai = b.latestAi().get(i.getId());
        List<String> reasons = !showDetail || ai == null || ai.getReasons() == null || ai.getReasons().isBlank() ? List.of() : Arrays.asList(ai.getReasons().split("\n"));
        WeatherInfo w = null;
        if (ai != null && ai.getWeatherData() != null) {
            try { w = json.readValue(ai.getWeatherData(), WeatherInfo.class); } catch (Exception ignored) { }
            // Ca chấm trước khi bỏ hàm ước lượng: lượng mưa "Dự phòng" là số liệu giả, không hiển thị như dữ liệu thật
            if (w != null && "Dự phòng".equals(w.source())) w = null;
        }
        OrgBrief ob = null;
        if (i.getAssignedOrg() != null) {
            RescueOrganization o = i.getAssignedOrg();
            // SĐT và tọa độ trụ sở của tổ chức phụ trách: chỉ Admin, chủ ca (bản đồ hiện trụ sở đội đang cứu mình) và chính tổ chức đó.
            // Người khác chỉ thấy tên tổ chức: trụ sở công khai chỉ tới Phường/Xã (nhóm thiện nguyện hay dùng nhà riêng làm trụ sở)
            ob = new OrgBrief(o.getId(), o.getName(), o.getOrgType(), o.getOperatingStatus(),
                    showOrgPhone ? o.getLatitude() : null, showOrgPhone ? o.getLongitude() : null,
                    showOrgPhone ? o.getUser().getPhone() : null);
        }
        Instant now = Instant.now();
        long waiting = switch (i.getStatus()) {
            case PENDING -> Duration.between(i.getCreatedAt(), now).toMinutes();
            case ASSIGNED -> i.getAssignedAt() == null ? 0 : Duration.between(i.getAssignedAt(), now).toMinutes();
            default -> 0;
        };
        boolean attention = (i.getStatus() == IncidentStatus.PENDING && (i.getDangerLevel() == DangerLevel.KHONG || waiting >= PENDING_TIMEOUT_MIN))
                || (i.getStatus() == IncidentStatus.ASSIGNED && waiting >= ASSIGN_TIMEOUT_MIN)
                || (i.getStatus() == IncidentStatus.ACCEPTED && i.isAcceptedTimeoutNotified()); // cứu hộ kéo dài, đã nhắc
        // Ca hoàn thành trước khi có cột completedByRole: do tổ chức bấm Hoàn thành
        CompletedBy completedBy = i.getStatus() != IncidentStatus.COMPLETED ? null
                : i.getCompletedByRole() != null ? i.getCompletedByRole() : CompletedBy.ORG;
        IncidentAssignment last = b.lastAssignment().get(i.getId());
        // Người không có quyền xem chi tiết chỉ nhận tọa độ đã làm tròn (~100 m): đủ để hiện khu vực trên bản đồ, không chỉ ra căn nhà
        return new IncidentDto(i.getId(), i.getCode(), showDetail ? i.getDescription() : null, showDetail ? i.getAddress() : null,
                showDetail ? i.getLatitude() : Geo.coarse(i.getLatitude()), showDetail ? i.getLongitude() : Geo.coarse(i.getLongitude()),
                i.getStatus(), i.getAiDangerLevel(), i.getDangerLevel(), i.isNeedsVerification(), reasons, w,
                ai == null ? 0 : ai.getNearbySosCount(),
                showDetail ? userMapper.user(i.getCitizen(), b.senderOrgNames()) : null, showDetail ? i.getCitizen().getPhone() : null, ob,
                i.getCreatedAt(), i.getAssignedAt(), i.getAcceptedAt(), i.getCompletedAt(), waiting, attention,
                last == null ? null : last.getStatus().name(), m, distMe, owner,
                admin || owner ? i.getInvalidReason() : null, i.getInvalidatedAt(),
                // Ghi chú xác minh đi cùng mô tả (cùng người được xem). Thời điểm xác minh: Admin luôn thấy; người khác chỉ thấy
                // khi có ghi chú (để biết ghi chú viết lúc nào). Tên Admin xác minh chỉ Admin thấy
                admin && i.getVerifiedBy() != null ? i.getVerifiedBy().getFullName() : null,
                admin || (showDetail && i.getVerificationNote() != null) ? i.getVerifiedAt() : null,
                showDetail ? i.getVerificationNote() : null,
                completedBy, admin || owner || assignedOrgUser ? i.getCompletionNote() : null,
                admin && i.getCompletedBy() != null ? i.getCompletedBy().getFullName() : null,
                // Lý do lần phân công gần nhất (từ chối, hủy phân công, tổ chức bị khóa...): chỉ Admin, lý do từ chối là thông tin nội bộ
                admin && last != null ? last.getRejectReason() : null);
    }
}
