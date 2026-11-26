package vn.floodconnect.rescue.scheduler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.rescue.service.RescueService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

/**
 * Tác vụ định kỳ của ca SOS: quá hạn chờ tiếp nhận, quá hạn phản hồi phân công, tự trả ca Đã phân công không ai xử lý, cứu hộ kéo dài.
 *
 * Mỗi lần chạy chỉ lấy danh sách id, rồi xử lý TỪNG CA trong một transaction riêng: khóa dòng (findByIdForUpdate), kiểm tra
 * lại điều kiện trên dữ liệu vừa khóa, sau đó mới cập nhật. Nhờ vậy:
 * - không ghi đè thao tác của tổ chức/Admin xảy ra giữa lúc lấy danh sách và lúc cập nhật (ví dụ tổ chức vừa nhận ca thì
 *   ca không còn PENDING, tác vụ bỏ qua ca đó thay vì ghi trạng thái cũ đè lên);
 * - một ca lỗi chỉ hủy phần của ca đó, các ca khác vẫn được nhắc; khóa chỉ giữ trong thời gian xử lý một ca.
 */
@Service
@Slf4j
public class RescueScheduler {
    private final IncidentRepository incidents;
    private final IncidentAssignmentRepository assignments;
    private final NotificationPublisher notifications;
    private final RescueService rescue;
    private final TransactionTemplate tx;
    @Value("${app.rescue.accepted-reminder-hours:2}") private long acceptedReminderHours;
    /** Sau mốc quá hạn phản hồi (5 phút), thêm ngần này phút Admin chưa điều phối lại thì ca tự về Chờ xử lý. */
    @Value("${app.rescue.assign-release-minutes:10}") private long assignReleaseMinutes = 10;

    public RescueScheduler(IncidentRepository incidents, IncidentAssignmentRepository assignments,
                           NotificationPublisher notifications, RescueService rescue, PlatformTransactionManager txManager) {
        this.incidents = incidents;
        this.assignments = assignments;
        this.notifications = notifications;
        this.rescue = rescue;
        this.tx = new TransactionTemplate(txManager);
    }

    /** Xử lý từng ca trong transaction riêng, khóa dòng trước khi giao cho handler; lỗi ở một ca không ảnh hưởng ca khác. */
    private void eachLocked(String task, List<Long> ids, Consumer<Incident> handler) {
        for (Long id : ids) {
            try {
                tx.executeWithoutResult(status -> incidents.findByIdForUpdate(id).ifPresent(handler));
            } catch (RuntimeException e) {
                log.warn("Tác vụ {}: bỏ qua ca id={} do lỗi, sẽ thử lại ở lần chạy sau", task, id, e);
            }
        }
    }

    /**
     * Ca Chờ xử lý quá 15 phút: chưa có tổ chức nhận thì báo Admin điều phối; ca KHONG (AI cho là ngoài phạm vi lũ lụt)
     * chưa được xác minh thì nhắc Admin xác minh, vì AI có thể chấm nhầm một ca thật.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void pendingTimeouts() {
        Instant before = Instant.now().minus(Duration.ofMinutes(SosService.PENDING_TIMEOUT_MIN));
        eachLocked("pendingTimeouts", incidents.findPendingTimedOutIds(IncidentStatus.PENDING, before), i -> {
            // Có thể vừa được tổ chức nhận / Admin phân công / người dân hủy, hoặc tác vụ lần trước đã nhắc
            if (i.getStatus() != IncidentStatus.PENDING || i.isPendingTimeoutNotified() || i.getCreatedAt().isAfter(before)) return;
            i.setPendingTimeoutNotified(true);
            if (i.getDangerLevel() == DangerLevel.KHONG)
                notifications.notifyAdmins(NotificationType.SOS_TIMEOUT, "Ca SOS chưa được xác minh",
                        "Ca " + i.getCode() + " được AI đánh giá không thuộc phạm vi lũ lụt và đã chờ quá " + SosService.PENDING_TIMEOUT_MIN
                                + " phút chưa được xác minh. Cần Admin xem xét.", "INCIDENT", i.getId());
            else
                notifications.notifyAdmins(NotificationType.SOS_TIMEOUT, "Ca SOS chưa có tổ chức tiếp nhận",
                        "Ca " + i.getCode() + " đã chờ quá " + SosService.PENDING_TIMEOUT_MIN + " phút. Cần Admin điều phối.", "INCIDENT", i.getId());
        });
    }

    /** Ca Đã phân công quá 5 phút tổ chức không phản hồi: ghi TIMEOUT (vào lịch sử cho AI Matching) và báo Admin điều phối lại. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 40_000)
    public void assignTimeouts() {
        Instant before = Instant.now().minus(Duration.ofMinutes(SosService.ASSIGN_TIMEOUT_MIN));
        eachLocked("assignTimeouts", incidents.findAssignedTimedOutIds(IncidentStatus.ASSIGNED, before), i -> {
            // Có thể tổ chức vừa xác nhận/từ chối, hoặc Admin vừa điều phối lại cho tổ chức khác (assignedAt mới)
            if (i.getStatus() != IncidentStatus.ASSIGNED || i.isAssignTimeoutNotified()
                    || i.getAssignedAt() == null || i.getAssignedAt().isAfter(before)) return;
            i.setAssignTimeoutNotified(true);
            assignments.findFirstByIncidentIdOrderByCreatedAtDesc(i.getId()).ifPresent(a -> {
                if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.TIMEOUT); a.setRespondedAt(Instant.now()); }
            });
            // Ghi tên tổ chức: một ca có thể quá hạn nhiều lần (mỗi lần điều phối cho một tổ chức), thiếu tên thì các thông báo trông trùng nhau
            String org = i.getAssignedOrg() == null ? "Tổ chức được phân công" : i.getAssignedOrg().getName();
            notifications.notifyAdmins(NotificationType.SOS_TIMEOUT, "Tổ chức không phản hồi",
                    org + " chưa xác nhận ca " + i.getCode() + " sau " + SosService.ASSIGN_TIMEOUT_MIN + " phút. Cần điều phối lại cho tổ chức khác; "
                            + "nếu chưa ai điều phối, ca sẽ tự trả về Chờ xử lý sau " + Math.max(0, assignReleaseMinutes) + " phút nữa.",
                    "INCIDENT", i.getId());
        });
    }

    /**
     * Ca Đã phân công quá hạn phản hồi (5 phút, đã ghi TIMEOUT và báo Admin) mà thêm app.rescue.assign-release-minutes phút
     * (mặc định 10, tức tổng 15 phút kể từ lúc giao) vẫn chưa được Admin điều phối lại: tự trả về Chờ xử lý để các tổ chức Sẵn sàng
     * gần đó nhận được, không để ca nằm chờ khi Admin vắng mặt. Bản ghi phân công giữ TIMEOUT (tổ chức thật sự không phản hồi).
     * Trong khoảng phút 5 đến 15 tổ chức vẫn xác nhận muộn được; khóa dòng rồi kiểm tra lại nên ca vừa được xác nhận hoặc vừa được
     * điều phối lại (thời điểm giao mới) thì bỏ qua.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 45_000)
    public void releaseStaleAssignments() {
        long totalMinutes = SosService.ASSIGN_TIMEOUT_MIN + Math.max(0, assignReleaseMinutes);
        Instant before = Instant.now().minus(Duration.ofMinutes(totalMinutes));
        eachLocked("releaseStaleAssignments", incidents.findAssignReleaseIds(IncidentStatus.ASSIGNED, before), i -> {
            RescueOrganization o = i.getAssignedOrg();
            if (i.getStatus() != IncidentStatus.ASSIGNED || !i.isAssignTimeoutNotified() || o == null
                    || i.getAssignedAt() == null || i.getAssignedAt().isAfter(before)) return;
            Instant now = Instant.now();
            assignments.findFirstByIncidentIdOrderByCreatedAtDesc(i.getId()).ifPresent(a -> {
                // Bình thường đã là TIMEOUT (assignTimeouts); còn đang chờ thì cũng chốt là không phản hồi
                if (a.getStatus() == AssignmentStatus.WAITING_CONFIRM) { a.setStatus(AssignmentStatus.TIMEOUT); a.setRespondedAt(now); }
            });
            notifications.notify(o.getUser(), NotificationType.SOS_STATUS, "Ca SOS đã chuyển cho tổ chức khác",
                    "Tổ chức không xác nhận ca " + i.getCode() + " sau " + totalMinutes + " phút nên ca đã được chuyển về Chờ xử lý để tổ chức khác tiếp nhận.",
                    "INCIDENT", i.getId());
            notifications.notifyAdmins(NotificationType.SOS_TIMEOUT, "Ca SOS tự trả về Chờ xử lý",
                    o.getName() + " không phản hồi ca " + i.getCode() + " sau " + totalMinutes + " phút và ca chưa được điều phối lại, "
                            + "nên ca đã tự trả về Chờ xử lý và được báo cho các tổ chức gần đó.", "INCIDENT", i.getId());
            rescue.returnToPending(i, o);
        });
    }

    /**
     * Ca Đã tiếp nhận quá N giờ (mặc định 2) chưa hoàn thành: nhắc tổ chức phụ trách bấm Hoàn thành nếu đã xong, và báo Admin
     * liên hệ nắm tình hình. Mỗi ca nhắc một lần; chỉ là lưới an toàn, không tự đổi trạng thái ca.
     */
    @Scheduled(fixedDelay = 60_000, initialDelay = 50_000)
    public void acceptedTooLong() {
        Instant before = Instant.now().minus(Duration.ofHours(acceptedReminderHours));
        eachLocked("acceptedTooLong", incidents.findAcceptedTooLongIds(IncidentStatus.ACCEPTED, before), i -> {
            // Có thể tổ chức vừa bấm Hoàn thành
            if (i.getStatus() != IncidentStatus.ACCEPTED || i.isAcceptedTimeoutNotified()
                    || i.getAcceptedAt() == null || i.getAcceptedAt().isAfter(before)) return;
            i.setAcceptedTimeoutNotified(true);
            String org = i.getAssignedOrg() == null ? "tổ chức" : i.getAssignedOrg().getName();
            if (i.getAssignedOrg() != null)
                notifications.notify(i.getAssignedOrg().getUser(), NotificationType.SOS_TIMEOUT, "Ca cứu hộ kéo dài",
                        "Ca " + i.getCode() + " đã được tiếp nhận hơn " + acceptedReminderHours + " giờ nhưng chưa hoàn thành. "
                                + "Nếu đã cứu hộ xong, hãy bấm Hoàn thành; nếu gặp khó khăn, hãy liên hệ Admin.", "INCIDENT", i.getId());
            notifications.notifyAdmins(NotificationType.SOS_TIMEOUT, "Ca cứu hộ kéo dài",
                    "Ca " + i.getCode() + " do " + org + " tiếp nhận đã quá " + acceptedReminderHours + " giờ chưa hoàn thành. "
                            + "Cần liên hệ tổ chức để nắm tình hình; nếu người dân đã an toàn, Admin có thể bấm Hoàn thành.", "INCIDENT", i.getId());
        });
    }
}
