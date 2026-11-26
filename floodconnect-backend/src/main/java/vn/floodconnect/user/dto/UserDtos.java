package vn.floodconnect.user.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.user.entity.GpsPermission;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.UserStatus;

public final class UserDtos {
    private UserDtos() {}

    public record UpdateProfileRequest(@NotBlank String fullName, @NotBlank String phone) {}
    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword, @NotBlank String confirmPassword) {}
    public record GpsRequest(@NotNull GpsPermission permission, Double latitude, Double longitude) {}
    public record UserSummary(Long id, String fullName, String avatarUrl, Role role, String organizationName) {}
    public record UserProfile(Long id, String fullName, String email, String phone, Role role, String avatarUrl,
                              boolean emailVerified, UserStatus status, boolean locked, Instant lockedUntil,
                              GpsPermission gpsPermission, Double latitude, Double longitude, OrgInfo organization,
                              Instant createdAt,
                              /* Admin gốc: giao diện hiện mục "Quản lý Admin" (backend vẫn tự kiểm tra quyền) */
                              boolean rootAdmin) {}
    public record PublicProfile(UserSummary user, long followers, long following, boolean followedByMe,
                                OrgInfo organization, Instant joinedAt) {}
    public record SearchResult(UserSummary user, OrgInfo organization, boolean followedByMe) {}
}
