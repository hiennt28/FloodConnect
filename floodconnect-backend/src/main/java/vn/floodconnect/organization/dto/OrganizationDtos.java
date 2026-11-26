package vn.floodconnect.organization.dto;

import jakarta.validation.constraints.*;
import java.util.List;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgDocSlot;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;

public final class OrganizationDtos {
    private OrganizationDtos() {}

    public record OperatingStatusRequest(@NotNull OperatingStatus status, Boolean confirm) {}
    /**
     * Tổ chức đã duyệt tự sửa thông tin (PUT /api/org/me). Không có tên, loại tổ chức và giấy tờ: các thông tin này gắn với hồ sơ
     * Admin đã duyệt nên không tự đổi được. Trụ sở = mã phường/xã + ghim trên bản đồ (phải nằm trong xã đã chọn), như lúc đăng ký.
     * websiteUrl để trống là xóa; description bắt buộc (AI Matching suy năng lực của tổ chức từ mô tả).
     */
    public record OrgUpdateRequest(
            String description,
            String websiteUrl,
            @NotBlank(message = "Vui lòng chọn Phường/Xã.") String wardCode,
            @NotNull(message = "Vui lòng đặt ghim vị trí trụ sở trên bản đồ.") Double latitude,
            @NotNull(message = "Vui lòng đặt ghim vị trí trụ sở trên bản đồ.") Double longitude,
            @NotBlank(message = "Vui lòng nhập thôn/xóm, số nhà, đường.") String streetAddress,
            /* Năng lực tiếp nhận, bắt buộc: AI Matching dùng cho điểm tải W và vùng phục vụ của điểm vị trí L */
            @NotNull(message = "Vui lòng nhập số ca xử lý cùng lúc.")
            @Min(value = 1, message = "Số ca xử lý cùng lúc từ 1 đến 50.") @Max(value = 50, message = "Số ca xử lý cùng lúc từ 1 đến 50.")
            Integer maxConcurrentCases,
            @NotNull(message = "Vui lòng nhập bán kính hoạt động.")
            @DecimalMin(value = "1", message = "Bán kính hoạt động từ 1 đến 100 km.") @DecimalMax(value = "100", message = "Bán kính hoạt động từ 1 đến 100 km.")
            Double serviceRadiusKm) {}
    public record OrgInfo(Long id, String name, OrgType orgType, String province, String district, String ward,
                          String streetAddress, String description, Double latitude, Double longitude,
                          String serviceAreas, OrgStatus status, OperatingStatus operatingStatus,
                          String rejectionReason, List<DocInfo> documents, String websiteUrl,
                          Integer maxConcurrentCases, Double serviceRadiusKm) {}
    /** slot rỗng: tệp nộp trước khi hồ sơ được tách thành từng ô. Không có đường dẫn tệp: tải qua GET /api/org-docs/{id}/file (chỉ Admin và chính tổ chức). */
    public record DocInfo(Long id, OrgDocSlot slot, String docLabel, String fileType) {}
}
