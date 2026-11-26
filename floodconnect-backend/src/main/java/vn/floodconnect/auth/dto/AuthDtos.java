package vn.floodconnect.auth.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.user.dto.UserDtos.UserProfile;

public final class AuthDtos {
    private AuthDtos() {}

    public record RegisterRequest(
            @NotBlank(message = "Vui lòng nhập họ tên.") String fullName,
            @NotBlank(message = "Email không hợp lệ.") @Email(message = "Email không hợp lệ.") String email,
            @NotBlank(message = "Vui lòng nhập số điện thoại.") String phone,
            @NotBlank(message = "Vui lòng nhập mật khẩu.") String password,
            @NotBlank(message = "Vui lòng xác nhận mật khẩu.") String confirmPassword) {}
    public record OrgRegisterRequest(
            @Valid @NotNull RegisterRequest account,
            @NotBlank(message = "Vui lòng nhập tên tổ chức.") String orgName,
            @NotNull(message = "Vui lòng chọn loại tổ chức.") OrgType orgType,
            /* Mã phường/xã chọn ở giao diện. Server tự lấy tên tỉnh, tên xã và tọa độ trụ sở (điểm đại diện của xã) từ bảng địa giới */
            @NotBlank(message = "Vui lòng chọn Phường/Xã.") String wardCode,
            @NotBlank(message = "Vui lòng nhập thôn/xóm, số nhà, đường.") String streetAddress,
            String description,
            /* Tọa độ trụ sở = ghim người đại diện đặt trên bản đồ (không gõ số); phải nằm trong xã đã chọn */
            @NotNull(message = "Vui lòng đặt ghim vị trí trụ sở trên bản đồ.") Double latitude,
            @NotNull(message = "Vui lòng đặt ghim vị trí trụ sở trên bản đồ.") Double longitude,
            String serviceAreas,
            String websiteUrl,
            /* Năng lực tiếp nhận, bắt buộc: AI Matching dùng cho điểm tải W và vùng phục vụ của điểm vị trí L */
            @NotNull(message = "Vui lòng nhập số ca xử lý cùng lúc.")
            @Min(value = 1, message = "Số ca xử lý cùng lúc từ 1 đến 50.") @Max(value = 50, message = "Số ca xử lý cùng lúc từ 1 đến 50.")
            Integer maxConcurrentCases,
            @NotNull(message = "Vui lòng nhập bán kính hoạt động.")
            @DecimalMin(value = "1", message = "Bán kính hoạt động từ 1 đến 100 km.") @DecimalMax(value = "100", message = "Bán kính hoạt động từ 1 đến 100 km.")
            Double serviceRadiusKm) {}
    public record LoginRequest(@NotBlank String identifier, @NotBlank String password) {}
    public record RefreshRequest(@NotBlank String refreshToken) {}
    public record EmailRequest(@NotBlank @Email String email) {}
    public record ResetPasswordRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 6, message = "Mã xác thực gồm 6 chữ số.") String code,
                                       @NotBlank String newPassword, @NotBlank String confirmPassword) {}
    public record VerifyEmailRequest(@NotBlank @Email String email, @NotBlank @Size(min = 6, max = 6, message = "Mã xác thực gồm 6 chữ số.") String code) {}
    public record AuthResponse(String accessToken, String refreshToken, String tokenType, long expiresInSeconds, UserProfile user) {}
}
