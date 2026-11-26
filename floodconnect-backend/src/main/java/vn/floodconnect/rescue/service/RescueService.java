package vn.floodconnect.rescue.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.service.OrgContext;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.AssignmentType;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.matching.MatchingService;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.incident.service.IncidentMapper;
import vn.floodconnect.incident.service.SosService;

/** Nghiệp vụ của tổ chức cứu hộ với ca SOS: ca đề xuất, tiếp nhận, xác nhận/từ chối ca được phân công, hoàn thành. */
@Service
@RequiredArgsConstructor
public class RescueService {
    private final IncidentRepository incidents;
    private final IncidentAssignmentRepository assignments;
    private final MatchingService matching;
    private final NotificationPublisher notifications;
    private final CurrentUser current;
    private final OrgContext orgContext;
    private final IncidentMapper incidentMapper;
    private final SosService sos;

    // =====================================================================
    //  Tổ chức cứu hộ
    // =====================================================================
    /**
     * Trả toàn bộ ca "Đã phân công" (tổ chức chưa kịp xác nhận) của một tổ chức về Chờ xử lý, để Admin điều phối lại.
     * Dùng khi tổ chức bị khóa hoặc tự chuyển sang Không hoạt động — không đụng tới ca "Đã tiếp nhận" (đang xử lý dở).
     * Khóa từng ca rồi kiểm tra lại: ca có thể vừa được Admin điều phối cho tổ chức khác hoặc vừa được xác nhận/hủy;
     * khi đó bỏ qua, không ghi đè.
     */
    @Transactional
    public void releaseAssigned(RescueOrganization o, String reason) {
        for (Long id : incidents.findIdsByAssignedOrgIdAndStatusIn(o.getId(), EnumSet.of(IncidentStatus.ASSIGNED))) {
            Incident i = incidents.findByIdForUpdate(id).orElse(null);
            if (i == null || i.getStatus() != IncidentStatus.ASSIGNED
                    || i.getAssignedOrg() == null || !i.getAssignedOrg().getId().equals(o.getId())) continue;
            Instant now = Instant.now();
            assignments.findFirstByIncidentIdOrderByCreatedAtDesc(i.getId()).ifPresent(a -> {
                if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.CANCELLED); a.setRejectReason(reason); a.setRespondedAt(now); }
            });
            notifications.notifyAdmins(NotificationType.SOS_STATUS, "Ca SOS cần điều phối lại",
                    "Ca " + i.getCode() + " được trả về Chờ xử lý (" + reason + "). Cần điều phối lại.", "INCIDENT", i.getId());
            returnToPending(i, o);
        }
    }

    /**
     * Đưa ca Đã phân công về Chờ xử lý: bỏ tổ chức phụ trách, báo người dân và các tổ chức Sẵn sàng gần đó (trừ tổ chức vừa rời ca).
     * Dùng chung cho: tổ chức từ chối, tổ chức bị khóa / chuyển Không hoạt động, Admin hủy phân công, tự trả ca quá hạn.
     * Nơi gọi phải đã khóa dòng ca (findByIdForUpdate), đã kiểm tra ca đang ASSIGNED cho tổ chức from, và tự cập nhật bản ghi
     * phân công cùng thông báo cho Admin (mỗi trường hợp một nội dung khác nhau).
     * KHÔNG đặt lại cờ "đã nhắc ca chờ quá 15 phút": mọi đường trả ca đều đã báo Admin kèm lý do, nếu đặt lại thì ca gửi đã lâu
     * bị nhắc thêm "đã chờ quá 15 phút" ngay phút sau (báo trùng). Ca mới gửi chưa tới mốc thì cờ vẫn chưa bật, vẫn được nhắc như thường.
     */
    public void returnToPending(Incident i, RescueOrganization from) {
        i.setStatus(IncidentStatus.PENDING);
        i.setAssignedOrg(null); i.setAssignedAt(null);
        i.setAssignTimeoutNotified(false);
        notifyReturnedToPending(i, from);
    }

    /**
     * Ca Đã phân công vừa trở về Chờ xử lý: báo người dân để họ biết vì sao trạng thái lùi lại (không nêu lý do của tổ chức),
     * và báo các tổ chức Sẵn sàng gần đó rằng ca lại cần người nhận.
     */
    private void notifyReturnedToPending(Incident i, RescueOrganization from) {
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS đang được điều phối lại",
                "Ca " + i.getCode() + " đang được điều phối cho tổ chức khác.", "INCIDENT", i.getId());
        sos.notifyNearbyOrgs(i, "Ca SOS cần tổ chức tiếp nhận",
                "Ca " + i.getCode() + " mức " + i.getDangerLevel().label + " vừa trở về Chờ xử lý, cần tổ chức tiếp nhận", from.getId());
    }
    /**
     * Danh sách ca Chờ xử lý trong bán kính hoạt động tổ chức tự khai (service_radius_km), xếp theo điểm Matching giảm dần, loại ca
     * tổ chức đã từ chối / không phản hồi. Cùng quy tắc với thông báo ca mới (SosService.notifyNearbyOrgs).
     */
    @Transactional
    public List<IncidentDto> suggested() {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        if (o.getOperatingStatus() != OperatingStatus.AVAILABLE)
            throw ApiException.conflict("ORG_NOT_AVAILABLE", "Tổ chức cần ở trạng thái Sẵn sàng để nhận danh sách ca đề xuất.");
        if (o.getLatitude() == null || o.getLongitude() == null)
            throw ApiException.bad("ORG_NO_LOCATION", "Chưa xác định được vị trí tổ chức, vui lòng cập nhật tọa độ.");
        // Tra một lần cho cả danh sách: ca tổ chức đã từ chối / không phản hồi, và số liệu tải của tổ chức cho AI Matching
        Set<Long> refused = new HashSet<>(assignments.findIncidentIdsByOrgIdAndStatusIn(o.getId(), EnumSet.of(AssignmentStatus.REJECTED, AssignmentStatus.TIMEOUT)));
        List<Incident> cases = incidents.findWithinRadius(EnumSet.of(IncidentStatus.PENDING), Instant.EPOCH, o.getLatitude(), o.getLongitude(), serviceRadiusM(o))
                .stream().filter(i -> i.getDangerLevel() != DangerLevel.KHONG && !refused.contains(i.getId())).toList();
        MatchingService.OrgLoad load = matching.load(o);
        Map<Long, MatchingDto> score = new HashMap<>();
        Map<Long, Double> dist = new HashMap<>();
        for (Incident i : cases) {
            score.put(i.getId(), matching.compute(i, o, false, load));
            dist.put(i.getId(), Geo.haversine(o.getLatitude(), o.getLongitude(), i.getLatitude(), i.getLongitude()));
        }
        List<IncidentDto> out = new ArrayList<>(incidentMapper.toDtos(cases, u, x -> score.get(x.getId()), x -> dist.get(x.getId())));
        out.sort(Comparator.comparingInt((IncidentDto x) -> x.matching().score()).reversed());
        return out;
    }

    /** Bán kính hoạt động của tổ chức (mét). Cột bắt buộc từ V10; dữ liệu nhập tay thiếu thì dùng giá trị mặc định. */
    static double serviceRadiusM(RescueOrganization o) {
        Double km = o.getServiceRadiusKm();
        return (km != null && km > 0 ? km : RescueOrganization.DEFAULT_SERVICE_RADIUS_KM) * 1000;
    }

    /** Ca của tổ chức: filter = ASSIGNED | ACCEPTED | COMPLETED | PENDING (đề xuất) | ALL (mọi ca đã giao cho mình). */
    @Transactional
    public List<IncidentDto> orgCases(String filter) {
        User u = current.get();
        if ("PENDING".equalsIgnoreCase(filter)) return suggested();
        RescueOrganization o = orgContext.allowLocked(u);
        // ACTIVE: ca đang giao cho tổ chức (Đã phân công, Đã tiếp nhận), luôn đủ; giao diện đồng bộ định kỳ dùng bộ lọc này,
        // lịch sử (Hoàn thành, Đã hủy) xem theo trang ở history() thay vì tải lại toàn bộ mỗi 30 giây
        if ("ACTIVE".equalsIgnoreCase(filter))
            return incidentMapper.toDtos(incidents.findByAssignedOrgIdAndStatusIn(o.getId(), EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED))
                    .stream().sorted(Comparator.comparing(Incident::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).toList(), u);
        IncidentStatus st = null;
        if (filter != null && !filter.isBlank() && !"ALL".equalsIgnoreCase(filter)) st = IncidentStatus.valueOf(filter.toUpperCase());
        final IncidentStatus f = st;
        return incidentMapper.toDtos(incidents.findByAssignedOrgIdOrderByCreatedAtDesc(o.getId()).stream()
                .filter(x -> f == null || x.getStatus() == f).toList(), u);
    }

    /** Lịch sử ca của tổ chức (Hoàn thành, Đã hủy khi đang giao cho tổ chức), mới nhất trước, theo trang ("Tải thêm"). */
    @Transactional(readOnly = true)
    public PageResponse<IncidentDto> history(int page, int size) {
        User u = current.get();
        RescueOrganization o = orgContext.allowLocked(u);
        Specification<Incident> spec = (root, q, cb) -> cb.and(cb.equal(root.get("assignedOrg").get("id"), o.getId()),
                root.get("status").in(IncidentStatus.COMPLETED, IncidentStatus.CANCELLED));
        return PageResponse.ofAll(incidents.findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), l -> incidentMapper.toDtos(l, u));
    }

    /** Tổ chức chủ động tiếp nhận ca Chờ xử lý (kiểm tra chống nhận trùng bằng khóa dòng). */
    @Transactional
    public IncidentDto claim(Long id) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        if (o.getOperatingStatus() != OperatingStatus.AVAILABLE)
            throw ApiException.conflict("ORG_NOT_AVAILABLE", "Tổ chức không ở trạng thái Sẵn sàng, không thể tiếp nhận ca mới.");
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getStatus() != IncidentStatus.PENDING || i.getDangerLevel() == DangerLevel.KHONG)
            throw ApiException.conflict("SOS_UNAVAILABLE", "Ca SOS không còn khả dụng (đã có tổ chức tiếp nhận, đã phân công, đã hủy hoặc không hợp lệ).");
        matching.compute(i, o, true);
        Instant now = Instant.now();
        i.setStatus(IncidentStatus.ACCEPTED);
        i.setAssignedOrg(o); i.setAssignedAt(now); i.setAcceptedAt(now);
        IncidentAssignment a = new IncidentAssignment();
        a.setIncident(i); a.setOrg(o); a.setType(AssignmentType.ORG_CLAIM); a.setStatus(AssignmentStatus.ACCEPTED); a.setRespondedAt(now);
        assignments.save(a);
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS đã được tiếp nhận",
                "Tổ chức " + o.getName() + " đã tiếp nhận ca " + i.getCode() + ".", "INCIDENT", i.getId());
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Ca SOS đã được tiếp nhận",
                o.getName() + " đã tiếp nhận ca " + i.getCode() + ".", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, u);
    }

    private Incident assignedToMe(Long id, RescueOrganization o) {
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getAssignedOrg() == null || !i.getAssignedOrg().getId().equals(o.getId()))
            throw ApiException.conflict("SOS_REASSIGNED", "Ca SOS đã được điều phối cho tổ chức khác.");
        return i;
    }

    /** Xác nhận tiếp nhận ca do Admin phân công. */
    @Transactional
    public IncidentDto acceptAssigned(Long id) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        Incident i = assignedToMe(id, o);
        if (i.getStatus() == IncidentStatus.CANCELLED) throw ApiException.conflict("SOS_CANCELLED", "Ca SOS đã bị hủy, không thể tiếp nhận.");
        if (i.getStatus() != IncidentStatus.ASSIGNED) throw ApiException.conflict("SOS_NOT_ASSIGNED", "Ca SOS không còn ở trạng thái Đã phân công.");
        Instant now = Instant.now();
        i.setStatus(IncidentStatus.ACCEPTED); i.setAcceptedAt(now);
        assignments.findFirstByIncidentIdOrderByCreatedAtDesc(id).ifPresent(a -> { a.setStatus(AssignmentStatus.ACCEPTED); a.setRespondedAt(now); });
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS đã được tiếp nhận",
                "Tổ chức " + o.getName() + " đã xác nhận tiếp nhận ca " + i.getCode() + ".", "INCIDENT", i.getId());
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Tổ chức xác nhận ca",
                o.getName() + " đã xác nhận tiếp nhận ca " + i.getCode() + ".", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, u);
    }

    /**
     * Từ chối ca được phân công: ghi lý do (vào lịch sử từ chối cho AI Matching), ca trở về Chờ xử lý; báo Admin (kèm lý do),
     * người dân và các tổ chức Sẵn sàng gần đó.
     */
    @Transactional
    public IncidentDto rejectAssigned(Long id, String reason) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        Incident i = assignedToMe(id, o);
        if (i.getStatus() != IncidentStatus.ASSIGNED) throw ApiException.conflict("SOS_NOT_ASSIGNED", "Ca SOS không còn ở trạng thái Đã phân công.");
        if (reason == null || reason.isBlank()) throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do từ chối.");
        Instant now = Instant.now();
        assignments.findFirstByIncidentIdOrderByCreatedAtDesc(id).ifPresent(a -> {
            a.setStatus(AssignmentStatus.REJECTED); a.setRejectReason(reason); a.setRespondedAt(now);
        });
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Tổ chức từ chối ca",
                o.getName() + " từ chối ca " + i.getCode() + (reason == null || reason.isBlank() ? "" : ": " + reason) + ". Cần điều phối lại.",
                "INCIDENT", i.getId());
        returnToPending(i, o);
        return incidentMapper.toDto(i, u);
    }

    /** Cập nhật trạng thái cứu hộ: Đã tiếp nhận -> Hoàn thành. Tổ chức bị khóa vẫn được hoàn tất ca đang phụ trách. */
    @Transactional
    public IncidentDto complete(Long id) {
        User u = current.get();
        RescueOrganization o = orgContext.allowLocked(u);
        Incident i = assignedToMe(id, o);
        if (i.getStatus() != IncidentStatus.ACCEPTED)
            throw ApiException.conflict("INVALID_TRANSITION", "Chỉ ca ở trạng thái Đã tiếp nhận mới chuyển được sang Hoàn thành.");
        i.setStatus(IncidentStatus.COMPLETED); i.setCompletedAt(Instant.now());
        i.setCompletedByRole(CompletedBy.ORG); i.setCompletedBy(u);
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS hoàn thành",
                "Ca " + i.getCode() + " đã được " + o.getName() + " cứu hộ xong.", "INCIDENT", i.getId());
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Ca SOS hoàn thành", "Ca " + i.getCode() + " đã hoàn thành.", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, u);
    }
}
