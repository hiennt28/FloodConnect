package vn.floodconnect.notification.event;

/** Việc mà nhóm thông báo nhắc tới đã được xử lý (vd. Admin đã kết luận báo cáo): đánh dấu đã đọc mọi thông báo chưa đọc của nhóm. */
public record NotificationGroupClosedEvent(String groupKey) {}
