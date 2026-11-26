package vn.floodconnect.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import vn.floodconnect.notification.entity.Notification;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.repository.NotificationRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Bỏ thích rút lượt khỏi thông báo gộp chưa đọc, để thích lại không bị đếm thành người thứ hai. */
class NotificationServiceTest {
    private final NotificationRepository repo = mock(NotificationRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final NotificationService service = new NotificationService(repo, users, mock(SimpMessagingTemplate.class),
            mock(CurrentUser.class));
    private final User author = new User();
    private final Notification group = new Notification();
    private final Instant groupStart = Instant.parse("2026-11-02T10:00:00Z");

    @BeforeEach void setUp() {
        author.setId(1L);
        group.setGroupKey("like:9"); group.setGroupCount(3); group.setCreatedAt(groupStart);
        when(repo.findFirstByUserIdAndGroupKeyAndReadFalse(1L, "like:9")).thenReturn(Optional.of(group));
    }

    @Test void unlikeCountedInOpenGroupDecrements() {
        service.retractGrouped(author, "like:9", groupStart.plusSeconds(60), n -> n + " người đã thích bài viết của bạn.");
        assertEquals(2, group.getGroupCount());
        assertEquals("2 người đã thích bài viết của bạn.", group.getContent());
    }

    @Test void firstLikeSavedJustBeforeTheNotificationStillCounts() {
        service.retractGrouped(author, "like:9", groupStart.minusMillis(5), n -> n + " người");
        assertEquals(2, group.getGroupCount());
    }

    @Test void olderLikeFromAlreadyReadGroupIsIgnored() {
        service.retractGrouped(author, "like:9", groupStart.minus(Duration.ofDays(1)), n -> n + " người");
        assertEquals(3, group.getGroupCount());
    }

    @Test void retractingLastLikeDeletesTheNotification() {
        group.setGroupCount(1);
        service.retractGrouped(author, "like:9", groupStart, n -> n + " người");
        verify(repo).delete(group);
    }

    @Test void notificationKeepsTheSubReferenceSoTheExactCommentCanBeOpened() {
        when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));
        service.notify(author, NotificationType.COMMENT, "Bình luận mới", "An đã bình luận bài viết của bạn.", "POST", 9L, 55L);
        verify(repo).save(argThat(n -> "POST".equals(n.getReferenceType()) && n.getReferenceId() == 9L && n.getSubReferenceId() == 55L));
        Notification n = new Notification();
        n.setUser(author); n.setReferenceType("POST"); n.setReferenceId(9L); n.setSubReferenceId(55L); n.setRead(false);
        assertEquals(55L, NotificationService.toDto(n).subReferenceId());
    }

    @Test void reportNotificationIsGroupedPerAdminAndClosedOnceHandled() {
        User a1 = new User(); a1.setId(100L); a1.setRole(Role.ADMIN);
        User a2 = new User(); a2.setId(101L); a2.setRole(Role.ADMIN);
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(a1, a2));
        Notification open = new Notification(); open.setUser(a1); open.setGroupCount(1); open.setRead(false);
        when(repo.findFirstByUserIdAndGroupKeyAndReadFalse(100L, "report:post:9")).thenReturn(Optional.of(open)); // a1 chưa đọc thông báo cũ
        when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyAdminsGrouped(NotificationType.REPORT_NEW, "report:post:9", "Bài viết bị báo cáo", n -> "có 2 báo cáo", "POST", 9L);

        assertEquals(2, open.getGroupCount());           // a1: cập nhật thông báo đang có, không tạo thêm
        assertEquals("có 2 báo cáo", open.getContent());
        verify(repo, times(2)).save(any(Notification.class)); // a2: tạo thông báo mới

        service.closeGroup("report:post:9");
        verify(repo).markGroupRead("report:post:9");
    }

    /** Admin bị Admin gốc vô hiệu hóa (không đăng nhập được) không nhận thông báo SOS nữa. */
    @Test void disabledAdminsAreNotNotified() {
        User active = new User(); active.setId(100L); active.setRole(Role.ADMIN);
        User disabled = new User(); disabled.setId(101L); disabled.setRole(Role.ADMIN);
        disabled.setStatus(vn.floodconnect.user.entity.UserStatus.LOCKED); // vô hiệu hóa: không có thời hạn
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(active, disabled));
        when(repo.save(any(Notification.class))).thenAnswer(inv -> inv.getArgument(0));

        service.notifyAdmins(NotificationType.SOS_NEW, "Ca SOS mới", "Ca SOS-1001 mức Cao", "INCIDENT", 1L);

        verify(repo).save(argThat(n -> n.getUser() == active));
        verify(repo, never()).save(argThat(n -> n.getUser() == disabled));
    }
}
