package vn.floodconnect.campaign.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.dto.CampaignDtos.DonateRequest;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationDto;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationHistory;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationInit;
import vn.floodconnect.campaign.service.CampaignService;
import vn.floodconnect.campaign.service.DonationService;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;

/** Chiến dịch ủng hộ phía người dùng (cần đăng nhập). */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CampaignController {
    private final CampaignService campaigns;
    private final DonationService donations;

    /** status = UPCOMING | ACTIVE (mặc định) | ENDED | ALL */
    @GetMapping("/campaigns")
    public PageResponse<CampaignDto> list(@RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(required = false) String q,
                                          @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "12") int size) { return campaigns.list(status, q, page, size); }

    @GetMapping("/campaigns/{id}") public CampaignDto detail(@PathVariable Long id) { return campaigns.detail(id); }
    @GetMapping("/campaigns/{id}/donations")
    public PageResponse<DonationDto> statement(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return donations.statement(id, page, size); }
    @PostMapping("/campaigns/{id}/donate") public DonationInit donate(@PathVariable Long id, @Valid @RequestBody DonateRequest r) { return donations.donate(id, r); }
    @PostMapping("/campaigns/{id}/report") public MessageResponse report(@PathVariable Long id, @Valid @RequestBody ReportRequest r) { return campaigns.report(id, r); }
    @GetMapping("/donations/mine") public DonationHistory history() { return donations.history(); }
}
