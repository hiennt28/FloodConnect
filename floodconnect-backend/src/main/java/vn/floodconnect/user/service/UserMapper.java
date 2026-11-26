package vn.floodconnect.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import vn.floodconnect.user.dto.UserDtos.UserSummary;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.*;
import vn.floodconnect.organization.service.OrgMapper;

/** Chuyển User sang DTO (tóm tắt, hồ sơ). Phải gọi trong transaction (có quan hệ lazy). */
@Component
@RequiredArgsConstructor
public class UserMapper {
    private final RescueOrganizationRepository orgs;
    private final FollowRepository follows;
    private final OrgMapper orgMapper;

    // ---------- người dùng / tổ chức ----------
    public UserSummary user(User u) {
        if (u == null) return null;
        String orgName = u.getRole() == Role.RESCUE_ORG ? orgs.findByUserId(u.getId()).map(RescueOrganization::getName).orElse(null) : null;
        return new UserSummary(u.getId(), u.getFullName(), u.getAvatarUrl(), u.getRole(), orgName);
    }

    /** Tên tổ chức của các tài khoản tổ chức trong danh sách, tra một lần (dùng khi chuyển nhiều bản ghi, tránh N+1). */
    public Map<Long, String> orgNames(Collection<User> users) {
        List<Long> ids = users.stream().filter(u -> u != null && u.getRole() == Role.RESCUE_ORG).map(User::getId).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        Map<Long, String> out = new HashMap<>();
        for (RescueOrganization o : orgs.findByUserIdIn(ids)) out.put(o.getUser().getId(), o.getName());
        return out;
    }

    /** Như user(u), lấy tên tổ chức từ orgNames đã tra sẵn thay vì truy vấn riêng. */
    public UserSummary user(User u, Map<Long, String> orgNames) {
        if (u == null) return null;
        return new UserSummary(u.getId(), u.getFullName(), u.getAvatarUrl(), u.getRole(), u.getRole() == Role.RESCUE_ORG ? orgNames.get(u.getId()) : null);
    }

    public UserProfile profile(User u) {
        OrgInfo oi = u.getRole() == Role.RESCUE_ORG ? orgs.findByUserId(u.getId()).map(o -> orgMapper.org(o, true)).orElse(null) : null;
        return new UserProfile(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.getRole(), u.getAvatarUrl(),
                u.isEmailVerified(), u.getStatus(), u.isLocked(), u.getLockedUntil(), u.getGpsPermission(),
                u.getLatitude(), u.getLongitude(), oi, u.getCreatedAt(), u.isRootAdmin());
    }

    public boolean followedBy(Long viewerId, Long targetId) {
        return viewerId != null && follows.existsByFollowerIdAndFollowingId(viewerId, targetId);
    }
}
