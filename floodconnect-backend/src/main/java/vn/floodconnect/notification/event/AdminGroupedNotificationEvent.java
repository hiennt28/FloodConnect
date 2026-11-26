package vn.floodconnect.notification.event;

import vn.floodconnect.notification.entity.NotificationType;
import java.util.function.IntFunction;

/** Yêu cầu gửi thông báo gộp theo groupKey tới mọi Admin (Admin nào còn thông báo chưa đọc cùng nhóm thì cập nhật thay vì tạo mới). */
public record AdminGroupedNotificationEvent(NotificationType type, String groupKey, String title,
                                            IntFunction<String> contentByCount, String refType, Long refId) {}
