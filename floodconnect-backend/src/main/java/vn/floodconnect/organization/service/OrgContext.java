package vn.floodconnect.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.user.entity.User;

/** Kiểm tra quyền hoạt động của tổ chức theo trạng thái tài khoản. */
@Component
@RequiredArgsConstructor
public class OrgContext {
    private final RescueOrganizationRepository orgs;

    public RescueOrganization of(User u) {
        return orgs.findByUserId(u.getId()).orElseThrow(() -> ApiException.forbidden("Tài khoản không phải tổ chức cứu hộ."));
    }

    /** Tổ chức đã được duyệt (ACTIVE). Tổ chức đang bị khóa bị từ chối. */
    public RescueOrganization active(User u) {
        RescueOrganization o = of(u);
        if (o.getStatus() == OrgStatus.LOCKED || u.isLocked())
            throw ApiException.forbidden("ACCOUNT_LOCKED", "Tổ chức đang bị khóa, chỉ được xử lý tiếp các ca đã tiếp nhận.");
        if (o.getStatus() != OrgStatus.ACTIVE)
            throw ApiException.forbidden("ORG_NOT_APPROVED", "Tổ chức chưa được Admin duyệt (trạng thái: " + o.getStatus() + ").");
        return o;
    }

    /** Dùng cho việc hoàn tất ca đang phụ trách: cho phép cả khi tổ chức đã bị khóa. */
    public RescueOrganization allowLocked(User u) {
        RescueOrganization o = of(u);
        if (o.getStatus() != OrgStatus.ACTIVE && o.getStatus() != OrgStatus.LOCKED)
            throw ApiException.forbidden("ORG_NOT_APPROVED", "Tổ chức chưa được Admin duyệt.");
        return o;
    }
}
