package vn.floodconnect.rescue.scheduler;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.rescue.service.RescueService;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Tác vụ định kỳ của ca SOS. Mô phỏng tranh chấp: danh sách id được lấy trước, rồi trước khi tác vụ khóa được ca thì tổ chức
 * đã kịp thao tác (nhận ca, xác nhận, hoàn thành). Ca khóa được mang trạng thái mới đó, và tác vụ phải bỏ qua chứ không ghi đè.
 */
class RescueSchedulerTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final RescueService rescue = mock(RescueService.class);
    private final RescueScheduler scheduler = new RescueScheduler(incidents, assignments, notifications, rescue, txManager);
    private final User orgUser = new User();
    private final RescueOrganization org = new RescueOrganization();
    private final Incident i = new Incident();

    @BeforeEach void setUp() {
        ReflectionTestUtils.setField(scheduler, "acceptedReminderHours", 2L);
        org.setId(5L); org.setName("Đội A"); org.setUser(orgUser);
        i.setId(3L); i.setCode("SOS-1003"); i.setDangerLevel(DangerLevel.CAO);
        i.setCreatedAt(Instant.now().minus(Duration.ofMinutes(20)));
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
    }

    private void accepted(long minutesAgo) {
        i.setStatus(IncidentStatus.ACCEPTED); i.setAssignedOrg(org);
        i.setAcceptedAt(Instant.now().minus(Duration.ofMinutes(minutesAgo)));
    }

    // ---------- Cứu hộ kéo dài ----------
    @Test void caseAcceptedTooLongRemindsOrgAndAdminsOnce() {
        accepted(150);
        when(incidents.findAcceptedTooLongIds(eq(IncidentStatus.ACCEPTED), any())).thenReturn(List.of(3L));

        scheduler.acceptedTooLong();

        assertTrue(i.isAcceptedTimeoutNotified()); // lần chạy sau truy vấn không lấy lại ca này
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus()); // chỉ nhắc, không tự đổi trạng thái
        verify(notifications).notify(eq(orgUser), eq(NotificationType.SOS_TIMEOUT), anyString(), contains("hơn 2 giờ"), eq("INCIDENT"), eq(3L));
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_TIMEOUT), anyString(), contains("Đội A"), eq("INCIDENT"), eq(3L));
    }

    @Test void looksBackTheConfiguredNumberOfHours() {
        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        when(incidents.findAcceptedTooLongIds(eq(IncidentStatus.ACCEPTED), before.capture())).thenReturn(List.of());

        scheduler.acceptedTooLong();

        long minutesAgo = Duration.between(before.getValue(), Instant.now()).toMinutes();
        assertTrue(minutesAgo >= 119 && minutesAgo <= 120);
        verifyNoInteractions(notifications);
    }

    @Test void caseCompletedMeanwhileIsNotRemindedNorReverted() {
        accepted(150);
        i.setStatus(IncidentStatus.COMPLETED); i.setCompletedAt(Instant.now()); // tổ chức bấm Hoàn thành ngay trước khi tác vụ khóa được ca
        when(incidents.findAcceptedTooLongIds(eq(IncidentStatus.ACCEPTED), any())).thenReturn(List.of(3L));

        scheduler.acceptedTooLong();

        assertEquals(IncidentStatus.COMPLETED, i.getStatus());
        assertFalse(i.isAcceptedTimeoutNotified());
        verifyNoInteractions(notifications);
    }

    // ---------- Chờ xử lý quá 15 phút ----------
    @Test void pendingCaseWaitingTooLongAlertsAdmins() {
        i.setStatus(IncidentStatus.PENDING);
        when(incidents.findPendingTimedOutIds(eq(IncidentStatus.PENDING), any())).thenReturn(List.of(3L));

        scheduler.pendingTimeouts();

        assertTrue(i.isPendingTimeoutNotified());
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_TIMEOUT), eq("Ca SOS chưa có tổ chức tiếp nhận"), anyString(), eq("INCIDENT"), eq(3L));
    }

    @Test void caseClaimedMeanwhileKeepsTheOrgThatClaimedIt() {
        accepted(0); // danh sách lấy lúc ca còn PENDING; tổ chức A nhận ca trước khi tác vụ khóa được
        when(incidents.findPendingTimedOutIds(eq(IncidentStatus.PENDING), any())).thenReturn(List.of(3L));

        scheduler.pendingTimeouts();

        assertEquals(IncidentStatus.ACCEPTED, i.getStatus()); // trước đây có thể bị ghi đè về PENDING, mất tổ chức nhận ca
        assertSame(org, i.getAssignedOrg());
        assertFalse(i.isPendingTimeoutNotified());
        verifyNoInteractions(notifications);
    }

    // ---------- Phân công quá 5 phút không phản hồi ----------
    @Test void assignmentNotAnsweredIsMarkedTimeout() {
        i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedOrg(org); i.setAssignedAt(Instant.now().minus(Duration.ofMinutes(7)));
        IncidentAssignment a = new IncidentAssignment();
        a.setStatus(AssignmentStatus.WAITING_CONFIRM);
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));
        when(incidents.findAssignedTimedOutIds(eq(IncidentStatus.ASSIGNED), any())).thenReturn(List.of(3L));

        scheduler.assignTimeouts();

        assertTrue(i.isAssignTimeoutNotified());
        assertEquals(AssignmentStatus.TIMEOUT, a.getStatus());
        // ghi tên tổ chức để phân biệt các lần quá hạn của cùng một ca
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_TIMEOUT), eq("Tổ chức không phản hồi"), startsWith("Đội A chưa xác nhận ca"), eq("INCIDENT"), eq(3L));
    }

    @Test void assignmentAcceptedOrReassignedMeanwhileIsLeftAlone() {
        when(incidents.findAssignedTimedOutIds(eq(IncidentStatus.ASSIGNED), any())).thenReturn(List.of(3L));

        accepted(0); // tổ chức vừa xác nhận
        scheduler.assignTimeouts();
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus());

        i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedAt(Instant.now()); // Admin vừa điều phối lại: còn trong 5 phút mới
        scheduler.assignTimeouts();

        assertFalse(i.isAssignTimeoutNotified());
        verifyNoInteractions(assignments, notifications);
    }

    // ---------- Quá hạn phản hồi + 10 phút chưa điều phối lại: tự trả về Chờ xử lý ----------
    private IncidentAssignment timedOut(long assignedMinutesAgo) {
        i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedOrg(org); i.setAssignTimeoutNotified(true);
        i.setAssignedAt(Instant.now().minus(Duration.ofMinutes(assignedMinutesAgo)));
        IncidentAssignment a = new IncidentAssignment();
        a.setStatus(AssignmentStatus.TIMEOUT);
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));
        when(incidents.findAssignReleaseIds(eq(IncidentStatus.ASSIGNED), any())).thenReturn(List.of(3L));
        return a;
    }

    @Test void assignedCaseNobodyHandledReturnsToPendingAfter15Minutes() {
        IncidentAssignment a = timedOut(16);

        scheduler.releaseStaleAssignments();

        verify(rescue).returnToPending(i, org); // về Chờ xử lý, báo người dân và tổ chức gần đó (trừ tổ chức này)
        assertEquals(AssignmentStatus.TIMEOUT, a.getStatus()); // vẫn tính là không phản hồi cho điểm W
        verify(notifications).notify(eq(orgUser), eq(NotificationType.SOS_STATUS), anyString(), contains("15 phút"), eq("INCIDENT"), eq(3L));
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_TIMEOUT), eq("Ca SOS tự trả về Chờ xử lý"), contains("Đội A"), eq("INCIDENT"), eq(3L));
    }

    @Test void releaseLooksBackFiveMinutesPlusTheConfiguredDelay() {
        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        when(incidents.findAssignReleaseIds(eq(IncidentStatus.ASSIGNED), before.capture())).thenReturn(List.of());

        scheduler.releaseStaleAssignments();

        long minutesAgo = Duration.between(before.getValue(), Instant.now()).toMinutes();
        assertTrue(minutesAgo >= 14 && minutesAgo <= 15, "5 phút chờ phản hồi + 10 phút mặc định");
        verifyNoInteractions(notifications, rescue);
    }

    @Test void caseConfirmedLateOrReassignedMeanwhileIsNotReleased() {
        timedOut(16);
        accepted(0); // tổ chức xác nhận muộn (phút 5–15) ngay trước khi tác vụ khóa được ca
        scheduler.releaseStaleAssignments();
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus());

        i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedAt(Instant.now()); // Admin vừa điều phối lại cho tổ chức khác
        scheduler.releaseStaleAssignments();

        verifyNoInteractions(notifications, rescue);
    }

    @Test void caseNotYetMarkedTimeoutIsNotReleased() {
        timedOut(16);
        i.setAssignTimeoutNotified(false); // Admin điều phối lại lần trước đã đặt lại cờ: chưa qua mốc 5 phút của lần giao này

        scheduler.releaseStaleAssignments();

        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
        verifyNoInteractions(notifications, rescue);
    }

    // ---------- Lỗi một ca không chặn các ca khác ----------
    @Test void failureOnOneCaseDoesNotStopTheOthers() {
        Incident other = new Incident();
        other.setId(4L); other.setCode("SOS-1004"); other.setStatus(IncidentStatus.PENDING); other.setDangerLevel(DangerLevel.THAP);
        other.setCreatedAt(Instant.now().minus(Duration.ofMinutes(30)));
        when(incidents.findByIdForUpdate(3L)).thenThrow(new IllegalStateException("dữ liệu hỏng"));
        when(incidents.findByIdForUpdate(4L)).thenReturn(Optional.of(other));
        when(incidents.findPendingTimedOutIds(eq(IncidentStatus.PENDING), any())).thenReturn(List.of(3L, 4L));

        scheduler.pendingTimeouts();

        assertTrue(other.isPendingTimeoutNotified());
        verify(notifications).notifyAdmins(any(), anyString(), contains("SOS-1004"), eq("INCIDENT"), eq(4L));
        verify(txManager, times(1)).rollback(any()); // chỉ transaction của ca lỗi bị hủy
        verify(txManager, times(1)).commit(any());
    }
}
