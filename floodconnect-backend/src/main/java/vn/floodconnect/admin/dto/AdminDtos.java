package vn.floodconnect.admin.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.entity.CampaignReportReason;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.ReviewDecision;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.entity.PostReportReason;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import vn.floodconnect.user.dto.UserDtos.UserSummary;
import vn.floodconnect.user.entity.UserStatus;

public final class AdminDtos {
    private AdminDtos() {}

    public record ReviewRequest(@NotNull ReviewDecision decision, String reason) {}
    public record LockRequest(@NotBlank(message = "Vui lòng nhập lý do khóa tài khoản.") String reason, @Min(1) Integer months) {}
    public record ModerationRequest(@NotNull ModerationDecision decision, String reason, String description) {}

    // ---------------- Quản lý tài khoản Admin (chỉ Admin gốc) ----------------
    /** Admin gốc tạo Admin mới: không nhập mật khẩu, người đó tự đặt bằng mã gửi qua email. */
    public record CreateAdminRequest(@NotBlank(message = "Vui lòng nhập họ tên.") String fullName,
                                     @NotBlank(message = "Email không hợp lệ.") @Email(message = "Email không hợp lệ.") String email,
                                     @NotBlank(message = "Vui lòng nhập số điện thoại.") String phone) {}
    public record DisableAdminRequest(@NotBlank(message = "Vui lòng nhập lý do vô hiệu hóa.")
                                      @Size(max = 500, message = "Lý do tối đa 500 ký tự.") String reason) {}
    /**
     * Một dòng trong danh sách Admin. state: ACTIVE (đang hoạt động) | INVITED (chưa đặt mật khẩu lần đầu) | DISABLED (bị vô hiệu hóa).
     * disabledReason / disabledAt: lần vô hiệu hóa đang có hiệu lực (null nếu không bị vô hiệu hóa).
     */
    public record AdminAccountRow(Long id, String fullName, String email, String phone, boolean rootAdmin, String state,
                                  Instant createdAt, String disabledReason, Instant disabledAt) {}
    public record OrgAdminRow(Long orgId, String name, String representative, String phone, String email,
                              OrgType orgType, Instant createdAt, OrgStatus status,
                              long removedPosts, long stoppedCampaigns, long totalViolations, Instant lockedAt,
                              /* khu vực trụ sở (Phường/Xã, Tỉnh) và trạng thái Sẵn sàng/Đang bận/Không hoạt động: tab "Đang hoạt động" */
                              String area, OperatingStatus operatingStatus) {}
    public record ReviewDto(Long id, String adminName, ReviewDecision decision, String reason, Instant createdAt) {}
    public record ViolationItem(Long id, String title, String reason, Instant handledAt, String extra) {}
    public record OrgAdminDetail(OrgInfo organization, UserProfile representative, List<ReviewDto> reviews,
                                 List<ViolationItem> violatedPosts, List<ViolationItem> violatedCampaigns) {}
    public record CitizenAdminRow(Long userId, String fullName, String email, UserStatus status,
                                  long removedPosts, Instant lastViolationAt, Instant lockedUntil,
                                  /* tab "Tất cả người dân": tìm theo cả số điện thoại; tài khoản chưa xác thực email được đánh dấu */
                                  String phone, boolean emailVerified) {}
    public record CitizenAdminDetail(UserProfile user, long removedPosts, List<ViolationItem> violations) {}
    public record ReportedPostRow(PostDto post, long pendingReports) {}
    public record PostReportDto(Long id, Long postId, String postContent, UserSummary reporter,
                                PostReportReason reason, String description, ReportStatus status, Instant createdAt) {}
    public record PostReportDetail(PostDto post, List<PostReportDto> reports, Map<String, Long> countByReason) {}
    public record ReportedCampaignRow(CampaignDto campaign, long pendingReports) {}
    public record CampaignReportDto(Long id, Long campaignId, String campaignName, UserSummary reporter,
                                    CampaignReportReason reason, String description, ReportStatus status, Instant createdAt) {}
    public record CampaignReportDetail(CampaignDto campaign, List<CampaignReportDto> reports) {}

    // ---------------- Thống kê theo thời gian ----------------
    /** Một ngày trên biểu đồ: số ca được gửi và số ca hoàn thành trong ngày đó (giờ Việt Nam). */
    public record DayCount(LocalDate date, long created, long completed) {}
    /** Thời gian trung bình (phút, null khi chưa có dữ liệu) và số ca/lần dùng để tính. */
    public record DurationStat(Double avgMinutes, long count) {}
    /** Biểu đồ N ngày gần nhất (đủ mọi ngày, ngày không có ca = 0) và ba chỉ số thời gian cho cùng khoảng đó. */
    public record SosTimeline(int days, List<DayCount> points, DurationStat untilAssigned, DurationStat orgResponse,
                              DurationStat untilCompleted) {}
    /** Số liệu toàn hệ thống cho các ô KPI: ca SOS đang mở, tổng tiền ủng hộ thành công. */
    /** pendingReportedPosts / pendingReportedCampaigns: số bài / chiến dịch đang có báo cáo chờ xử lý (đếm trong CSDL). */
    public record StatsOverview(long openSos, long totalRaised, long pendingReportedPosts, long pendingReportedCampaigns) {}
}
