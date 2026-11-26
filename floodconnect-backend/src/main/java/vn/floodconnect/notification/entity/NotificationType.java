package vn.floodconnect.notification.entity;

/** REPORT_NEW: gửi Admin khi bài viết / chiến dịch bị báo cáo (gộp theo từng bài / chiến dịch). */
public enum NotificationType {
    SYSTEM, COMMENT, REPLY, LIKE, FOLLOW, POST_REMOVED, REPORT_RESULT, ORG_REVIEW,
    SOS_NEW, SOS_STATUS, SOS_TIMEOUT, SOS_INVALID, ACCOUNT, CAMPAIGN, DONATION, REPORT_NEW
}
