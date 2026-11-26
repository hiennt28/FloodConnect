package vn.floodconnect.stats.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.stats.dto.PublicStatsDtos.PublicStats;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

/**
 * Thống kê công khai cho trang giới thiệu.
 * Endpoint không cần đăng nhập nên ai cũng gọi được, kể cả tải lại liên tục: kết quả được lưu tạm TTL (5 phút)
 * để cơ sở dữ liệu không phải đếm lại ở mỗi lượt truy cập. Con số trễ tối đa 5 phút là chấp nhận được cho trang giới thiệu.
 */
@Service
@RequiredArgsConstructor
public class PublicStatsService {
    static final Duration TTL = Duration.ofMinutes(5);

    private final IncidentRepository incidents;
    private final RescueOrganizationRepository orgs;
    private final FundraisingCampaignRepository campaigns;
    private final DonationRepository donations;

    /** Đồng hồ; test thay bằng đồng hồ giả để kiểm tra thời gian lưu tạm. */
    Supplier<Instant> clock = Instant::now;
    /** volatile: luồng khác thấy ngay bản mới. Hai yêu cầu cùng lúc lỡ cùng tính lại thì cũng chỉ tốn thêm một lượt đếm, không sai. */
    private volatile PublicStats cached;

    @Transactional(readOnly = true)
    public PublicStats stats() {
        Instant now = clock.get();
        PublicStats c = cached;
        if (c != null && c.updatedAt().plus(TTL).isAfter(now)) return c;
        PublicStats fresh = new PublicStats(
                incidents.countByStatus(IncidentStatus.COMPLETED),
                orgs.countByStatus(OrgStatus.ACTIVE),
                campaigns.countByStatus(CampaignStatus.ACTIVE),
                donations.sumAmountByStatus(DonationStatus.SUCCESS),
                now);
        cached = fresh;
        return fresh;
    }

    /** Tổ chức đã duyệt đang Sẵn sàng nhận ca (tổ chức bị khóa có trạng thái LOCKED nên không tính). Không lưu đệm. */
    @Transactional(readOnly = true)
    public long availableOrgs() { return orgs.countByStatusAndOperatingStatus(OrgStatus.ACTIVE, OperatingStatus.AVAILABLE); }
}
