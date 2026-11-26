package vn.floodconnect.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.admin.dto.AdminDtos.CampaignReportDetail;
import vn.floodconnect.admin.dto.AdminDtos.CampaignReportDto;
import vn.floodconnect.admin.dto.AdminDtos.ModerationRequest;
import vn.floodconnect.admin.dto.AdminDtos.PostReportDetail;
import vn.floodconnect.admin.dto.AdminDtos.PostReportDto;
import vn.floodconnect.admin.dto.AdminDtos.ReportedCampaignRow;
import vn.floodconnect.admin.dto.AdminDtos.ReportedPostRow;
import vn.floodconnect.campaign.entity.CampaignModeration;
import vn.floodconnect.campaign.entity.CampaignReport;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.repository.CampaignModerationRepository;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.service.CampaignMapper;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.service.PostMapper;
import vn.floodconnect.user.service.UserMapper;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.post.entity.ContentStatus;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostModeration;
import vn.floodconnect.post.entity.PostReport;
import vn.floodconnect.post.repository.PostModerationRepository;
import vn.floodconnect.post.repository.PostReportRepository;
import vn.floodconnect.post.repository.PostRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Admin xử lý báo cáo bài đăng và chiến dịch (giữ/xóa bài, giữ/dừng chiến dịch). */
@Service
@RequiredArgsConstructor
public class AdminModerationService {
    private final PostRepository posts;
    private final PostReportRepository postReports;
    private final PostModerationRepository postModerations;
    private final FundraisingCampaignRepository campaigns;
    private final CampaignReportRepository campaignReports;
    private final CampaignModerationRepository campaignModerations;
    private final NotificationPublisher notifications;
    private final CurrentUser current;
    private final CampaignMapper campaignMapper;
    private final PostMapper postMapper;
    private final UserMapper userMapper;

    // =====================================================================
    //  Báo cáo bài đăng
    // =====================================================================
    /** Chuyển nhiều báo cáo một lần: tên tổ chức của người báo cáo tra một câu, bài được Hibernate nạp theo lô. */
    private List<PostReportDto> postReportDtos(List<PostReport> list) {
        Map<Long, String> orgNames = userMapper.orgNames(list.stream().map(PostReport::getReporter).toList());
        return list.stream().map(r -> {
            String c = r.getPost().getContent();
            return new PostReportDto(r.getId(), r.getPost().getId(), c.length() > 160 ? c.substring(0, 160) + "…" : c,
                    userMapper.user(r.getReporter(), orgNames), r.getReason(), r.getDescription(), r.getStatus(), r.getCreatedAt());
        }).toList();
    }

    /** Thực thể theo đúng thứ tự ids (findAllById không giữ thứ tự), một câu SQL. */
    private static <T> List<T> inOrder(List<Long> ids, List<T> found, java.util.function.Function<T, Long> idOf) {
        Map<Long, T> byId = new HashMap<>();
        for (T x : found) byId.put(idOf.apply(x), x);
        return ids.stream().map(byId::get).filter(Objects::nonNull).toList();
    }

    /** Tab 1: bài bị báo cáo, sắp xếp theo số báo cáo đang chờ giảm dần. */
    @Transactional(readOnly = true)
    public PageResponse<ReportedPostRow> reportedPosts(int page, int size) {
        var p = postReports.pendingCountsByPost(ReportStatus.PENDING, Paging.of(page, size));
        return PageResponse.ofAll(p, rows -> {
            Map<Long, Long> pending = new HashMap<>();
            for (Object[] r : rows) pending.put((Long) r[0], (Long) r[1]);
            List<Long> ids = rows.stream().map(r -> (Long) r[0]).toList();
            List<Post> list = inOrder(ids, posts.findAllById(ids), Post::getId);
            List<PostDto> dtos = postMapper.posts(list, null);
            List<ReportedPostRow> out = new ArrayList<>();
            for (int k = 0; k < list.size(); k++) out.add(new ReportedPostRow(dtos.get(k), pending.get(list.get(k).getId())));
            return out;
        });
    }

    /** Tab 2: từng báo cáo, mới nhất trước. */
    @Transactional(readOnly = true)
    public PageResponse<PostReportDto> allPostReports(int page, int size) {
        return PageResponse.ofAll(postReports.findAllByOrderByCreatedAtDesc(Paging.of(page, size)), this::postReportDtos);
    }

    @Transactional(readOnly = true)
    public PostReportDetail postReportDetail(Long postId) {
        Post p = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Bài đăng không tồn tại."));
        List<PostReport> pending = postReports.findByPostIdAndStatus(postId, ReportStatus.PENDING);
        Map<String, Long> byReason = pending.stream().collect(Collectors.groupingBy(r -> r.getReason().name(), LinkedHashMap::new, Collectors.counting()));
        return new PostReportDetail(postMapper.post(p, null), postReportDtos(pending), byReason);
    }

    /** Giữ hoặc xóa (xóa mềm) bài đăng; hoàn tất các báo cáo đang chờ và thông báo người báo cáo (và chủ bài nếu xóa). */
    @Transactional
    public MessageResponse moderatePost(Long postId, ModerationRequest r) {
        User admin = current.get();
        Post p = posts.findById(postId).orElseThrow(() -> ApiException.notFound("Bài đăng không tồn tại."));
        if (p.getStatus() == ContentStatus.DELETED) throw ApiException.conflict("ALREADY_DELETED", "Bài đăng đã bị xóa trước đó.");
        boolean remove = r.decision() == ModerationDecision.REMOVE;
        if (remove && (r.reason() == null || r.reason().isBlank())) throw ApiException.bad("REASON_REQUIRED", "Vui lòng chọn lý do khi xóa bài đăng.");
        List<PostReport> pending = postReports.findByPostIdAndStatus(postId, ReportStatus.PENDING);

        PostModeration m = new PostModeration();
        m.setPost(p); m.setAdmin(admin); m.setDecision(r.decision()); m.setReason(r.reason()); m.setDescription(r.description());
        postModerations.save(m);
        if (remove) {
            p.setStatus(ContentStatus.DELETED); p.setDeletedAt(Instant.now()); p.setDeletedBy(admin); p.setDeleteReason(r.reason());
            // Bài đã xóa thì không còn trang để mở: thông báo không kèm liên kết, lý do nằm trong nội dung
            notifications.notify(p.getAuthor(), NotificationType.POST_REMOVED, "Bài đăng bị gỡ",
                    "Bài đăng của bạn đã bị Admin xóa. Lý do: " + r.reason() + ".", null, null);
        }
        postReports.completeAll(postId, ReportStatus.PENDING, ReportStatus.COMPLETED);
        notifications.closeGroup(NotificationPublisher.reportGroupKey("post", postId)); // các Admin khác không còn thấy là việc chưa làm
        String result = remove ? "Admin đã xóa bài đăng bạn báo cáo." : "Admin đã xem xét và quyết định giữ bài đăng bạn báo cáo.";
        for (PostReport pr : pending)
            notifications.notify(pr.getReporter(), NotificationType.REPORT_RESULT, "Kết quả xử lý báo cáo", result,
                    remove ? null : "POST", remove ? null : postId); // bài đã xóa: không kèm liên kết
        return new MessageResponse(remove ? "Đã xóa bài đăng." : "Đã giữ bài đăng.");
    }

    // =====================================================================
    //  Báo cáo chiến dịch
    // =====================================================================
    private List<CampaignReportDto> campaignReportDtos(List<CampaignReport> list) {
        Map<Long, String> orgNames = userMapper.orgNames(list.stream().map(CampaignReport::getReporter).toList());
        return list.stream().map(r -> new CampaignReportDto(r.getId(), r.getCampaign().getId(), r.getCampaign().getName(),
                userMapper.user(r.getReporter(), orgNames), r.getReason(), r.getDescription(), r.getStatus(), r.getCreatedAt())).toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<ReportedCampaignRow> reportedCampaigns(int page, int size) {
        var p = campaignReports.pendingCountsByCampaign(ReportStatus.PENDING, Paging.of(page, size));
        return PageResponse.ofAll(p, rows -> {
            Map<Long, Long> pending = new HashMap<>();
            for (Object[] r : rows) pending.put((Long) r[0], (Long) r[1]);
            List<Long> ids = rows.stream().map(r -> (Long) r[0]).toList();
            List<FundraisingCampaign> list = inOrder(ids, campaigns.findAllById(ids), FundraisingCampaign::getId);
            List<CampaignDto> dtos = campaignMapper.campaigns(list, null);
            List<ReportedCampaignRow> out = new ArrayList<>();
            for (int k = 0; k < list.size(); k++) out.add(new ReportedCampaignRow(dtos.get(k), pending.get(list.get(k).getId())));
            return out;
        });
    }

    @Transactional(readOnly = true)
    public PageResponse<CampaignReportDto> allCampaignReports(int page, int size) {
        return PageResponse.ofAll(campaignReports.findAllByOrderByCreatedAtDesc(Paging.of(page, size)), this::campaignReportDtos);
    }

    @Transactional(readOnly = true)
    public CampaignReportDetail campaignReportDetail(Long id) {
        FundraisingCampaign c = campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Chiến dịch không tồn tại."));
        return new CampaignReportDetail(campaignMapper.campaign(c, null),
                campaignReportDtos(campaignReports.findByCampaignIdAndStatus(id, ReportStatus.PENDING)));
    }

    /** KEEP: giữ chiến dịch. REMOVE: dừng chiến dịch (ENDED / ADMIN_STOPPED), các khoản ủng hộ thành công vẫn được lưu. */
    @Transactional
    public MessageResponse moderateCampaign(Long id, ModerationRequest r) {
        User admin = current.get();
        FundraisingCampaign c = campaigns.findByIdForUpdate(id).orElseThrow(() -> ApiException.notFound("Chiến dịch không tồn tại."));
        boolean stop = r.decision() == ModerationDecision.REMOVE;
        if (stop && (r.reason() == null || r.reason().isBlank())) throw ApiException.bad("REASON_REQUIRED", "Vui lòng chọn lý do khi dừng chiến dịch.");
        if (stop && c.getStatus() == CampaignStatus.ENDED) throw ApiException.conflict("CAMPAIGN_ENDED", "Chiến dịch đã kết thúc.");
        List<CampaignReport> pending = campaignReports.findByCampaignIdAndStatus(id, ReportStatus.PENDING);

        CampaignModeration m = new CampaignModeration();
        m.setCampaign(c); m.setAdmin(admin); m.setDecision(r.decision()); m.setReason(r.reason()); m.setDescription(r.description());
        campaignModerations.save(m);
        if (stop) {
            c.setStatus(CampaignStatus.ENDED); c.setEndReason(EndReason.ADMIN_STOPPED); c.setEndedAt(Instant.now());
            notifications.notify(c.getOrg().getUser(), NotificationType.CAMPAIGN, "Chiến dịch bị dừng",
                    "Chiến dịch \"" + c.getName() + "\" đã bị Admin dừng. Lý do: " + r.reason() + ".", "CAMPAIGN", id);
        }
        campaignReports.completeAll(id, ReportStatus.PENDING, ReportStatus.COMPLETED);
        notifications.closeGroup(NotificationPublisher.reportGroupKey("campaign", id));
        String result = stop ? "Admin đã dừng chiến dịch bạn báo cáo." : "Admin đã xem xét và quyết định giữ chiến dịch bạn báo cáo.";
        for (CampaignReport cr : pending)
            notifications.notify(cr.getReporter(), NotificationType.REPORT_RESULT, "Kết quả xử lý báo cáo", result, "CAMPAIGN", id);
        return new MessageResponse(stop ? "Đã dừng chiến dịch." : "Đã giữ chiến dịch.");
    }
}
