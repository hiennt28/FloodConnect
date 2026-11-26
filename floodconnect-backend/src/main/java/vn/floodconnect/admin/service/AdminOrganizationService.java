package vn.floodconnect.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.admin.dto.AdminDtos.LockRequest;
import vn.floodconnect.admin.dto.AdminDtos.OrgAdminDetail;
import vn.floodconnect.admin.dto.AdminDtos.OrgAdminRow;
import vn.floodconnect.admin.dto.AdminDtos.ReviewDto;
import vn.floodconnect.admin.dto.AdminDtos.ReviewRequest;
import vn.floodconnect.admin.dto.AdminDtos.ViolationItem;
import vn.floodconnect.auth.service.MailService;
import vn.floodconnect.campaign.repository.CampaignModerationRepository;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.organization.service.OrgMapper;
import vn.floodconnect.user.service.UserMapper;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrganizationReview;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.entity.ReviewDecision;
import vn.floodconnect.organization.repository.OrganizationReviewRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.post.repository.PostModerationRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.service.AccountLockService;
import java.time.*;
import java.util.*;

/** Admin quản lý tổ chức cứu hộ: duyệt/từ chối hồ sơ, xem vi phạm, khóa/mở khóa. */
@Service
@RequiredArgsConstructor
public class AdminOrganizationService {
    private final RescueOrganizationRepository orgs;
    private final OrganizationReviewRepository orgReviews;
    private final PostModerationRepository postModerations;
    private final CampaignReportRepository campaignReports;
    private final CampaignModerationRepository campaignModerations;
    private final AccountLockService lockService;
    private final NotificationPublisher notifications;
    private final MailService mail;
    private final CurrentUser current;
    private final OrgMapper orgMapper;
    private final UserMapper userMapper;

    // =====================================================================
    //  Tổ chức
    // =====================================================================
    /**
     * Dòng danh sách cho nhiều tổ chức một lần: số bài bị xóa và số chiến dịch bị dừng tra GỘP (hai truy vấn cho cả danh sách)
     * thay vì hai truy vấn cho mỗi tổ chức (lỗi N+1).
     */
    private List<OrgAdminRow> rows(List<RescueOrganization> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, Long> posts = new HashMap<>(), camps = new HashMap<>();
        for (Object[] r : postModerations.countByAuthors(list.stream().map(o -> o.getUser().getId()).toList(), ModerationDecision.REMOVE))
            posts.put((Long) r[0], ((Number) r[1]).longValue());
        for (Object[] r : campaignModerations.countByOrgs(list.stream().map(RescueOrganization::getId).toList(), ModerationDecision.REMOVE))
            camps.put((Long) r[0], ((Number) r[1]).longValue());
        return list.stream().map(o -> {
            long p = posts.getOrDefault(o.getUser().getId(), 0L), c = camps.getOrDefault(o.getId(), 0L);
            String area = String.join(", ", java.util.stream.Stream.of(o.getWard(), o.getProvince()).filter(Objects::nonNull).toList());
            return new OrgAdminRow(o.getId(), o.getName(), o.getUser().getFullName(), o.getUser().getPhone(), o.getUser().getEmail(),
                    o.getOrgType(), o.getCreatedAt(), o.getStatus(), p, c, p + c, o.getLockedAt(), area, o.getOperatingStatus());
        }).toList();
    }

    /** filter = NEW (chờ duyệt, cũ trước) | ACTIVE (mọi tổ chức đang hoạt động, theo tên) | REJECTED | VIOLATING (có vi phạm chưa khóa, nhiều trước) | LOCKED (khóa lâu nhất trước). */
    @Transactional(readOnly = true)
    public List<OrgAdminRow> listOrgs(String filter) {
        String f = filter == null ? "NEW" : filter.toUpperCase();
        return switch (f) {
            case "LOCKED" -> rows(orgs.findByStatusOrderByLockedAtAsc(OrgStatus.LOCKED));
            // Tab "Đang hoạt động": mọi tổ chức đã duyệt chưa bị khóa (kể cả không có vi phạm), xếp theo tên
            case "ACTIVE" -> rows(orgs.findByStatus(OrgStatus.ACTIVE)).stream()
                    .sorted(Comparator.comparing(OrgAdminRow::name, String.CASE_INSENSITIVE_ORDER)).toList();
            case "VIOLATING" -> rows(orgs.findByStatus(OrgStatus.ACTIVE)).stream().filter(r -> r.totalViolations() > 0)
                    .sorted(Comparator.comparingLong(OrgAdminRow::totalViolations).reversed()).toList();
            case "REJECTED" -> rows(orgs.findByStatus(OrgStatus.REJECTED));
            default -> rows(orgs.findByStatusOrderByCreatedAtAsc(OrgStatus.PENDING));
        };
    }

    private RescueOrganization org(Long id) {
        return orgs.findById(id).orElseThrow(() -> ApiException.notFound("Tổ chức không tồn tại."));
    }

    @Transactional(readOnly = true)
    public OrgAdminDetail orgDetail(Long id) {
        RescueOrganization o = org(id);
        List<ReviewDto> reviews = orgReviews.findByOrgIdOrderByCreatedAtDesc(id).stream()
                .map(r -> new ReviewDto(r.getId(), r.getAdmin().getFullName(), r.getDecision(), r.getReason(), r.getCreatedAt())).toList();
        List<ViolationItem> vp = postModerations.findByAuthor(o.getUser().getId(), ModerationDecision.REMOVE).stream()
                .map(m -> new ViolationItem(m.getPost().getId(), m.getPost().getContent(), m.getReason(), m.getCreatedAt(), null)).toList();
        List<ViolationItem> vc = campaignModerations.findByOrg(id, ModerationDecision.REMOVE).stream()
                .map(m -> new ViolationItem(m.getCampaign().getId(), m.getCampaign().getName(), m.getReason(), m.getCreatedAt(),
                        m.getCampaign().getStartDate() + " → " + m.getCampaign().getEndDate()
                                + " | báo cáo: " + campaignReports.countByCampaignIdAndStatus(m.getCampaign().getId(), ReportStatus.COMPLETED))).toList();
        return new OrgAdminDetail(orgMapper.org(o, true), userMapper.profile(o.getUser()), reviews, vp, vc);
    }

    /** Duyệt hoặc từ chối hồ sơ đăng ký (từ chối bắt buộc có lý do). */
    @Transactional
    public OrgInfo review(Long id, ReviewRequest r) {
        User admin = current.get();
        RescueOrganization o = org(id);
        if (o.getStatus() != OrgStatus.PENDING) throw ApiException.conflict("NOT_PENDING", "Hồ sơ không ở trạng thái chờ duyệt.");
        if (r.decision() == ReviewDecision.REJECTED && (r.reason() == null || r.reason().isBlank()))
            throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do từ chối để tổ chức biết cần bổ sung gì.");
        OrganizationReview rv = new OrganizationReview();
        rv.setOrg(o); rv.setAdmin(admin); rv.setDecision(r.decision()); rv.setReason(r.reason());
        orgReviews.save(rv);
        String msg;
        if (r.decision() == ReviewDecision.APPROVED) {
            o.setStatus(OrgStatus.ACTIVE); o.setRejectionReason(null);
            msg = "Hồ sơ tổ chức \"" + o.getName() + "\" đã được duyệt. Bạn có thể truy cập Rescue Dashboard.";
        } else {
            o.setStatus(OrgStatus.REJECTED); o.setRejectionReason(r.reason());
            msg = "Hồ sơ tổ chức \"" + o.getName() + "\" bị từ chối. Lý do: " + r.reason() + ". Bạn có thể chỉnh sửa và gửi lại.";
        }
        notifications.notify(o.getUser(), NotificationType.ORG_REVIEW, "Kết quả xét duyệt hồ sơ", msg, "ORG", o.getId());
        mail.send(o.getUser().getEmail(), "FloodConnect - Kết quả xét duyệt hồ sơ", msg);
        return orgMapper.org(o, true);
    }

    @Transactional
    public OrgInfo lockOrg(Long id, LockRequest r) {
        RescueOrganization o = org(id);
        if (o.getStatus() != OrgStatus.ACTIVE) throw ApiException.conflict("NOT_ACTIVE", "Chỉ khóa được tổ chức đang hoạt động.");
        lockService.lock(o.getUser(), current.get(), r.reason(), r.months() == null ? 1 : r.months());
        return orgMapper.org(o, false);
    }

    @Transactional
    public OrgInfo unlockOrg(Long id) {
        RescueOrganization o = org(id);
        if (o.getStatus() != OrgStatus.LOCKED) throw ApiException.conflict("NOT_LOCKED", "Tổ chức không bị khóa.");
        lockService.unlock(o.getUser(), current.get());
        return orgMapper.org(o, false);
    }
}
