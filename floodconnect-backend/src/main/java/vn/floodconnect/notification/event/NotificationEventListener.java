package vn.floodconnect.notification.event;

import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import vn.floodconnect.notification.service.NotificationService;

/**
 * Nhận yêu cầu thông báo từ các module và giao cho NotificationService (lưu DB + đẩy WebSocket).
 * Dùng @EventListener (đồng bộ) chứ không dùng @TransactionalEventListener(AFTER_COMMIT): đổi sang AFTER_COMMIT sẽ thay đổi
 * hành vi (thông báo được lưu ngoài transaction nghiệp vụ, lỗi không còn làm rollback nghiệp vụ).
 */
@Component
@RequiredArgsConstructor
public class NotificationEventListener {
    private final NotificationService notifications;

    @EventListener
    public void on(UserNotificationEvent e) {
        notifications.notify(e.to(), e.type(), e.title(), e.content(), e.refType(), e.refId(), e.subRefId());
    }

    @EventListener
    public void on(AdminNotificationEvent e) {
        notifications.notifyAdmins(e.type(), e.title(), e.content(), e.refType(), e.refId());
    }

    @EventListener
    public void on(GroupedNotificationEvent e) {
        notifications.notifyGrouped(e.to(), e.type(), e.groupKey(), e.title(), e.contentByCount(), e.refType(), e.refId());
    }

    @EventListener
    public void on(AdminGroupedNotificationEvent e) {
        notifications.notifyAdminsGrouped(e.type(), e.groupKey(), e.title(), e.contentByCount(), e.refType(), e.refId());
    }

    @EventListener
    public void on(NotificationGroupClosedEvent e) {
        notifications.closeGroup(e.groupKey());
    }

    @EventListener
    public void on(GroupedNotificationRetractEvent e) {
        notifications.retractGrouped(e.to(), e.groupKey(), e.contributedAt(), e.contentByCount());
    }
}
