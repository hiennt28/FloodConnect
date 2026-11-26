package vn.floodconnect.notification.event;

import org.junit.jupiter.api.Test;
import vn.floodconnect.notification.entity.NotificationType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import static org.junit.jupiter.api.Assertions.*;

/** NotificationPublisher chỉ đóng gói tham số thành sự kiện, không thêm/bớt dữ liệu. */
class NotificationPublisherTest {
    private final List<Object> published = new ArrayList<>();
    private final NotificationPublisher publisher = new NotificationPublisher(published::add);

    @Test void notifyPublishesUserEvent() {
        publisher.notify(null, NotificationType.SOS_STATUS, "Tiêu đề", "Nội dung", "INCIDENT", 7L);
        assertEquals(List.of(new UserNotificationEvent(null, NotificationType.SOS_STATUS, "Tiêu đề", "Nội dung", "INCIDENT", 7L)), published);
    }

    @Test void notifyAdminsPublishesAdminEvent() {
        publisher.notifyAdmins(NotificationType.SOS_TIMEOUT, "T", "C", "INCIDENT", 3L);
        assertEquals(List.of(new AdminNotificationEvent(NotificationType.SOS_TIMEOUT, "T", "C", "INCIDENT", 3L)), published);
    }

    @Test void notifyGroupedKeepsContentFunction() {
        IntFunction<String> content = n -> n + " lượt thích";
        publisher.notifyGrouped(null, NotificationType.SOS_STATUS, "like:1", "T", content, "POST", 1L);
        GroupedNotificationEvent e = (GroupedNotificationEvent) published.get(0);
        assertEquals("like:1", e.groupKey());
        assertEquals("3 lượt thích", e.contentByCount().apply(3));
        assertEquals(1, published.size());
    }

    @Test void retractGroupedPublishesRetractEvent() {
        Instant likedAt = Instant.parse("2026-11-02T10:00:00Z");
        publisher.retractGrouped(null, "like:1", likedAt, n -> n + " người");
        GroupedNotificationRetractEvent e = (GroupedNotificationRetractEvent) published.get(0);
        assertEquals("like:1", e.groupKey());
        assertEquals(likedAt, e.contributedAt());
        assertEquals("2 người", e.contentByCount().apply(2));
    }
}
