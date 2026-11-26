package vn.floodconnect.incident.service;

import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.incident.entity.IncidentStatus;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Bộ lọc trạng thái ca SOS từ giao diện: ACTIVE (đang hoạt động: Chờ xử lý, Đã phân công, Đã tiếp nhận) | ENDED (đã kết thúc:
 * Hoàn thành, Đã hủy, Không hợp lệ) | ALL hoặc rỗng (mọi trạng thái) | tên một trạng thái. Sai tên thì báo 400 INVALID_STATUS.
 */
public final class StatusFilter {
    public static final Set<IncidentStatus> ACTIVE = Collections.unmodifiableSet(EnumSet.copyOf(SosService.ACTIVE));
    public static final Set<IncidentStatus> ENDED =
            Collections.unmodifiableSet(EnumSet.of(IncidentStatus.COMPLETED, IncidentStatus.CANCELLED, IncidentStatus.INVALID));

    private StatusFilter() {}

    public static Set<IncidentStatus> parse(String status) {
        if (status == null || status.isBlank() || "ALL".equalsIgnoreCase(status)) return EnumSet.allOf(IncidentStatus.class);
        if ("ACTIVE".equalsIgnoreCase(status)) return EnumSet.copyOf(ACTIVE);
        if ("ENDED".equalsIgnoreCase(status)) return EnumSet.copyOf(ENDED);
        try {
            return EnumSet.of(IncidentStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException e) {
            throw ApiException.bad("INVALID_STATUS", "Trạng thái lọc không hợp lệ.");
        }
    }
}
