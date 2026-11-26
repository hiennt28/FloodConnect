package vn.floodconnect.notification.event;

import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.user.entity.User;

/** Yêu cầu gửi một thông báo tới một người dùng (to = null thì bỏ qua, như trước). subRefId: vd. mã bình luận trong bài viết refId. */
public record UserNotificationEvent(User to, NotificationType type, String title, String content, String refType, Long refId, Long subRefId) {
    public UserNotificationEvent(User to, NotificationType type, String title, String content, String refType, Long refId) {
        this(to, type, title, content, refType, refId, null);
    }
}
