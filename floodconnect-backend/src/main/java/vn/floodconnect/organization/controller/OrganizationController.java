package vn.floodconnect.organization.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.organization.dto.OrganizationDtos.OperatingStatusRequest;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgUpdateRequest;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.organization.service.OrgService;
import java.util.List;

/** Tổ chức cứu hộ: hồ sơ, trạng thái hoạt động, gửi lại hồ sơ (role RESCUE_ORG). */
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrganizationController {
    private final OrgService org;

    // ---- hồ sơ & trạng thái ----
    @GetMapping("/me") public OrgInfo me() { return org.me(); }
    /** Tổ chức đã duyệt sửa thông tin: mô tả, trang web, địa chỉ, ghim trụ sở (không gồm tên, loại tổ chức, giấy tờ). */
    @PutMapping(value = "/me", consumes = MediaType.APPLICATION_JSON_VALUE)
    public OrgInfo update(@Valid @RequestBody OrgUpdateRequest r) { return org.update(r); }
    @PutMapping("/me/operating-status") public OrgInfo status(@Valid @RequestBody OperatingStatusRequest r) { return org.setOperatingStatus(r); }
    /** multipart: "data" (JSON) + tệp mới theo từng ô như khi đăng ký ("documents", "idFront", "idBack", "evidence"); ô không gửi tệp thì giữ tệp cũ. */
    @PutMapping(value = "/me/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public OrgInfo resubmit(@RequestPart("data") OrgService.ResubmitRequest r,
                            @RequestPart(value = "documents", required = false) List<MultipartFile> documents,
                            @RequestPart(value = "idFront", required = false) List<MultipartFile> idFront,
                            @RequestPart(value = "idBack", required = false) List<MultipartFile> idBack,
                            @RequestPart(value = "evidence", required = false) List<MultipartFile> evidence) {
        return org.resubmit(r, OrgDocumentService.Uploads.of(documents, idFront, idBack, evidence));
    }
}
