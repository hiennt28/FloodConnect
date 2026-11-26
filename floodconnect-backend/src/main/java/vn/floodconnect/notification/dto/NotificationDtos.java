package vn.floodconnect.notification.dto;

import java.time.Instant;
import vn.floodconnect.notification.entity.NotificationType;

public final class NotificationDtos {
    private NotificationDtos() {}

    public record NotificationDto(Long id, NotificationType type, String title, String content,
                                  String referenceType, Long referenceId, Long subReferenceId, int groupCount, boolean read, Instant createdAt) {}
}
