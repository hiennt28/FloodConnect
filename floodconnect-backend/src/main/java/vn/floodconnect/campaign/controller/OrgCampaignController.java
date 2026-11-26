package vn.floodconnect.campaign.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignRequest;
import vn.floodconnect.campaign.dto.CampaignDtos.ExtendRequest;
import vn.floodconnect.campaign.service.CampaignService;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import java.util.List;

/** Tổ chức cứu hộ quản lý chiến dịch gây quỹ của mình (role RESCUE_ORG). */
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrgCampaignController {
    private final CampaignService campaigns;

    // ---- chiến dịch của tổ chức ----
    @GetMapping("/campaigns") public List<CampaignDto> myCampaigns() { return campaigns.mine(); }

    /** multipart: "data" (JSON CampaignRequest) + "avatar" + "images". */
    @PostMapping(value = "/campaigns", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CampaignDto createCampaign(@RequestPart("data") CampaignRequest r, @RequestPart(value = "avatar", required = false) MultipartFile avatar,
                                      @RequestPart(value = "images", required = false) List<MultipartFile> images) { return campaigns.create(r, avatar, images); }

    @PutMapping("/campaigns/{id}") public CampaignDto updateCampaign(@PathVariable Long id, @RequestBody CampaignRequest r) { return campaigns.update(id, r); }
    @PutMapping(value = "/campaigns/{id}/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CampaignDto avatar(@PathVariable Long id, @RequestPart("file") MultipartFile f) { return campaigns.setAvatar(id, f); }
    @PostMapping(value = "/campaigns/{id}/images", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public CampaignDto images(@PathVariable Long id, @RequestPart("images") List<MultipartFile> files) { return campaigns.addImages(id, files); }
    @DeleteMapping("/campaigns/{id}/images/{mediaId}") public MessageResponse removeImage(@PathVariable Long id, @PathVariable Long mediaId) {
        campaigns.removeImage(id, mediaId);
        return new MessageResponse("Đã xóa ảnh.");
    }
    @PostMapping("/campaigns/{id}/end") public CampaignDto end(@PathVariable Long id) { return campaigns.end(id); }
    @PostMapping("/campaigns/{id}/extend") public CampaignDto extend(@PathVariable Long id, @Valid @RequestBody ExtendRequest r) { return campaigns.extend(id, r.endDate()); }
}
