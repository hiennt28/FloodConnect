package vn.floodconnect.admin.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.admin.dto.AdminDtos.AdminAccountRow;
import vn.floodconnect.admin.dto.AdminDtos.CreateAdminRequest;
import vn.floodconnect.admin.dto.AdminDtos.DisableAdminRequest;
import vn.floodconnect.admin.service.AdminUserService;
import vn.floodconnect.admin.dto.AdminDtos.CampaignReportDetail;
import vn.floodconnect.admin.dto.AdminDtos.CampaignReportDto;
import vn.floodconnect.admin.dto.AdminDtos.CitizenAdminDetail;
import vn.floodconnect.admin.dto.AdminDtos.CitizenAdminRow;
import vn.floodconnect.admin.dto.AdminDtos.LockRequest;
import vn.floodconnect.admin.dto.AdminDtos.ModerationRequest;
import vn.floodconnect.admin.dto.AdminDtos.OrgAdminDetail;
import vn.floodconnect.admin.dto.AdminDtos.OrgAdminRow;
import vn.floodconnect.admin.dto.AdminDtos.PostReportDetail;
import vn.floodconnect.admin.dto.AdminDtos.PostReportDto;
import vn.floodconnect.admin.dto.AdminDtos.ReportedCampaignRow;
import vn.floodconnect.admin.dto.AdminDtos.ReportedPostRow;
import vn.floodconnect.admin.dto.AdminDtos.ReviewRequest;
import vn.floodconnect.admin.dto.AdminDtos.SosTimeline;
import vn.floodconnect.admin.dto.AdminDtos.StatsOverview;
import vn.floodconnect.admin.service.AdminOrganizationService;
import vn.floodconnect.admin.service.AdminAccountService;
import vn.floodconnect.admin.service.AdminModerationService;
import vn.floodconnect.admin.service.AdminStatsService;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.incident.dto.IncidentDtos.AdjustLevelRequest;
import vn.floodconnect.incident.dto.IncidentDtos.AdminCompleteRequest;
import vn.floodconnect.incident.dto.IncidentDtos.HeatPoint;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.dto.IncidentDtos.MarkInvalidRequest;
import vn.floodconnect.incident.dto.IncidentDtos.UnassignRequest;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.rescue.dto.RescueDtos.AdminSosMap;
import vn.floodconnect.rescue.dto.RescueDtos.AssignRequest;
import vn.floodconnect.rescue.dto.RescueDtos.CandidateOrgDto;
import vn.floodconnect.rescue.dto.RescueDtos.NearbyCaseDto;
import vn.floodconnect.rescue.service.AdminSosMapService;
import vn.floodconnect.rescue.service.SosAdminService;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** Toàn bộ chức năng của Admin (role ADMIN). */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminOrganizationService orgAdmin;
    private final AdminAccountService accountAdmin;
    private final AdminModerationService moderation;
    private final AdminStatsService stats;
    private final SosAdminService sos;
    private final AdminSosMapService sosMap;
    private final AdminUserService adminUsers;

    // ---------------- SOS ----------------
    /** status = ACTIVE | ENDED | ALL | một trạng thái; sortBy = level | time ; dir = asc | desc ; level=KHONG: các ca chờ Admin xác minh. */
    @GetMapping("/sos")
    public PageResponse<IncidentDto> sosList(@RequestParam(required = false) String status, @RequestParam(required = false) DangerLevel level,
                                             @RequestParam(required = false) String sortBy, @RequestParam(required = false) String dir,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return sos.list(status, level, sortBy, dir, page, size);
    }
    @GetMapping("/sos/heatmap") public List<HeatPoint> heatmap() { return sos.heatmap(); }
    /** Bản đồ điều phối: status = ACTIVE | ALL | một trạng thái; ca đã kết thúc chỉ lấy trong days ngày (1..90, mặc định 7); tối đa app.map.max-markers ca. */
    @GetMapping("/sos/map")
    public AdminSosMap sosMap(@RequestParam(defaultValue = "ACTIVE") String status, @RequestParam(required = false) DangerLevel level,
                              @RequestParam(required = false) Integer days) { return sosMap.cases(status, level, days); }
    @GetMapping("/sos/{id}") public IncidentDto sosDetail(@PathVariable Long id) { return sos.detail(id); }
    /** Đổi mức ca Chờ xử lý. Ca nhãn KHONG (chờ xác minh) bắt buộc kèm note = ghi chú xác minh. */
    @PostMapping("/sos/{id}/adjust-level") public IncidentDto adjust(@PathVariable Long id, @Valid @RequestBody AdjustLevelRequest r) { return sos.adjustLevel(id, r.newLevel(), r.note()); }
    /** Admin đã liên hệ người gửi, giữ nguyên mức: gỡ cảnh báo "nên xác minh" (ca đang hoạt động, mức khác KHONG). */
    @PostMapping("/sos/{id}/confirm-verification") public IncidentDto confirmVerification(@PathVariable Long id) { return sos.confirmVerification(id); }
    /** Admin kết luận ca không phải yêu cầu cứu hộ (chỉ ca Chờ xử lý): đóng ca, báo người gửi. */
    @PostMapping("/sos/{id}/mark-invalid") public IncidentDto markInvalid(@PathVariable Long id, @Valid @RequestBody MarkInvalidRequest r) { return sos.markInvalid(id, r.reason()); }
    /** Admin xác nhận hoàn thành ca Đã tiếp nhận sau khi liên hệ (người dân đã an toàn / tổ chức quên bấm); bắt buộc lý do. */
    @PostMapping("/sos/{id}/complete") public IncidentDto complete(@PathVariable Long id, @Valid @RequestBody AdminCompleteRequest r) { return sos.complete(id, r.reason()); }
    @GetMapping("/sos/{id}/candidates") public List<CandidateOrgDto> candidates(@PathVariable Long id) { return sos.candidates(id); }
    /** Ca khác trong 500 m: đang hoạt động, hoặc đã hoàn thành và gửi trong khoảng 24 giờ trước ca này trở về sau. Tính tại thời điểm gọi. */
    @GetMapping("/sos/{id}/nearby") public List<NearbyCaseDto> nearby(@PathVariable Long id) { return sos.nearby(id); }
    /** Chỉ định lần đầu hoặc điều phối lại (tổ chức từ chối / không phản hồi sau 5 phút). */
    @PostMapping("/sos/{id}/assign") public IncidentDto assign(@PathVariable Long id, @Valid @RequestBody AssignRequest r) { return sos.assign(id, r.orgId()); }
    /** Hủy phân công ca Đã phân công (tổ chức chưa xác nhận, kể cả chưa hết 5 phút): ca về Chờ xử lý; bắt buộc lý do. */
    @PostMapping("/sos/{id}/unassign") public IncidentDto unassign(@PathVariable Long id, @Valid @RequestBody UnassignRequest r) { return sos.unassign(id, r.reason()); }

    // ---------------- Tổ chức ----------------
    /** filter = NEW | ACTIVE | VIOLATING | LOCKED | REJECTED */
    @GetMapping("/orgs") public List<OrgAdminRow> orgs(@RequestParam(defaultValue = "NEW") String filter) { return orgAdmin.listOrgs(filter); }
    @GetMapping("/orgs/{id}") public OrgAdminDetail orgDetail(@PathVariable Long id) { return orgAdmin.orgDetail(id); }
    @PostMapping("/orgs/{id}/review") public OrgInfo review(@PathVariable Long id, @Valid @RequestBody ReviewRequest r) { return orgAdmin.review(id, r); }
    @PostMapping("/orgs/{id}/lock") public OrgInfo lockOrg(@PathVariable Long id, @Valid @RequestBody LockRequest r) { return orgAdmin.lockOrg(id, r); }
    @PostMapping("/orgs/{id}/unlock") public OrgInfo unlockOrg(@PathVariable Long id) { return orgAdmin.unlockOrg(id); }

    // ---------------- Tài khoản Admin (chỉ Admin gốc; Admin thường nhận 403 ROOT_ADMIN_ONLY) ----------------
    @GetMapping("/admins") public List<AdminAccountRow> admins() { return adminUsers.list(); }
    /** Tạo Admin mới: họ tên, email, SĐT; người đó tự đặt mật khẩu bằng mã gửi qua email. */
    @PostMapping("/admins") public AdminAccountRow createAdmin(@Valid @RequestBody CreateAdminRequest r) { return adminUsers.create(r); }
    @PostMapping("/admins/{id}/resend-invite") public AdminAccountRow resendInvite(@PathVariable Long id) { return adminUsers.resendInvite(id); }
    @PostMapping("/admins/{id}/disable") public AdminAccountRow disableAdmin(@PathVariable Long id, @Valid @RequestBody DisableAdminRequest r) { return adminUsers.disable(id, r.reason()); }
    @PostMapping("/admins/{id}/enable") public AdminAccountRow enableAdmin(@PathVariable Long id) { return adminUsers.enable(id); }

    // ---------------- Người dân ----------------
    /** filter = VIOLATING | LOCKED */
    @GetMapping("/citizens") public List<CitizenAdminRow> citizens(@RequestParam(defaultValue = "VIOLATING") String filter) { return accountAdmin.listCitizens(filter); }
    /** Tab "Tất cả người dân": tìm theo tên (không dấu), email hoặc số điện thoại; q rỗng = tất cả; phân trang. */
    @GetMapping("/citizens/search")
    public PageResponse<CitizenAdminRow> searchCitizens(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "0") int page,
                                                        @RequestParam(defaultValue = "20") int size) { return accountAdmin.searchCitizens(q, page, size); }
    @GetMapping("/citizens/{id}") public CitizenAdminDetail citizen(@PathVariable Long id) { return accountAdmin.citizenDetail(id); }
    @PostMapping("/citizens/{id}/lock") public UserProfile lock(@PathVariable Long id, @Valid @RequestBody LockRequest r) { return accountAdmin.lockCitizen(id, r); }
    @PostMapping("/citizens/{id}/unlock") public UserProfile unlock(@PathVariable Long id) { return accountAdmin.unlockCitizen(id); }

    // ---------------- Báo cáo bài đăng ----------------
    @GetMapping("/post-reports/posts")
    public PageResponse<ReportedPostRow> reportedPosts(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return moderation.reportedPosts(page, size); }
    @GetMapping("/post-reports")
    public PageResponse<PostReportDto> postReports(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return moderation.allPostReports(page, size); }
    @GetMapping("/posts/{id}/reports") public PostReportDetail postReportDetail(@PathVariable Long id) { return moderation.postReportDetail(id); }
    @PostMapping("/posts/{id}/moderate") public MessageResponse moderatePost(@PathVariable Long id, @Valid @RequestBody ModerationRequest r) { return moderation.moderatePost(id, r); }

    // ---------------- Báo cáo chiến dịch ----------------
    @GetMapping("/campaign-reports/campaigns")
    public PageResponse<ReportedCampaignRow> reportedCampaigns(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return moderation.reportedCampaigns(page, size); }
    @GetMapping("/campaign-reports")
    public PageResponse<CampaignReportDto> campaignReports(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return moderation.allCampaignReports(page, size); }
    @GetMapping("/campaigns/{id}/reports") public CampaignReportDetail campaignReportDetail(@PathVariable Long id) { return moderation.campaignReportDetail(id); }
    @PostMapping("/campaigns/{id}/moderate") public MessageResponse moderateCampaign(@PathVariable Long id, @Valid @RequestBody ModerationRequest r) { return moderation.moderateCampaign(id, r); }

    // ---------------- Thống kê ----------------
    @GetMapping("/stats/citizens") public Map<String, Long> citizenStats() { return stats.citizenStats(); }
    @GetMapping("/stats/sos")
    public Map<String, Long> sosStats(@RequestParam(defaultValue = "day") String period,
                                      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) { return stats.sosStats(period, date); }
    @GetMapping("/stats/sos-status")
    public Map<String, Long> sosStatusStats(@RequestParam(defaultValue = "day") String period,
                                            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) { return stats.sosStatusStats(period, date); }
    /** days: số ngày gần nhất (giao diện dùng 7, 30 hoặc 90; giá trị khác được đưa về khoảng 1–90). */
    @GetMapping("/stats/sos-timeline") public SosTimeline sosTimeline(@RequestParam(defaultValue = "30") int days) { return stats.sosTimeline(days); }
    @GetMapping("/stats/overview") public StatsOverview overview() { return stats.overview(); }
    @GetMapping("/stats/orgs") public Map<String, Object> orgStats() { return stats.orgStats(); }
    @GetMapping("/stats/top-orgs") public List<Map<String, Object>> topOrgs() { return stats.topOrgs(); }
    @GetMapping("/stats/top-donors") public List<Map<String, Object>> topDonors() { return stats.topDonors(); }
}
