package vn.floodconnect.stats.dto;

import java.time.Instant;

public final class PublicStatsDtos {
    private PublicStatsDtos() {}

    /**
     * Số liệu công khai cho trang giới thiệu (khách chưa đăng nhập cũng xem được).
     * Chỉ là con số tổng hợp, không có dữ liệu cá nhân hay vị trí của ai.
     * updatedAt: thời điểm tính, vì kết quả được lưu tạm vài phút.
     */
    public record PublicStats(long completedSos, long activeOrgs, long activeCampaigns, long totalRaised, Instant updatedAt) {}
}
