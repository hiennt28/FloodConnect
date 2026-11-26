package vn.floodconnect.campaign.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.PaymentMethod;
import vn.floodconnect.common.dto.CommonDtos.MediaDto;

public final class CampaignDtos {
    private CampaignDtos() {}

    public record CampaignRequest(
            String name, String purpose, String supportArea, String targetAudience, String orgInfo,
            Long targetAmount, String bankName, String accountHolder, String accountNumber,
            Instant startDate, Instant endDate) {}
    public record ExtendRequest(@NotNull Instant endDate) {}
    public record DonateRequest(@NotNull @Positive(message = "Số tiền phải lớn hơn 0.") Long amount,
                                @Size(max = 500) String message, PaymentMethod paymentMethod, Boolean anonymous) {} // paymentMethod: bỏ trống = VNPAY (chỉ nhận VNPay)
    public record CampaignDto(Long id, Long orgId, String orgName, String name, String avatarUrl, String purpose,
                              String supportArea, String targetAudience, String orgInfo, long targetAmount,
                              long raisedAmount, String bankName, String accountHolder, String accountNumber,
                              Instant startDate, Instant endDate, CampaignStatus status, EndReason endReason,
                              boolean canDonate, boolean reportedByMe, List<MediaDto> media, Instant createdAt) {}
    public record DonationDto(Long id, Long campaignId, String campaignName, String donorName, long amount,
                              String message, PaymentMethod paymentMethod, String transactionCode,
                              DonationStatus status, Instant createdAt) {}
    public record DonationInit(Long donationId, String transactionCode, String paymentUrl, boolean mock) {}
    public record DonationHistory(List<DonationDto> items, long totalSuccessAmount) {}
}
