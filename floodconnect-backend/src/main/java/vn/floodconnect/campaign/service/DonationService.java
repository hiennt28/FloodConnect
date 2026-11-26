package vn.floodconnect.campaign.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.campaign.dto.CampaignDtos.DonateRequest;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationDto;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationHistory;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationInit;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.entity.PaymentMethod;
import vn.floodconnect.campaign.payment.PaymentGateway;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.util.Format;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DonationService {
    private final FundraisingCampaignRepository campaigns;
    private final DonationRepository donations;
    private final PaymentGateway gateway;
    private final CurrentUser current;
    private final NotificationPublisher notifications;
    private final CampaignMapper campaignMapper;
    private final PlatformTransactionManager txManager;
    private static final SecureRandom RND = new SecureRandom();

    /**
     * Khoảng số tiền mỗi lần ủng hộ (VND). Mặc định theo giới hạn thường gặp của VNPay (từ 5.000đ đến dưới 1 tỷ đồng); con số này
     * chưa đối chiếu được với tài liệu chính thức của VNPay nên để cấu hình được (PAYMENT_MIN_AMOUNT / PAYMENT_MAX_AMOUNT).
     * Ngoài khoảng này VNPay báo lỗi trên trang của họ và giao dịch treo PENDING tới khi hết hạn, nên chặn ngay từ đầu.
     */
    @Value("${app.payment.min-amount:5000}") private long minAmount = 5000;
    @Value("${app.payment.max-amount:999999999}") private long maxAmount = 999_999_999;

    /**
     * Tạo khoản ủng hộ PENDING rồi xin link thanh toán VNPay. Không bọc cả hàm trong một transaction: bước gọi cổng thanh toán
     * nằm ngoài để không giữ kết nối DB khi cổng chậm (VNPay hiện tạo link tại chỗ, nhưng cổng khác hoặc phiên bản sau có thể
     * phải gọi HTTP). Bước 1 lưu giao dịch và commit; bước 2 gọi cổng; cổng lỗi thì đánh dấu FAILED để giao dịch không treo PENDING.
     */
    public DonationInit donate(Long campaignId, DonateRequest r) {
        TransactionTemplate tx = new TransactionTemplate(txManager);
        Donation d = tx.execute(status -> createPending(campaignId, r));
        try {
            String url = gateway.createPaymentUrl(d);
            return new DonationInit(d.getId(), d.getTransactionCode(), url, gateway.isMock());
        } catch (RuntimeException e) {
            try {
                tx.executeWithoutResult(status -> donations.findByIdForUpdate(d.getId()).ifPresent(x -> {
                    if (x.getStatus() == DonationStatus.PENDING) { x.setStatus(DonationStatus.FAILED); x.setCompletedAt(Instant.now()); }
                }));
            } catch (RuntimeException ignored) {
                log.warn("Không đánh dấu được FAILED cho giao dịch {} sau khi cổng lỗi; tác vụ hết hạn sẽ xử lý", d.getTransactionCode());
            }
            throw e;
        }
    }

    private Donation createPending(Long campaignId, DonateRequest r) {
        if (r.amount() == null || r.amount() < minAmount || r.amount() > maxAmount)
            throw ApiException.bad("INVALID_AMOUNT", "Số tiền ủng hộ phải từ " + Format.money(minAmount) + " đến " + Format.money(maxAmount) + ".");
        User u = current.get(); // tài khoản bị khóa vẫn được ủng hộ: đặc tả chỉ cấm đăng bài, tương tác và báo cáo
        FundraisingCampaign c = campaigns.findById(campaignId).orElseThrow(() -> ApiException.notFound("Chiến dịch không tồn tại."));
        if (c.getStatus() != CampaignStatus.ACTIVE) throw ApiException.conflict("CAMPAIGN_NOT_ACTIVE", "Chiến dịch hiện không nhận ủng hộ.");
        if (c.getRaisedAmount() >= c.getTargetAmount()) throw ApiException.conflict("TARGET_REACHED", "Chiến dịch đã đạt số tiền mục tiêu.");
        // Chỉ nhận ủng hộ qua VNPay (trên trang VNPay người ủng hộ tự chọn ngân hàng, quét QR hoặc nhập thẻ)
        if (r.paymentMethod() != null && r.paymentMethod() != PaymentMethod.VNPAY)
            throw ApiException.bad("PAYMENT_METHOD_NOT_SUPPORTED", "Hệ thống chỉ nhận ủng hộ qua VNPay.");
        Donation d = new Donation();
        d.setCampaign(c); d.setDonor(u); d.setAmount(r.amount()); d.setMessage(r.message());
        d.setPaymentMethod(PaymentMethod.VNPAY); d.setAnonymous(Boolean.TRUE.equals(r.anonymous()));
        d.setTransactionCode("VNP" + System.currentTimeMillis() + RND.nextInt(1000));
        d.setStatus(DonationStatus.PENDING);
        return donations.save(d);
    }

    /** Id các giao dịch còn PENDING tạo trước mốc before (CampaignScheduler xử lý từng giao dịch, mỗi giao dịch một transaction). */
    @Transactional(readOnly = true)
    public List<Long> stalePendingIds(Instant before) {
        return donations.findIdsByStatusCreatedBefore(DonationStatus.PENDING, before);
    }

    /**
     * Giao dịch người ủng hộ bỏ dở (đóng trang thanh toán): quá hạn mà cổng chưa báo kết quả thì chuyển EXPIRED.
     * Khóa dòng rồi kiểm tra lại, vì cổng có thể vừa báo kết quả. Không gửi thông báo: người dùng tự bỏ dở.
     * @return true nếu đã chuyển EXPIRED
     */
    @Transactional
    public boolean expireIfStale(Long id, Instant before) {
        Donation d = donations.findByIdForUpdate(id).orElse(null);
        if (d == null || d.getStatus() != DonationStatus.PENDING || d.getCreatedAt() == null || d.getCreatedAt().isAfter(before)) return false;
        d.setStatus(DonationStatus.EXPIRED);
        d.setCompletedAt(Instant.now());
        return true;
    }

    /**
     * Xử lý kết quả từ cổng thanh toán (idempotent). Dùng khóa dòng để cập nhật donation và tổng tiền chiến dịch trong cùng transaction.
     * Giao dịch đã khởi tạo trước khi chiến dịch ENDED vẫn được ghi nhận theo kết quả thực tế.
     */
    @Transactional
    public DonationDto processResult(String transactionCode, boolean success, String gatewayRef, Long paidAmount) {
        Donation d = donations.findByTransactionCodeForUpdate(transactionCode)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch."));
        // Đã xử lý thì bỏ qua (cổng báo qua cả return URL lẫn IPN). Riêng EXPIRED: thành công báo về muộn vẫn ghi nhận
        boolean late = d.getStatus() == DonationStatus.EXPIRED;
        if (d.getStatus() != DonationStatus.PENDING && !late) return campaignMapper.donation(d, false);
        if (success && paidAmount != null && paidAmount != d.getAmount()) {
            log.warn("Số tiền cổng thanh toán {} khác số tiền khởi tạo {} (giao dịch {})", paidAmount, d.getAmount(), transactionCode);
            success = false;
        }
        if (late && !success) return campaignMapper.donation(d, false); // hết hạn và cổng báo không thành công: giữ EXPIRED
        if (late) log.warn("Giao dịch {} đã hết hạn nhưng cổng báo thanh toán thành công: vẫn ghi nhận vì tiền đã bị trừ", transactionCode);
        d.setGatewayRef(gatewayRef);
        d.setCompletedAt(Instant.now());
        FundraisingCampaign c = campaigns.findByIdForUpdate(d.getCampaign().getId()).orElseThrow();
        if (success) {
            d.setStatus(DonationStatus.SUCCESS);
            c.setRaisedAmount(c.getRaisedAmount() + d.getAmount());
            if (c.getStatus() == CampaignStatus.ACTIVE && c.getRaisedAmount() >= c.getTargetAmount()) {
                c.setStatus(CampaignStatus.ENDED);
                c.setEndReason(EndReason.TARGET_REACHED);
                c.setEndedAt(Instant.now());
                notifications.notify(c.getOrg().getUser(), NotificationType.CAMPAIGN, "Chiến dịch đạt mục tiêu",
                        "Chiến dịch \"" + c.getName() + "\" đã đạt số tiền mục tiêu và tự động kết thúc.", "CAMPAIGN", c.getId());
            }
            notifications.notify(d.getDonor(), NotificationType.DONATION, "Ủng hộ thành công",
                    "Bạn đã ủng hộ " + Format.money(d.getAmount()) + " cho chiến dịch \"" + c.getName() + "\". Cảm ơn bạn!", "DONATION", d.getId()); // mở lịch sử ủng hộ
            notifications.notify(c.getOrg().getUser(), NotificationType.DONATION, "Khoản ủng hộ mới",
                    "Chiến dịch \"" + c.getName() + "\" nhận thêm " + Format.money(d.getAmount()) + ".", "CAMPAIGN", c.getId());
        } else {
            d.setStatus(DonationStatus.FAILED);
            notifications.notify(d.getDonor(), NotificationType.DONATION, "Thanh toán thất bại",
                    "Giao dịch " + d.getTransactionCode() + " không thành công hoặc đã bị hủy.", "CAMPAIGN", c.getId());
        }
        return campaignMapper.donation(d, false);
    }

    /** Lịch sử đóng góp của tôi; tổng chỉ cộng giao dịch thành công. */
    @Transactional(readOnly = true)
    public DonationHistory history() {
        List<Donation> list = donations.findByDonorIdOrderByCreatedAtDesc(current.id());
        long total = list.stream().filter(x -> x.getStatus() == DonationStatus.SUCCESS).mapToLong(Donation::getAmount).sum();
        return new DonationHistory(list.stream().map(x -> campaignMapper.donation(x, false)).toList(), total);
    }

    /** Sao kê công khai của chiến dịch: chỉ giao dịch thành công, mới nhất trước; ẩn danh được che tên. */
    @Transactional(readOnly = true)
    public PageResponse<DonationDto> statement(Long campaignId, int page, int size) {
        return PageResponse.of(donations.findByCampaignIdAndStatusOrderByCreatedAtDesc(campaignId, DonationStatus.SUCCESS,
                Paging.of(page, size)), x -> campaignMapper.donation(x, true));
    }
}