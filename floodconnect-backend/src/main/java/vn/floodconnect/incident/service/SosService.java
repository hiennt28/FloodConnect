package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.ai.dto.AiDtos.NeighborCase;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.ai.service.SosAssessor;
import vn.floodconnect.ai.service.WeatherService;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.incident.dto.IncidentDtos.CreateSosResponse;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.dto.IncidentDtos.SosCreateRequest;
import vn.floodconnect.incident.dto.IncidentDtos.SosUpdateRequest;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.incident.repository.IncidentDangerAdjustmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/** Nghiệp vụ ca SOS của người dân: gửi, sửa, hủy, theo dõi; xem danh sách công khai. Phần của tổ chức cứu hộ ở RescueService. */
@Service
@RequiredArgsConstructor
public class SosService {
    public static final double DUP_RADIUS_M = 500;
    public static final int DUP_WINDOW_MIN = 30;
    public static final int PENDING_TIMEOUT_MIN = 15;
    public static final int ASSIGN_TIMEOUT_MIN = 5;
    public static final EnumSet<IncidentStatus> ACTIVE = EnumSet.of(IncidentStatus.PENDING, IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED);
    /** Chỉ mục duy nhất (Flyway, db/migration/V1__baseline_schema.sql): mỗi người dân chỉ có một ca đang hoạt động, kể cả khi gửi hai yêu cầu cùng lúc. */
    private static final String ONE_ACTIVE_INDEX = "uq_incidents_one_active_per_citizen";

    private final IncidentRepository incidents;
    private final IncidentAiAssessmentRepository assessments;
    private final IncidentDangerAdjustmentRepository adjustments;
    private final RescueOrganizationRepository orgs;
    private final SosAssessor assessor;
    private final NotificationPublisher notifications;
    private final CurrentUser current;
    private final ObjectMapper json;
    private final IncidentMapper incidentMapper;
    private final IncidentAssignmentRepository assignments;
    private final WeatherService weather;
    private final PlatformTransactionManager txManager;

    /** Ca lân cận gửi kèm cho dịch vụ AI để xét cụm (khớp NEARBY_RADIUS_METERS / NEARBY_TIME_WINDOW_MINUTES bên AI). */
    @Value("${app.ai.cluster-radius-m:1000}") private double clusterRadiusM = 1000;
    @Value("${app.ai.cluster-window-minutes:360}") private long clusterWindowMin = 360;
    private static final int MAX_NEIGHBORS = 200;

    // =====================================================================
    //  Người dân
    // =====================================================================
    private List<Incident> nearbyActive(double lat, double lng, double radius, Instant since, Long excludeId) {
        return incidents.findWithinRadius(ACTIVE, since, lat, lng, radius).stream()
                .filter(x -> !x.getId().equals(excludeId == null ? -1L : excludeId)).toList();
    }

    private static ApiException alreadyActive() {
        return ApiException.conflict("SOS_ALREADY_ACTIVE",
                "Bạn đang có một yêu cầu cứu hộ chưa hoàn tất. Vui lòng hủy hoặc chờ yêu cầu đó hoàn thành/hủy trước khi gửi yêu cầu mới.");
    }

    /**
     * Gửi ca SOS (chỉ chữ và vị trí, không kèm ảnh). Không bọc cả hàm trong một transaction: thời tiết (gọi Open-Meteo qua mạng, chờ tối đa vài giây) được lấy
     * TRƯỚC, khi chưa giữ kết nối DB; nếu gọi trong transaction, Open-Meteo chậm lúc nhiều người cùng gửi SOS sẽ chiếm hết
     * kết nối DB và làm chậm cả hệ thống. Phần đọc/ghi DB vẫn nằm trọn trong một transaction như trước.
     */
    public CreateSosResponse create(SosCreateRequest r) {
        // Chỉ người dân gửi yêu cầu cứu hộ. Tổ chức cứu hộ và Admin là bên tiếp nhận / điều phối ca, không tự gửi SOS.
        // Kiểm tra trước khi gọi thời tiết qua mạng.
        if (current.get().getRole() != Role.CITIZEN)
            throw ApiException.forbidden("SOS_CITIZEN_ONLY", "Chỉ tài khoản người dân mới gửi được yêu cầu cứu hộ SOS. "
                    + "Trường hợp khẩn cấp, hãy gọi 112.");
        String desc = r.description() == null ? "" : r.description().trim();
        if (desc.isEmpty()) throw ApiException.bad("INVALID_DESCRIPTION", "Vui lòng nhập mô tả sự cố.");
        if (desc.length() > 2000) throw ApiException.bad("INVALID_DESCRIPTION", "Mô tả tối đa 2.000 ký tự.");
        if (Validators.tooLong(r.address(), Validators.TEXT_MAX))
            throw ApiException.bad("INVALID_ADDRESS", Validators.tooLongMsg("Địa chỉ", Validators.TEXT_MAX));
        Validators.coords(r.latitude(), r.longitude());
        WeatherInfo w = weather.at(r.latitude(), r.longitude());
        return new TransactionTemplate(txManager).execute(status -> createInTx(r, desc, w));
    }

    private CreateSosResponse createInTx(SosCreateRequest r, String desc, WeatherInfo w) {
        User u = current.get(); // tài khoản bị khóa vẫn được gửi SOS: khóa chỉ hạn chế mạng xã hội, không chặn cứu nạn
        if (incidents.existsByCitizenIdAndStatusIn(u.getId(), ACTIVE)) throw alreadyActive();

        // Cảnh báo ca tương tự (500 m, 30 phút gần nhất); không tự gộp ca, người dân quyết định.
        // Ca KHONG đang chờ Admin xác minh thì không đưa ra (bị ẩn với người dân khác).
        if (!Boolean.TRUE.equals(r.forceCreate())) {
            var similar = nearbyActive(r.latitude(), r.longitude(), DUP_RADIUS_M, Instant.now().minus(Duration.ofMinutes(DUP_WINDOW_MIN)), null)
                    .stream().filter(x -> x.getDangerLevel() != DangerLevel.KHONG).toList();
            if (!similar.isEmpty())
                return new CreateSosResponse(true, incidentMapper.toDtos(similar, u), null);
        }

        Incident i = new Incident();
        i.setCode("TMP-" + UUID.randomUUID());
        i.setCitizen(u); i.setDescription(desc); i.setAddress(r.address() == null || r.address().isBlank() ? null : r.address().trim());
        i.setLatitude(r.latitude()); i.setLongitude(r.longitude()); i.setStatus(IncidentStatus.PENDING);
        try {
            i = incidents.saveAndFlush(i);
        } catch (DataIntegrityViolationException e) { // hai yêu cầu gửi gần như cùng lúc đều qua bước kiểm tra ở trên
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains(ONE_ACTIVE_INDEX)) throw alreadyActive();
            throw e;
        }
        i.setCode("SOS-" + (1000 + i.getId()));
        runAssessment(i, w);
        notifyNewSos(i);
        return new CreateSosResponse(false, List.of(), incidentMapper.toDto(i, u));
    }

    /**
     * Chạy AI đánh giá và gắn kết quả vào ca: mức, cờ "nên xác minh" và lý do lấy đúng theo kết quả AI.
     * Dùng chung cho gửi ca mới và người dân sửa mô tả (sửa mô tả được chấm lại y như ca mới, mức có thể lên hoặc xuống).
     * @param w thời tiết đã lấy sẵn trước transaction (null = không có dữ liệu)
     */
    private void runAssessment(Incident i, WeatherInfo w) {
        int near = nearbyActive(i.getLatitude(), i.getLongitude(), DUP_RADIUS_M, Instant.EPOCH, i.getId()).size();
        SosAssessor.Context ctx = new SosAssessor.Context(i.getId(), i.getCitizen() == null ? null : i.getCitizen().getId(),
                i.getLatitude(), i.getLongitude(), () -> neighborCases(i));
        SosAssessor.Assessment a = assessor.assess(i.getDescription(), near, w, ctx);
        DangerLevel level = a.finalLevel();
        boolean verify = a.needsVerification();
        List<String> reasons = a.reasons();
        i.setAiDangerLevel(a.aiLevel());
        i.setDangerLevel(level);
        i.setNeedsVerification(verify);
        if (verify) { i.setVerifiedBy(null); i.setVerifiedAt(null); } // nội dung mới lại cần xác minh: lần xác minh trước không còn giá trị
        IncidentAiAssessment s = new IncidentAiAssessment();
        s.setIncident(i); s.setPredictedLevel(level); s.setConfidence(a.confidence());
        s.setContentLevel(a.contentLevel()); s.setSource(a.source() == null ? null : a.source().name());
        s.setNeedsVerification(verify); s.setNearbySosCount(a.nearCount());
        s.setReasons(String.join("\n", reasons));
        if (a.weather() != null) try { s.setWeatherData(json.writeValueAsString(a.weather())); } catch (Exception ignored) { }
        assessments.save(s);
    }

    /**
     * Ca đang hoạt động quanh ca i (bán kính + cửa sổ thời gian của AI), kèm mức theo nội dung của lần đánh giá gần nhất và cờ
     * Admin đã điều chỉnh mức. Chỉ chạy khi thực sự gọi dịch vụ AI. Ba truy vấn cho cả danh sách.
     */
    private List<NeighborCase> neighborCases(Incident i) {
        List<Incident> list = nearbyActive(i.getLatitude(), i.getLongitude(), clusterRadiusM,
                Instant.now().minus(Duration.ofMinutes(clusterWindowMin)), i.getId()).stream().limit(MAX_NEIGHBORS).toList();
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Incident::getId).toList();
        Map<Long, DangerLevel> content = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (IncidentAiAssessment x : assessments.findByIncidentIdsLatestFirst(ids)) { // bản đầu tiên của mỗi ca là bản mới nhất
            Long id = x.getIncident().getId();
            if (seen.add(id) && x.getContentLevel() != null) content.put(id, x.getContentLevel()); // trước V9: không có, AI dùng mức hiện có
        }
        Set<Long> adjusted = new HashSet<>(adjustments.findAdjustedIncidentIds(ids));
        List<NeighborCase> out = new ArrayList<>();
        for (Incident x : list)
            out.add(new NeighborCase(x.getId(), x.getDescription(), x.getCitizen() == null ? null : x.getCitizen().getId(),
                    x.getDangerLevel(), content.get(x.getId()), adjusted.contains(x.getId())));
        return out;
    }

    /** Gửi thông báo ca mới: KHONG chỉ gửi Admin xác minh; các mức còn lại gửi Admin và tổ chức phù hợp trong khu vực. */
    @Transactional
    public void notifyNewSos(Incident i) {
        if (i.getDangerLevel() == DangerLevel.KHONG) {
            notifications.notifyAdmins(NotificationType.SOS_NEW, "Ca SOS cần xác minh",
                    "Ca " + i.getCode() + " được AI đánh giá không thuộc phạm vi cứu hộ lũ lụt. Cần Admin xem xét.", "INCIDENT", i.getId());
            return;
        }
        String label = "Ca " + i.getCode() + " mức " + i.getDangerLevel().label + (i.isNeedsVerification() ? " (nên xác minh)" : "");
        notifications.notifyAdmins(NotificationType.SOS_NEW, "Ca SOS mới", label, "INCIDENT", i.getId());
        notifyNearbyOrgs(i, "Ca SOS mới trong khu vực", label, null);
    }

    /**
     * Báo các tổ chức Sẵn sàng mà ca nằm trong bán kính hoạt động họ tự khai (gần nhất trước, tối đa 20, bỏ tổ chức bị khóa);
     * nội dung = label + khoảng cách. Ca không thuộc vùng của tổ chức nào thì không tổ chức nào được báo: Admin đã nhận thông báo
     * ca mới và được nhắc sau 15 phút nếu chưa ai nhận, rồi chỉ định trực tiếp (danh sách ứng viên của Admin có mọi tổ chức).
     * @param returnedFrom tổ chức vừa trả ca (từ chối, bị khóa, chuyển Không hoạt động); null khi là ca mới. Khi khác null,
     *                     bỏ tổ chức đó và các tổ chức đã từ chối/không phản hồi ca này (danh sách đề xuất của họ cũng không hiện ca)
     */
    public void notifyNearbyOrgs(Incident i, String title, String label, Long returnedFrom) {
        // Ca bị trả lại: các tổ chức đã từ chối / không phản hồi ca này, một câu cho cả danh sách (ca mới thì chưa có ai)
        Set<Long> refused = returnedFrom == null ? Set.of()
                : new HashSet<>(assignments.findOrgIdsByIncidentIdAndStatusIn(i.getId(), EnumSet.of(AssignmentStatus.REJECTED, AssignmentStatus.TIMEOUT)));
        orgs.findAvailableCovering(i.getLatitude(), i.getLongitude()).stream() // đã xếp gần nhất trước
                .filter(o -> !o.getUser().isLocked())
                .filter(o -> returnedFrom == null || (!o.getId().equals(returnedFrom) && !refused.contains(o.getId())))
                .limit(20)
                .forEach(o -> notifications.notify(o.getUser(), NotificationType.SOS_NEW, title,
                        label + ", cách bạn " + String.format("%.1f", Geo.haversine(o.getLatitude(), o.getLongitude(), i.getLatitude(), i.getLongitude()) / 1000)
                                + " km.", "INCIDENT", i.getId()));
    }

    @Transactional(readOnly = true)
    public List<IncidentDto> mine(IncidentStatus status) {
        User u = current.get();
        return incidentMapper.toDtos(incidents.findByCitizenIdOrderByCreatedAtDesc(u.getId()).stream()
                .filter(x -> status == null || x.getStatus() == status).toList(), u);
    }

    /** Tất cả ca SOS (công khai, trừ ca KHONG, ca đã hủy và ca không hợp lệ). Thông tin người gửi được ẩn với người không có quyền. */
    @Transactional(readOnly = true)
    public PageResponse<IncidentDto> all(IncidentStatus status, DangerLevel level, int page, int size) {
        User u = current.get();
        Specification<Incident> spec = (root, q, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            ps.add(cb.notEqual(root.get("dangerLevel"), DangerLevel.KHONG));
            ps.add(cb.not(root.get("status").in(IncidentStatus.CANCELLED, IncidentStatus.INVALID)));
            if (status != null) ps.add(cb.equal(root.get("status"), status));
            if (level != null) ps.add(cb.equal(root.get("dangerLevel"), level));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResponse.ofAll(incidents.findAll(spec, Paging.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))), l -> incidentMapper.toDtos(l, u));
    }

    @Transactional(readOnly = true)
    public IncidentDto detail(Long id) {
        User u = current.get();
        Incident i = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        boolean admin = u.getRole() == Role.ADMIN, owner = i.getCitizen().getId().equals(u.getId());
        boolean assigned = i.getAssignedOrg() != null && i.getAssignedOrg().getUser().getId().equals(u.getId());
        if (!admin && !owner && !assigned && (i.getDangerLevel() == DangerLevel.KHONG
                || i.getStatus() == IncidentStatus.CANCELLED || i.getStatus() == IncidentStatus.INVALID))
            throw ApiException.notFound("Ca SOS không tồn tại.");
        return incidentMapper.toDto(i, u);
    }

    /**
     * Sửa ca SOS. Giống create: thời tiết lấy trước khi mở transaction, vì trong transaction ca bị khóa dòng; chờ Open-Meteo
     * lúc đang khóa sẽ bắt tổ chức bấm nhận đúng ca đó phải chờ theo. Tọa độ ca không đổi khi sửa nên đọc trước được.
     */
    public IncidentDto update(Long id, SosUpdateRequest r) {
        String d = r.description() == null ? "" : r.description().trim();
        if (d.isEmpty() || d.length() > 2000) throw ApiException.bad("INVALID_DESCRIPTION", "Mô tả phải từ 1 đến 2.000 ký tự.");
        Incident cur = incidents.findById(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        WeatherInfo w = weather.at(cur.getLatitude(), cur.getLongitude()); // mô tả đổi thì phải chấm lại
        return new TransactionTemplate(txManager).execute(status -> updateInTx(id, d, w));
    }

    private IncidentDto updateInTx(Long id, String d, WeatherInfo w) {
        User u = current.get(); // tài khoản bị khóa vẫn được sửa ca SOS của mình
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (!i.getCitizen().getId().equals(u.getId())) throw ApiException.forbidden("Bạn chỉ sửa được ca SOS của mình.");
        /* Chỉ sửa được khi ca còn Chờ xử lý. Từ lúc Admin giao ca cho tổ chức (Đã phân công), nội dung ca được giữ nguyên để
           tổ chức quyết định nhận dựa đúng trên mô tả đã dùng khi tính điểm phù hợp (matching); người dân chỉ còn Hủy được.
           Đã tiếp nhận thì chỉ báo "Tôi đã an toàn". */
        if (i.getStatus() != IncidentStatus.PENDING)
            throw ApiException.conflict("SOS_NOT_EDITABLE", i.getStatus() == IncidentStatus.ASSIGNED
                    ? "Ca đã được giao cho tổ chức nên không sửa được nội dung. Cần báo thêm tình hình khẩn, hãy gọi 112."
                    : "Yêu cầu đã chuyển sang trạng thái không thể chỉnh sửa.");

        if (d.equals(i.getDescription())) return incidentMapper.toDto(i, u); // không đổi gì: không chấm lại, không báo ai
        i.setDescription(d);
        /* Chỉ đánh giá lại khi Admin chưa điều chỉnh nhãn thủ công (mức Admin là chuẩn). Chấm lại y như gửi ca mới: mức theo
           đúng kết quả AI, có thể lên hoặc xuống. Chỉ báo khi ca đổi nhóm, như lúc gửi mới: thành KHONG thì báo Admin xác minh
           (ca rời danh sách của tổ chức), từ KHONG thành mức khác thì báo Admin và tổ chức trong khu vực. Đổi giữa Thấp..Khẩn cấp:
           không báo lại, nhãn trong danh sách tự cập nhật. */
        if (!adjustments.existsByIncidentId(id)) {
            boolean wasKhong = i.getDangerLevel() == DangerLevel.KHONG;
            runAssessment(i, w);
            if (wasKhong != (i.getDangerLevel() == DangerLevel.KHONG)) notifyNewSos(i);
        }
        return incidentMapper.toDto(i, u);
    }

    @Transactional
    public IncidentDto cancel(Long id) {
        User u = current.get();
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (!i.getCitizen().getId().equals(u.getId())) throw ApiException.forbidden("Bạn chỉ hủy được ca SOS của mình.");
        if (i.getStatus() == IncidentStatus.ACCEPTED)
            throw ApiException.conflict("SOS_ALREADY_ACCEPTED",
                    "Ca đã được tổ chức tiếp nhận, không thể hủy. Nếu bạn đã an toàn, hãy bấm \"Tôi đã an toàn\" để tổ chức dừng cứu hộ.");
        if (i.getStatus() != IncidentStatus.PENDING && i.getStatus() != IncidentStatus.ASSIGNED)
            throw ApiException.conflict("SOS_NOT_CANCELLABLE", "Ca SOS không còn ở trạng thái có thể hủy.");
        RescueOrganization org = i.getAssignedOrg();
        if (org != null) // ca đang "Đã phân công" (chưa xác nhận): đóng bản ghi phân công đang chờ, tránh để trạng thái treo
            assignments.findFirstByIncidentIdOrderByCreatedAtDesc(i.getId()).ifPresent(a -> {
                if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.CANCELLED); a.setRejectReason("Người dân đã hủy ca"); a.setRespondedAt(Instant.now()); }
            });
        i.setStatus(IncidentStatus.CANCELLED);
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Ca SOS bị hủy", "Người dân đã hủy ca " + i.getCode() + ".", "INCIDENT", i.getId());
        if (org != null)
            notifications.notify(org.getUser(), NotificationType.SOS_STATUS, "Ca SOS bị hủy", "Người dân đã hủy ca " + i.getCode() + ".", "INCIDENT", i.getId());
        return incidentMapper.toDto(i, u);
    }

    /**
     * Người dân báo "Tôi đã an toàn" khi ca đang Đã tiếp nhận (được đơn vị khác cứu, nước rút...): ca chuyển Hoàn thành,
     * ghi người hoàn thành là người dân, báo tổ chức dừng cứu hộ và báo Admin. Ca chưa có tổ chức tiếp nhận thì dùng Hủy.
     * Khóa dòng rồi mới kiểm tra: tổ chức có thể vừa bấm Hoàn thành cùng lúc.
     */
    @Transactional
    public IncidentDto markSafe(Long id, String note) {
        User u = current.get(); // tài khoản bị khóa vẫn được báo an toàn cho ca của mình
        Incident i = incidents.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Ca SOS không tồn tại."));
        if (!i.getCitizen().getId().equals(u.getId())) throw ApiException.forbidden("Bạn chỉ cập nhật được ca SOS của mình.");
        if (i.getStatus() == IncidentStatus.PENDING || i.getStatus() == IncidentStatus.ASSIGNED)
            throw ApiException.conflict("SOS_USE_CANCEL", "Ca chưa được tổ chức tiếp nhận. Nếu không còn cần cứu hộ, hãy dùng chức năng Hủy.");
        if (i.getStatus() != IncidentStatus.ACCEPTED) throw ApiException.conflict("SOS_FINISHED", "Ca SOS đã kết thúc.");
        String n = note == null || note.isBlank() ? null : note.trim();
        if (n != null && n.length() > 500) throw ApiException.bad("NOTE_TOO_LONG", "Ghi chú tối đa 500 ký tự.");
        i.setStatus(IncidentStatus.COMPLETED); i.setCompletedAt(Instant.now());
        i.setCompletedByRole(CompletedBy.CITIZEN); i.setCompletedBy(u); i.setCompletionNote(n);
        String body = "Người dân báo đã an toàn, không cần cứu hộ ca " + i.getCode() + " nữa" + (n == null ? "." : ": " + n + ".");
        if (i.getAssignedOrg() != null)
            notifications.notify(i.getAssignedOrg().getUser(), NotificationType.SOS_STATUS, "Người dân đã an toàn",
                    body + " Đội có thể dừng cứu hộ.", "INCIDENT", i.getId());
        notifications.notifyAdmins(NotificationType.SOS_STATUS, "Người dân đã an toàn", body, "INCIDENT", i.getId());
        return incidentMapper.toDto(i, u);
    }
}
