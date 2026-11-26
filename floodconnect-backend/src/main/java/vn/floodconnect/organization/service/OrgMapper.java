package vn.floodconnect.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.floodconnect.organization.dto.OrganizationDtos.DocInfo;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.rescue.dto.RescueDtos.OrgBrief;
import java.util.List;

/** Chuyển tổ chức cứu hộ sang DTO. Phải gọi trong transaction (có quan hệ lazy). */
@Component
@RequiredArgsConstructor
public class OrgMapper {
    private final OrganizationDocumentRepository docs;

    public OrgInfo org(RescueOrganization o, boolean withDocs) {
        if (o == null) return null;
        List<DocInfo> d = withDocs ? docs.findByOrgIdOrderByIdAsc(o.getId()).stream()
                .map(x -> new DocInfo(x.getId(), x.getSlot(), x.getDocLabel(), x.getFileType())).toList() : List.of();
        // Trang web là thông tin công khai (hiện trên hồ sơ tổ chức), khác với tệp hồ sơ pháp lý chỉ trả khi withDocs
        return new OrgInfo(o.getId(), o.getName(), o.getOrgType(), o.getProvince(), o.getDistrict(), o.getWard(),
                o.getStreetAddress(), o.getDescription(), o.getLatitude(), o.getLongitude(), o.getServiceAreas(),
                o.getStatus(), o.getOperatingStatus(), o.getRejectionReason(), d, o.getWebsiteUrl(),
                o.getMaxConcurrentCases(), o.getServiceRadiusKm());
    }

    /**
     * Thông tin tổ chức cho NGƯỜI NGOÀI (hồ sơ công khai, tìm kiếm, kể cả khách chưa đăng nhập): trụ sở chỉ tới Phường/Xã, Tỉnh.
     * Bỏ số nhà/đường và tọa độ chính xác: nhóm thiện nguyện thường lấy nhà riêng của trưởng nhóm làm trụ sở. Không có giấy tờ,
     * không có lý do từ chối. Chính tổ chức, Admin và người dân có ca đang được tổ chức xử lý vẫn nhận vị trí chính xác ở chỗ khác.
     */
    public OrgInfo orgPublic(RescueOrganization o) {
        if (o == null) return null;
        return new OrgInfo(o.getId(), o.getName(), o.getOrgType(), o.getProvince(), o.getDistrict(), o.getWard(),
                null, o.getDescription(), null, null, o.getServiceAreas(),
                o.getStatus(), o.getOperatingStatus(), null, List.of(), o.getWebsiteUrl(),
                o.getMaxConcurrentCases(), o.getServiceRadiusKm());
    }

    public OrgBrief orgBrief(RescueOrganization o) {
        if (o == null) return null;
        return new OrgBrief(o.getId(), o.getName(), o.getOrgType(), o.getOperatingStatus(), o.getLatitude(), o.getLongitude(),
                o.getUser().getPhone());
    }
}
