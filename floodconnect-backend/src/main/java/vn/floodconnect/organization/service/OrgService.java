package vn.floodconnect.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.dto.OrganizationDtos.OperatingStatusRequest;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgUpdateRequest;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.service.RescueService;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

/** Chức năng riêng của tổ chức: hồ sơ, gửi lại hồ sơ khi bị từ chối, trạng thái hoạt động. */
@Service
@RequiredArgsConstructor
public class OrgService {
    private final RescueOrganizationRepository orgs;
    private final IncidentRepository incidents;
    private final CurrentUser current;
    private final OrgContext orgContext;
    private final OrgDocumentService orgDocuments;
    private final NotificationPublisher notifications;
    private final OrgMapper orgMapper;
    private final RescueService rescueService;
    private final GeoService geo;

    /**
     * wardCode + latitude/longitude: mã phường/xã và ghim trụ sở trên bản đồ, gửi cùng nhau (trống: giữ địa chỉ, tọa độ cũ).
     * Server lấy tên tỉnh, tên xã theo mã và kiểm tra ghim nằm trong xã đó.
     */
    public record ResubmitRequest(String orgName, OrgType orgType, String wardCode, Double latitude, Double longitude,
                                  String streetAddress, String description, String serviceAreas, String websiteUrl,
                                  Integer maxConcurrentCases, Double serviceRadiusKm) {}

    @Transactional(readOnly = true)
    public OrgInfo me() { return orgMapper.org(orgContext.of(current.get()), true); }

    /** Mô tả hoạt động tối đa ngần này ký tự khi tổ chức tự sửa. */

    /**
     * Tổ chức đã duyệt (đang hoạt động, không bị khóa) tự sửa thông tin: mô tả, trang web, địa chỉ và ghim trụ sở.
     * Tên, loại tổ chức và giấy tờ không sửa được (gắn với hồ sơ Admin đã duyệt). Ghim trụ sở phải nằm trong phường/xã đã chọn;
     * tên tỉnh, xã lấy từ bảng địa giới theo mã xã. Dời trụ sở hoặc đổi bán kính hoạt động thì AI Matching, thông báo ca mới
     * và danh sách ca đề xuất (theo bán kính tổ chức tự khai) dùng ngay giá trị mới.
     */
    @Transactional
    public OrgInfo update(OrgUpdateRequest r) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u); // tổ chức bị khóa không tự sửa thông tin
        Map<String, String> e = new LinkedHashMap<>();
        String street = r.streetAddress() == null ? "" : r.streetAddress().trim();
        if (street.isEmpty()) e.put("address", "Vui lòng nhập thôn/xóm, số nhà, đường.");
        else if (street.length() > Validators.TEXT_MAX) e.put("address", Validators.tooLongMsg("Thôn/xóm, số nhà, đường", Validators.TEXT_MAX));
        // Mô tả bắt buộc, không xóa trắng được: AI Matching suy năng lực của tổ chức từ đây (điểm C)
        String descriptionError = Validators.orgDescriptionError(r.description());
        if (descriptionError != null) e.put("description", descriptionError);
        String description = r.description() == null ? null : r.description().trim();
        String website = r.websiteUrl() == null || r.websiteUrl().isBlank() ? null : r.websiteUrl().trim();
        if (website != null && !Validators.urlOk(website)) e.put("websiteUrl", Validators.URL_MSG);
        // Bảng địa giới chưa nạp (503) thì để lỗi đi thẳng ra ngoài, giống lúc đăng ký
        WardCenter w = null;
        try { w = geo.center(r.wardCode() == null ? null : r.wardCode().trim()); }
        catch (ApiException ex) { if (ex.getStatus().is5xxServerError()) throw ex; e.put("address", ex.getMessage()); }
        if (w != null) {
            try { geo.requireInWard(r.latitude(), r.longitude(), w); }
            catch (ApiException ex) { if (ex.getStatus().is5xxServerError()) throw ex; e.put("location", ex.getMessage()); }
        }
        if (!e.isEmpty()) throw ApiException.validation(e);

        o.setProvince(w.provinceName()); o.setDistrict(null); o.setWard(w.name()); o.setStreetAddress(street);
        o.setLatitude(r.latitude()); o.setLongitude(r.longitude());
        o.setServiceAreas(w.provinceName()); // như lúc đăng ký: khu vực phục vụ = tỉnh của trụ sở (một phần văn bản cho AI Matching)
        o.setDescription(description);
        o.setWebsiteUrl(website);
        o.setMaxConcurrentCases(r.maxConcurrentCases()); o.setServiceRadiusKm(r.serviceRadiusKm());
        return orgMapper.org(o, true);
    }

    /** Quản lý trạng thái tổ chức: Sẵn sàng / Đang bận / Không hoạt động. */
    @Transactional
    public OrgInfo setOperatingStatus(OperatingStatusRequest r) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        if (r.status() == OperatingStatus.INACTIVE && o.getOperatingStatus() != OperatingStatus.INACTIVE
                && incidents.countByAssignedOrgIdAndStatusIn(o.getId(), EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED)) > 0
                && !Boolean.TRUE.equals(r.confirm()))
            throw ApiException.conflict("CONFIRM_REQUIRED", "Tổ chức đang có ca cứu hộ. Gửi lại với confirm=true để xác nhận chuyển sang Không hoạt động.");
        o.setOperatingStatus(r.status());
        if (r.status() == OperatingStatus.INACTIVE) rescueService.releaseAssigned(o, "Tổ chức chuyển sang Không hoạt động");
        return orgMapper.org(o, false);
    }

    /**
     * Tổ chức bị từ chối chỉnh sửa hồ sơ và gửi lại để Admin xét duyệt lần nữa. Trường để trống giữ giá trị cũ (riêng trang web:
     * gửi chuỗi rỗng là xóa). Tệp hồ sơ thay theo từng ô, xem OrgDocumentService.resubmit.
     */
    @Transactional
    public OrgInfo resubmit(ResubmitRequest r, OrgDocumentService.Uploads uploads) {
        User u = current.get();
        RescueOrganization o = orgContext.of(u);
        if (o.getStatus() != OrgStatus.REJECTED) throw ApiException.conflict("Chỉ hồ sơ bị từ chối mới được chỉnh sửa và gửi lại.");
        OrgType oldType = o.getOrgType();
        if (Validators.tooLong(r.orgName(), Validators.TEXT_MAX))
            throw ApiException.validation(Map.of("orgName", Validators.tooLongMsg("Tên tổ chức", Validators.TEXT_MAX)));
        if (Validators.tooLong(r.streetAddress(), Validators.TEXT_MAX))
            throw ApiException.validation(Map.of("address", Validators.tooLongMsg("Thôn/xóm, số nhà, đường", Validators.TEXT_MAX)));
        // Mô tả bắt buộc (AI Matching suy năng lực từ đây): không gửi thì giữ mô tả cũ, nhưng kết quả cuối không được trống
        String description = r.description() != null ? r.description() : o.getDescription();
        String descriptionError = Validators.orgDescriptionError(description);
        if (descriptionError != null) throw ApiException.validation(Map.of("description", descriptionError));
        if (r.orgName() != null && !r.orgName().isBlank()) o.setName(r.orgName().trim());
        if (r.orgType() != null) o.setOrgType(r.orgType());
        if (r.websiteUrl() != null) {
            String w = r.websiteUrl().isBlank() ? null : r.websiteUrl().trim();
            if (w != null && !Validators.urlOk(w)) throw ApiException.validation(Map.of("websiteUrl", Validators.URL_MSG));
            o.setWebsiteUrl(w);
        }
        if (r.wardCode() != null && !r.wardCode().isBlank()) {
            WardCenter w = geo.center(r.wardCode().trim());
            if (r.latitude() == null || r.longitude() == null)
                throw ApiException.validation(Map.of("location", "Vui lòng đặt ghim vị trí trụ sở trên bản đồ."));
            try { geo.requireInWard(r.latitude(), r.longitude(), w); }
            catch (ApiException ex) { if (ex.getStatus().is5xxServerError()) throw ex; throw ApiException.validation(Map.of("location", ex.getMessage())); }
            o.setProvince(w.provinceName()); o.setDistrict(null); o.setWard(w.name());
            o.setLatitude(r.latitude()); o.setLongitude(r.longitude());
        }
        if (r.streetAddress() != null && !r.streetAddress().isBlank()) o.setStreetAddress(r.streetAddress().trim());
        o.setDescription(description.trim());
        if (r.serviceAreas() != null) o.setServiceAreas(r.serviceAreas());
        Map<String, String> capacityErrors = capacityErrors(r.maxConcurrentCases(), r.serviceRadiusKm());
        if (!capacityErrors.isEmpty()) throw ApiException.validation(capacityErrors);
        if (r.maxConcurrentCases() != null) o.setMaxConcurrentCases(r.maxConcurrentCases());
        if (r.serviceRadiusKm() != null) o.setServiceRadiusKm(r.serviceRadiusKm());
        orgDocuments.resubmit(o, o.getOrgType() != oldType, uploads);
        o.setStatus(OrgStatus.PENDING);
        o.setRejectionReason(null);
        notifications.notifyAdmins(NotificationType.ORG_REVIEW, "Tổ chức gửi lại hồ sơ",
                "Tổ chức " + o.getName() + " đã chỉnh sửa và gửi lại hồ sơ.", "ORG", o.getId());
        return orgMapper.org(o, true);
    }

    /** Kiểm tra số ca xử lý cùng lúc và bán kính hoạt động khi gửi lại hồ sơ (null = giữ giá trị cũ). */
    private static Map<String, String> capacityErrors(Integer cases, Double radiusKm) {
        Map<String, String> e = new LinkedHashMap<>();
        if (cases != null && (cases < RescueOrganization.MIN_CONCURRENT_CASES || cases > RescueOrganization.MAX_CONCURRENT_CASES))
            e.put("maxConcurrentCases", "Số ca xử lý cùng lúc từ 1 đến 50.");
        if (radiusKm != null && (radiusKm < RescueOrganization.MIN_SERVICE_RADIUS_KM || radiusKm > RescueOrganization.MAX_SERVICE_RADIUS_KM))
            e.put("serviceRadiusKm", "Bán kính hoạt động từ 1 đến 100 km.");
        return e;
    }
}
