package vn.floodconnect.notification.event;

import vn.floodconnect.notification.entity.NotificationType;

/** Yêu cầu gửi cùng một thông báo tới tất cả tài khoản Admin. */
public record AdminNotificationEvent(NotificationType type, String title, String content, String refType, Long refId) {}
