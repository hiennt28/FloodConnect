package vn.floodconnect.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.function.IntFunction;

/**
 * Điểm duy nhất để các module nghiệp vụ yêu cầu gửi thông báo. Chỉ phát sự kiện, không biết thông báo được lưu/đẩy ra sao.
 *
 * Sự kiện được xử lý ĐỒNG BỘ bởi {@link NotificationEventListener} (cùng luồng, cùng transaction với nơi gọi), nên hành vi giữ
 * nguyên như khi gọi thẳng NotificationService: thông báo được lưu trong transaction của nghiệp vụ, rollback thì mất theo, lỗi
 * khi lưu vẫn ném ngược về nơi gọi; đẩy realtime qua WebSocket vẫn chờ commit (xử lý trong NotificationService).
 */
@Component
@RequiredArgsConstructor
public class NotificationPublisher {
    private final ApplicationEventPublisher events;

    public void notify(User to, NotificationType type, String title, String content, String refType, Long refId) {
        events.publishEvent(new UserNotificationEvent(to, type, title, content, refType, refId));
    }

    /** Như notify, kèm đối tượng con để mở đúng chỗ (vd. refType POST, refId = bài viết, subRefId = bình luận). */
    public void notify(User to, NotificationType type, String title, String content, String refType, Long refId, Long subRefId) {
        events.publishEvent(new UserNotificationEvent(to, type, title, content, refType, refId, subRefId));
    }

    public void notifyAdmins(NotificationType type, String title, String content, String refType, Long refId) {
        events.publishEvent(new AdminNotificationEvent(type, title, content, refType, refId));
    }

    public void notifyGrouped(User to, NotificationType type, String groupKey, String title,
                              IntFunction<String> contentByCount, String refType, Long refId) {
        events.publishEvent(new GroupedNotificationEvent(to, type, groupKey, title, contentByCount, refType, refId));
    }

    public void retractGrouped(User to, String groupKey, Instant contributedAt, IntFunction<String> contentByCount) {
        events.publishEvent(new GroupedNotificationRetractEvent(to, groupKey, contributedAt, contentByCount));
    }

    public void notifyAdminsGrouped(NotificationType type, String groupKey, String title,
                                    IntFunction<String> contentByCount, String refType, Long refId) {
        events.publishEvent(new AdminGroupedNotificationEvent(type, groupKey, title, contentByCount, refType, refId));
    }

    public void closeGroup(String groupKey) {
        events.publishEvent(new NotificationGroupClosedEvent(groupKey));
    }

    /** Nhóm thông báo "có báo cáo mới" của một bài viết (kind = "post") hoặc chiến dịch (kind = "campaign"). */
    public static String reportGroupKey(String kind, Long id) { return "report:" + kind + ":" + id; }
}
