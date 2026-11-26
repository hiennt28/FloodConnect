package vn.floodconnect.campaign.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.floodconnect.campaign.service.CampaignService;
import vn.floodconnect.campaign.service.DonationService;
import java.time.Duration;
import java.time.Instant;

/** Tác vụ định kỳ: cập nhật trạng thái chiến dịch (bắt đầu/kết thúc theo thời gian); hết hạn giao dịch ủng hộ bỏ dở. */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignScheduler {
    private final CampaignService campaigns;
    private final DonationService donations;
    /** Phải dài hơn hạn thanh toán của cổng (VNPay: 15 phút, đặt trong vnp_ExpireDate) để không đóng giao dịch đang thanh toán dở. */
    @Value("${app.payment.pending-expire-minutes:30}") private long pendingExpireMinutes;

    /** Bắt đầu / hết hạn chiến dịch theo thời gian; mỗi chiến dịch một transaction, lỗi một chiến dịch không chặn chiến dịch khác. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 20_000)
    public void campaignStatuses() {
        Instant now = Instant.now();
        for (Long id : campaigns.idsToStart(now)) {
            try { campaigns.startIfDue(id, now); }
            catch (RuntimeException e) { log.warn("Không bắt đầu được chiến dịch id={}, sẽ thử lại ở lần chạy sau", id, e); }
        }
        for (Long id : campaigns.idsToExpire(now)) {
            try { campaigns.expireIfDue(id, now); }
            catch (RuntimeException e) { log.warn("Không kết thúc được chiến dịch hết hạn id={}, sẽ thử lại ở lần chạy sau", id, e); }
        }
    }

    /** Giao dịch PENDING quá hạn → EXPIRED; mỗi giao dịch một transaction (khóa dòng, kiểm tra lại), lỗi một giao dịch không chặn giao dịch khác. */
    @Scheduled(fixedDelay = 60_000, initialDelay = 25_000)
    public void expireStaleDonations() {
        Instant before = Instant.now().minus(Duration.ofMinutes(pendingExpireMinutes));
        int expired = 0;
        for (Long id : donations.stalePendingIds(before)) {
            try {
                if (donations.expireIfStale(id, before)) expired++;
            } catch (RuntimeException e) {
                log.warn("Không chuyển được giao dịch ủng hộ id={} sang EXPIRED, sẽ thử lại ở lần chạy sau", id, e);
            }
        }
        if (expired > 0) log.info("Đã chuyển {} giao dịch ủng hộ quá {} phút chưa thanh toán sang EXPIRED", expired, pendingExpireMinutes);
    }
}
