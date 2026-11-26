package vn.floodconnect.notification.event;

import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.user.entity.User;
import java.util.function.IntFunction;

/** Yêu cầu gửi thông báo gộp theo groupKey (còn thông báo chưa đọc cùng nhóm thì tăng bộ đếm). */
public record GroupedNotificationEvent(User to, NotificationType type, String groupKey, String title,
                                       IntFunction<String> contentByCount, String refType, Long refId) {}
