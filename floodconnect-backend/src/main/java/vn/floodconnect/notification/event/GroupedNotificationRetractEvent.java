package vn.floodconnect.notification.event;

import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.function.IntFunction;

/**
 * Rút một lượt khỏi thông báo gộp chưa đọc (vd. bỏ thích), nếu lượt đó đã được tính vào nhóm đang mở.
 * contributedAt: thời điểm lượt đó xảy ra (vd. lúc thích), dùng để biết nó thuộc nhóm đang mở hay nhóm đã đọc trước đó.
 */
public record GroupedNotificationRetractEvent(User to, String groupKey, Instant contributedAt, IntFunction<String> contentByCount) {}
