package vn.floodconnect.rescue.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.incident.dto.IncidentDtos.HeatPoint;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentDangerAdjustment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentDangerAdjustmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.IncidentMapper;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.incident.service.StatusFilter;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.dto.RescueDtos.CandidateOrgDto;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.dto.RescueDtos.NearbyActiveCase;
import vn.floodconnect.rescue.dto.RescueDtos.NearbyCaseDto;
import vn.floodconnect.rescue.dto.RescueDtos.OrgBrief;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.AssignmentType;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.matching.MatchingService;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Nghiệp vụ ca SOS của Admin: danh sách, điều chỉnh nhãn, chỉ định/điều phối lại tổ chức, bản đồ nhiệt. */
@Service
@RequiredArgsConstructor
public class SosAdminService {
    /** Ca đã hoàn thành chỉ tính là "lân cận" nếu gửi không sớm hơn ca đang xem quá khoảng này (có thể là cùng sự cố đã được cứu). */
    static final Duration NEARBY_DONE_WINDOW = Duration.ofHours(24);

    private final IncidentRepository incidents;
    private final IncidentAssignmentRepository assignments;
    private final IncidentDangerAdjustmentRepository adjustments;
    private final RescueOrganizationRepository orgs;
    private final MatchingService matching;
    private final SosService sos;
    private final IncidentMapper incidentMapper;
    private final NotificationPublisher notifications;
    private final CurrentUser current;
    private final RescueService rescue;

    /**
     * status = ACTIVE | ENDED (đã kết thúc, dùng cho bảng lịch sử có phân trang) | ALL/rỗng | một trạng thái (xem StatusFilter);
     * sortBy = level (khẩn cấp → không) | time (theo thời gian gửi); dir = asc | desc (với time).
     */
    @Transactional(readOnly = true)
    public PageResponse<IncidentDto> list(String status, DangerLevel level, String sortBy, String dir, int page, int size) {
        User admin = current.get();
        Set<IncidentStatus> wanted = StatusFilter.parse(status);
        Specification<Incident> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (wanted.size() < IncidentStatus.values().length) ps.add(root.get("status").in(wanted));
            if (level != null) ps.add(cb.equal(root.get("dangerLevel"), level));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        Sort sort = "level".equalsIgnoreCase(sortBy)
                ? Sort.by(Sort.Order.desc("dangerRank"), Sort.Order.asc("createdAt"))
                : Sort.by("desc".equalsIgnoreCase(dir) ? Sort.Direction.DESC : Sort.Direction.ASC, "createdAt");
        if (sortBy == null && wanted.equals(Set.of(IncidentStatus.PENDING))) sort = Sort.by(Sort.Direction.ASC, "createdAt"); // chờ lâu nhất trước
        return PageResponse.ofAll(incidents.findAll(spec, Paging.of(page, size, sort)), l -> incidentMapper.toDtos(l, admin));
    }

    @Transactional(readOnly = true)
    public IncidentDto detail(Long id) {
        Incident i = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        return incidentMapper.toDto(i, current.get(), null, null);
    }

    /** Ca đang Chờ xử lý, khóa dòng để Admin khác không thao tác cùng lúc. */
    private Incident pendingForUpdate(Long id, String action) {
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getStatus() == IncidentStatus.CANCELLED) throw ApiException.conflict("SOS_CANCELLED", "Ca SOS đã bị hủy, không thể cập nhật.");
        if (i.getStatus() == IncidentStatus.INVALID) throw ApiException.conflict("SOS_INVALID", "Ca SOS đã được đánh dấu không hợp lệ, không thể cập nhật.");
        if (i.getStatus() != IncidentStatus.PENDING)
            throw ApiException.conflict("SOS_NOT_PENDING", "Ca SOS không còn ở trạng thái Chờ xử lý nên không thể " + action + ".");
        return i;
    }

    public static final int VERIFICATION_NOTE_MAX = 1000;

    /**
     * Điều chỉnh nhãn mức độ nguy hiểm: chỉ khi ca đang Chờ xử lý; lưu lịch sử. Nhãn KHONG chỉ do AI gán (nghĩa là "chờ Admin xác minh"),
     * Admin không chọn KHONG: ca không phải yêu cầu cứu hộ thì dùng markInvalid để đóng ca.
     * Ca đang KHONG (Admin đã liên hệ người gửi và chọn mức thật): bắt buộc note = ghi chú xác minh, vì mô tả gốc thường quá ít
     * (lý do AI xếp KHONG) mà tổ chức chỉ dựa vào đó để quyết định. Ca có mức khác KHONG không nhận ghi chú.
     */
    @Transactional
    public IncidentDto adjustLevel(Long id, DangerLevel newLevel, String note) {
        User admin = current.get();
        Incident i = pendingForUpdate(id, "điều chỉnh nhãn");
        if (newLevel == DangerLevel.KHONG)
            throw ApiException.bad("USE_MARK_INVALID", "Không chọn được nhãn KHONG. Nếu ca không phải yêu cầu cứu hộ, hãy dùng chức năng Đánh dấu không hợp lệ.");
        DangerLevel old = i.getDangerLevel();
        if (old == newLevel) throw ApiException.bad("NO_CHANGE", "Nhãn mới trùng nhãn hiện tại, không có thay đổi.");
        String n = note == null ? "" : note.strip();
        if (old == DangerLevel.KHONG) {
            if (n.isEmpty())
                throw ApiException.bad("NOTE_REQUIRED", "Vui lòng ghi lại thông tin đã xác minh với người gửi để tổ chức nắm tình hình.");
            if (n.length() > VERIFICATION_NOTE_MAX)
                throw ApiException.bad("NOTE_TOO_LONG", "Ghi chú xác minh tối đa " + VERIFICATION_NOTE_MAX + " ký tự.");
        } else if (!n.isEmpty()) {
            throw ApiException.bad("NOTE_NOT_ALLOWED", "Ghi chú xác minh chỉ dùng khi xác minh ca được AI xếp nhãn “Không”.");
        }
        IncidentDangerAdjustment a = new IncidentDangerAdjustment();
        a.setIncident(i); a.setAdmin(admin); a.setOldLevel(old); a.setNewLevel(newLevel);
        adjustments.save(a);
        i.setDangerLevel(newLevel);
        // Chọn mức sau khi liên hệ cũng là đã xác minh. Ca KHONG luôn tính là xác minh (kể cả ca cũ thiếu cờ), để ghi chú có người ghi và thời điểm
        if (i.isNeedsVerification() || old == DangerLevel.KHONG) { i.setVerifiedBy(admin); i.setVerifiedAt(Instant.now()); }
        if (old == DangerLevel.KHONG) i.setVerificationNote(n);
        i.setNeedsVerification(false);
        i.setPendingTimeoutNotified(false); // tính lại hạn chờ tổ chức nhận
        if (old == DangerLevel.KHONG) sos.notifyNewSos(i); // vừa được xác minh hợp lệ: gửi cho tổ chức phù hợp
        return incidentMapper.toDto(i, admin, null, null);
    }

    /**
     * Admin đã liên hệ người gửi và thấy mức hiện tại phù hợp: gỡ cảnh báo "nên xác minh", giữ nguyên mức và trạng thái,
     * lưu Admin xác minh và thời điểm. Dùng được cả khi ca đã phân công/đã tiếp nhận (lúc đó không đổi mức được nữa).
     * Ca nhãn KHONG đang chờ Admin không dùng chức năng này: phải chọn mức để chuyển cho tổ chức hoặc đánh dấu Không hợp lệ.
     */
    @Transactional
    public IncidentDto confirmVerification(Long id) {
        User admin = current.get();
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (!SosService.ACTIVE.contains(i.getStatus()))
            throw ApiException.conflict("SOS_NOT_ACTIVE", "Ca SOS đã kết thúc (hoàn thành, hủy hoặc không hợp lệ), không cần xác minh nữa.");
        if (i.getDangerLevel() == DangerLevel.KHONG)
            throw ApiException.bad("USE_REVIEW_FLOW", "Ca đang chờ xác minh nhãn “Không”: hãy chọn mức độ để chuyển cho tổ chức, hoặc đánh dấu Không hợp lệ.");
        if (!i.isNeedsVerification())
            throw ApiException.conflict("NO_VERIFICATION_NEEDED", "Ca này không có cảnh báo nên xác minh (có thể Admin khác vừa xác minh).");
        i.setNeedsVerification(false);
        i.setVerifiedBy(admin); i.setVerifiedAt(Instant.now());
        return incidentMapper.toDto(i, admin, null, null);
    }

    /**
     * Admin đã liên hệ xác minh và kết luận ca không phải yêu cầu cứu hộ: đóng ca (INVALID), lưu lý do, báo người gửi.
     * Chỉ khi ca đang Chờ xử lý (mọi mức). Ca không còn "đang hoạt động" nên người gửi được gửi SOS mới ngay.
     */
    @Transactional
    public IncidentDto markInvalid(Long id, String reason) {
        User admin = current.get();
        Incident i = pendingForUpdate(id, "đánh dấu không hợp lệ");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do.");
        i.setStatus(IncidentStatus.INVALID);
        i.setInvalidReason(r); i.setInvalidatedBy(admin); i.setInvalidatedAt(Instant.now());
        i.setNeedsVerification(false);
        notifications.notify(i.getCitizen(), NotificationType.SOS_INVALID, "Ca SOS không hợp lệ",
                "Ca " + i.getCode() + " không được xác nhận là yêu cầu cứu hộ. Lý do: " + r
                        + ". Nếu bạn vẫn cần cứu hộ, hãy gửi yêu cầu mới hoặc gọi 112.", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, admin, null, null);
    }

    /**
     * Admin xác nhận hoàn thành ca Đã tiếp nhận sau khi liên hệ: người dân đã an toàn nhưng không tự báo được (hết pin, mất mạng),
     * hoặc tổ chức đã cứu xong mà quên bấm. Bắt buộc lý do; ghi người hoàn thành là Admin; báo tổ chức và người dân.
     * Không dùng cho trường hợp tổ chức không làm được: người dân chưa an toàn thì ca chưa xong.
     */
    @Transactional
    public IncidentDto complete(Long id, String reason) {
        User admin = current.get();
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getStatus() != IncidentStatus.ACCEPTED)
            throw ApiException.conflict("SOS_NOT_ACCEPTED", "Chỉ ca ở trạng thái Đã tiếp nhận mới xác nhận hoàn thành được (ca có thể vừa được hoàn thành).");
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do.");
        i.setStatus(IncidentStatus.COMPLETED); i.setCompletedAt(Instant.now());
        i.setCompletedByRole(CompletedBy.ADMIN); i.setCompletedBy(admin); i.setCompletionNote(r);
        if (i.getAssignedOrg() != null)
            notifications.notify(i.getAssignedOrg().getUser(), NotificationType.SOS_STATUS, "Ca SOS đã được Admin đóng",
                    "Admin xác nhận ca " + i.getCode() + " đã hoàn thành: " + r + ". Đội có thể dừng cứu hộ ca này.", "INCIDENT", i.getId());
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS hoàn thành",
                "Admin xác nhận ca " + i.getCode() + " đã hoàn thành: " + r + ".", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, admin, null, null);
    }

    /**
     * Ca lân cận trong bán kính 500 m (cùng bán kính cảnh báo trùng khi gửi), tính tại thời điểm gọi nên có cả ca gửi SAU ca đang xem.
     * Gồm ca đang hoạt động (mọi mức, kể cả ca chờ xác minh) và ca đã hoàn thành gửi từ 24 giờ trước ca này trở về sau.
     * Dùng khi Admin xác minh: ca hợp lệ ngay cạnh là bằng chứng sự cố có thật, hoặc cho thấy hai ca có thể là cùng một sự cố.
     * Không tính ca đã hủy và ca không hợp lệ. Xếp gần nhất trước.
     */
    @Transactional(readOnly = true)
    public List<NearbyCaseDto> nearby(Long id) {
        User admin = current.get();
        Incident i = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        Map<Long, Incident> found = new LinkedHashMap<>();
        incidents.findWithinRadius(SosService.ACTIVE, Instant.EPOCH, i.getLatitude(), i.getLongitude(), SosService.DUP_RADIUS_M)
                .forEach(x -> found.put(x.getId(), x));
        Instant doneSince = i.getCreatedAt() == null ? Instant.EPOCH : i.getCreatedAt().minus(NEARBY_DONE_WINDOW);
        incidents.findWithinRadius(EnumSet.of(IncidentStatus.COMPLETED), doneSince, i.getLatitude(), i.getLongitude(), SosService.DUP_RADIUS_M)
                .forEach(x -> found.put(x.getId(), x));
        found.remove(i.getId());
        Map<Long, Double> dist = new HashMap<>();
        found.values().forEach(x -> dist.put(x.getId(), Geo.haversine(i.getLatitude(), i.getLongitude(), x.getLatitude(), x.getLongitude())));
        List<Incident> sorted = found.values().stream().sorted(Comparator.comparingDouble((Incident x) -> dist.get(x.getId()))).toList();
        List<IncidentDto> dtos = incidentMapper.toDtos(sorted, admin); // cùng thứ tự với sorted
        List<NearbyCaseDto> out = new ArrayList<>();
        for (int k = 0; k < sorted.size(); k++) out.add(new NearbyCaseDto(dtos.get(k), dist.get(sorted.get(k).getId())));
        return out;
    }

    /** Danh sách tổ chức đề xuất (AI Matching) cho một ca, xếp điểm giảm dần. */
    @Transactional
    public List<CandidateOrgDto> candidates(Long id) {
        Incident i = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getStatus() != IncidentStatus.PENDING && i.getStatus() != IncidentStatus.ASSIGNED)
            throw ApiException.conflict("SOS_NOT_DISPATCHABLE", "Ca SOS không còn cần điều phối.");
        // Tổ chức đã từ chối / không phản hồi ca này: một câu cho cả danh sách (trước đây một câu mỗi tổ chức)
        Set<Long> refused = new HashSet<>(assignments.findOrgIdsByIncidentIdAndStatusIn(id, EnumSet.of(AssignmentStatus.REJECTED, AssignmentStatus.TIMEOUT)));
        List<RescueOrganization> list = orgs.findByStatus(OrgStatus.ACTIVE).stream()
                .filter(o -> !o.getUser().isLocked())
                .filter(o -> i.getAssignedOrg() == null || !i.getAssignedOrg().getId().equals(o.getId()))
                .filter(o -> !refused.contains(o.getId()))
                .toList();
        Map<Long, MatchingService.OrgLoad> loads = matching.loads(list); // số liệu tải của các tổ chức đọc theo lô
        Map<Long, MatchingDto> scored = matching.computeAll(i, list, true, loads);
        List<CandidateOrgDto> out = new ArrayList<>();
        for (RescueOrganization o : list) {
            MatchingService.OrgLoad load = loads.get(o.getId());
            List<NearbyActiveCase> nearby = MatchingService.nearbyActive(i, load).stream()
                    .map(x -> new NearbyActiveCase(x.getId(), x.getCode(),
                            Geo.haversine(x.getLatitude(), x.getLongitude(), i.getLatitude(), i.getLongitude()))).toList();
            out.add(new CandidateOrgDto(new OrgBrief(o.getId(), o.getName(), o.getOrgType(), o.getOperatingStatus(),
                    o.getLatitude(), o.getLongitude(), o.getUser().getPhone()), scored.get(o.getId()),
                    o.getOperatingStatus() == OperatingStatus.AVAILABLE, nearby, matching.busyIfAssigned(o, load),
                    o.getMaxConcurrentCases(), o.getServiceRadiusKm()));
        }
        out.sort(Comparator.comparingInt((CandidateOrgDto x) -> x.matching().score()).reversed());
        return out;
    }

    /** Chỉ định tổ chức (hoặc điều phối lại khi tổ chức từ chối / không phản hồi sau 5 phút). */
    @Transactional
    public IncidentDto assign(Long id, Long orgId) {
        User admin = current.get();
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getDangerLevel() == DangerLevel.KHONG)
            throw ApiException.conflict("SOS_NOT_VERIFIED", "Ca chưa được xác minh hợp lệ. Hãy điều chỉnh mức nguy hiểm trước khi chỉ định.");
        RescueOrganization previous = i.getAssignedOrg();
        Instant now = Instant.now();
        if (i.getStatus() == IncidentStatus.ASSIGNED) {
            if (i.getAssignedAt() != null && Duration.between(i.getAssignedAt(), now).toMinutes() < SosService.ASSIGN_TIMEOUT_MIN)
                throw ApiException.conflict("ORG_STILL_WAITING", "Tổ chức vẫn đang trong thời gian chờ phản hồi (5 phút), chưa thể điều phối lại.");
            assignments.findFirstByIncidentIdOrderByCreatedAtDesc(id).ifPresent(a -> {
                if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.TIMEOUT); a.setRespondedAt(now); }
            });
        } else if (i.getStatus() != IncidentStatus.PENDING) {
            throw ApiException.conflict("SOS_UNAVAILABLE", "Ca SOS đã được tổ chức khác tiếp nhận hoặc đã kết thúc.");
        }
        RescueOrganization o = orgs.findById(orgId).orElseThrow(() -> ApiException.notFound("Tổ chức không tồn tại."));
        if (o.getStatus() != OrgStatus.ACTIVE || o.getUser().isLocked() || o.getOperatingStatus() != OperatingStatus.AVAILABLE)
            throw ApiException.conflict("ORG_NOT_AVAILABLE", "Tổ chức được chọn không còn sẵn sàng, vui lòng chọn tổ chức khác.");

        matching.compute(i, o, true);
        i.setStatus(IncidentStatus.ASSIGNED);
        i.setAssignedOrg(o); i.setAssignedAt(now); i.setAcceptedAt(null);
        i.setAssignTimeoutNotified(false);
        IncidentAssignment a = new IncidentAssignment();
        a.setIncident(i); a.setOrg(o); a.setType(AssignmentType.ADMIN_ASSIGN); a.setStatus(AssignmentStatus.WAITING_CONFIRM); a.setAssignedBy(admin);
        assignments.save(a);
        notifications.notify(o.getUser(), NotificationType.SOS_NEW, "Ca SOS được phân công",
                "Admin phân công ca " + i.getCode() + " cho tổ chức. Vui lòng xác nhận trong " + SosService.ASSIGN_TIMEOUT_MIN + " phút.", "INCIDENT", i.getId());
        notifications.notify(i.getCitizen(), NotificationType.SOS_STATUS, "Ca SOS đã được phân công",
                "Ca " + i.getCode() + " đã được phân công cho " + o.getName() + ".", "INCIDENT", i.getId());
        if (previous != null && !previous.getId().equals(o.getId()))
            notifications.notify(previous.getUser(), NotificationType.SOS_STATUS, "Ca SOS được điều phối lại",
                    "Ca " + i.getCode() + " đã được điều phối sang tổ chức khác.", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, admin, null, null);
    }

    public static final int UNASSIGN_REASON_MAX = 500;

    /**
     * Admin hủy phân công ca Đã phân công (tổ chức chưa xác nhận), không cần chờ hết 5 phút: ví dụ Admin gọi điện và biết tổ chức
     * không đi được. Ca về Chờ xử lý; báo tổ chức bị hủy (kèm lý do), người dân và các tổ chức Sẵn sàng gần đó, rồi Admin chỉ định
     * tổ chức khác như bình thường (hoặc tổ chức gần đó tự nhận).
     * Bản ghi phân công đang chờ xác nhận chuyển CANCELLED: đây là quyết định của Admin, không tính là tổ chức từ chối nên không trừ
     * điểm W của AI Matching. Đã quá 5 phút (TIMEOUT) thì giữ TIMEOUT vì tổ chức thật sự không phản hồi.
     * Khóa dòng rồi mới kiểm tra: tổ chức có thể vừa bấm xác nhận đúng lúc đó.
     */
    @Transactional
    public IncidentDto unassign(Long id, String reason) {
        User admin = current.get();
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do hủy phân công.");
        if (r.length() > UNASSIGN_REASON_MAX) throw ApiException.bad("REASON_TOO_LONG", "Lý do tối đa " + UNASSIGN_REASON_MAX + " ký tự.");
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (i.getStatus() == IncidentStatus.ACCEPTED)
            throw ApiException.conflict("SOS_ALREADY_ACCEPTED", "Tổ chức vừa xác nhận tiếp nhận ca, không hủy phân công được nữa.");
        if (i.getStatus() != IncidentStatus.ASSIGNED || i.getAssignedOrg() == null)
            throw ApiException.conflict("SOS_NOT_ASSIGNED", "Ca SOS không còn ở trạng thái Đã phân công.");
        RescueOrganization o = i.getAssignedOrg();
        Instant now = Instant.now();
        assignments.findFirstByIncidentIdOrderByCreatedAtDesc(id).ifPresent(a -> {
            if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.CANCELLED); a.setRespondedAt(now); }
            if (a.getRejectReason() == null) a.setRejectReason("Admin hủy phân công: " + r);
        });
        notifications.notify(o.getUser(), NotificationType.SOS_STATUS, "Phân công bị hủy",
                "Admin đã hủy phân công ca " + i.getCode() + " cho tổ chức. Lý do: " + r + ". Tổ chức không cần xử lý ca này nữa.",
                "INCIDENT", i.getId());
        rescue.returnToPending(i, o);
        return incidentMapper.toDto(i, admin, null, null);
    }

    /** Dữ liệu bản đồ nhiệt: các ca đang hoạt động (không tính ca KHONG), trọng số theo mức nguy hiểm. */
    @Transactional(readOnly = true)
    public List<HeatPoint> heatmap() {
        return incidents.findByStatusIn(SosService.ACTIVE).stream()
                .filter(x -> x.getDangerLevel() != DangerLevel.KHONG)
                .map(x -> new HeatPoint(x.getLatitude(), x.getLongitude(), x.getDangerLevel().weight / 4.0)).toList();
    }
}
