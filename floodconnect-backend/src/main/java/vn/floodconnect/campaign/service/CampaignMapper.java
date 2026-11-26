package vn.floodconnect.campaign.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationDto;
import vn.floodconnect.campaign.entity.CampaignMedia;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.repository.CampaignMediaRepository;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.common.dto.CommonDtos.MediaDto;
import java.util.*;

/**
 * Chuyển chiến dịch, khoản ủng hộ sang DTO. Phải gọi trong transaction (có quan hệ lazy).
 * Danh sách chiến dịch chuyển một lần (campaigns): ảnh và "tôi đã báo cáo" tra một câu cho cả danh sách, tổ chức được Hibernate
 * nạp theo lô (default_batch_fetch_size), không tra riêng từng chiến dịch.
 */
@Component
@RequiredArgsConstructor
public class CampaignMapper {
    private final CampaignMediaRepository campaignMedia;
    private final CampaignReportRepository campaignReports;

    // ---------- chiến dịch ----------
    public CampaignDto campaign(FundraisingCampaign c, Long viewerId) { return campaigns(List.of(c), viewerId).get(0); }

    /** Cùng thứ tự với list. viewerId null thì reportedByMe luôn false. */
    public List<CampaignDto> campaigns(List<FundraisingCampaign> list, Long viewerId) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(FundraisingCampaign::getId).toList();
        Map<Long, List<MediaDto>> media = new HashMap<>();
        for (CampaignMedia m : campaignMedia.findByCampaignIdInOrderBySortOrderAsc(ids))
            media.computeIfAbsent(m.getCampaign().getId(), k -> new ArrayList<>()).add(new MediaDto(m.getId(), m.getMediaUrl(), "IMAGE"));
        Set<Long> reported = viewerId == null ? Set.of() : new HashSet<>(campaignReports.findReportedCampaignIds(viewerId, ids));
        return list.stream().map(c -> {
            boolean canDonate = c.getStatus() == CampaignStatus.ACTIVE && c.getRaisedAmount() < c.getTargetAmount();
            return new CampaignDto(c.getId(), c.getOrg().getId(), c.getOrg().getName(), c.getName(), c.getAvatarUrl(), c.getPurpose(),
                    c.getSupportArea(), c.getTargetAudience(), c.getOrgInfo(), c.getTargetAmount(), c.getRaisedAmount(),
                    c.getBankName(), c.getAccountHolder(), c.getAccountNumber(), c.getStartDate(), c.getEndDate(),
                    c.getStatus(), c.getEndReason(), canDonate, reported.contains(c.getId()), media.getOrDefault(c.getId(), List.of()), c.getCreatedAt());
        }).toList();
    }

    public DonationDto donation(Donation d, boolean maskDonor) {
        String donor = d.isAnonymous() && maskDonor ? "Ẩn danh" : d.getDonor().getFullName();
        return new DonationDto(d.getId(), d.getCampaign().getId(), d.getCampaign().getName(), donor, d.getAmount(), d.getMessage(),
                d.getPaymentMethod(), d.getTransactionCode(), d.getStatus(), d.getCreatedAt());
    }
}
