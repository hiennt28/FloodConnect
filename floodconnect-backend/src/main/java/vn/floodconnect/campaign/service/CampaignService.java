package vn.floodconnect.campaign.service;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignDto;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignRequest;
import vn.floodconnect.campaign.entity.CampaignMedia;
import vn.floodconnect.campaign.entity.CampaignReport;
import vn.floodconnect.campaign.entity.CampaignReportReason;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.repository.CampaignMediaRepository;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.service.OrgContext;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

/** Chiến dịch ủng hộ: tạo, sửa theo trạng thái, kết thúc, gia hạn, xem, báo cáo. */
@Service
@RequiredArgsConstructor
public class CampaignService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_MEDIA = 10;

    private final FundraisingCampaignRepository campaigns;
    private final CampaignMediaRepository media;
    private final CampaignReportRepository reports;
    private final CurrentUser current;
    private final OrgContext orgContext;
    private final FileStorageService storage;
    private final NotificationPublisher notifications;
    private final CampaignMapper campaignMapper;

    // ---------------- Xem ----------------
    /** status = UPCOMING | ACTIVE | ENDED | ALL (mặc định ACTIVE); sắp xếp theo thời gian bắt đầu tăng dần. */
    @Transactional(readOnly = true)
    public PageResponse<CampaignDto> list(String status, String q, int page, int size) {
        Long me = current.idOrNull();
        CampaignStatus st = (status == null || status.isBlank()) ? CampaignStatus.ACTIVE
                : "ALL".equalsIgnoreCase(status) ? null : CampaignStatus.valueOf(status.toUpperCase());
        String kw = q == null ? "" : q.trim().toLowerCase();
        Specification<FundraisingCampaign> spec = (root, qq, cb) -> {
            List<Predicate> ps = new ArrayList<>();
            if (st != null) ps.add(cb.equal(root.get("status"), st));
            if (!kw.isEmpty()) ps.add(cb.like(cb.lower(root.get("name")), "%" + kw + "%"));
            return cb.and(ps.toArray(new Predicate[0]));
        };
        return PageResponse.ofAll(campaigns.findAll(spec, Paging.of(page, size, Sort.by("startDate").ascending().and(Sort.by("id"))) /* id: thứ tự ổn định giữa các lần "Tải thêm" */), l -> campaignMapper.campaigns(l, me));
    }

    @Transactional(readOnly = true)
    public CampaignDto detail(Long id) { return campaignMapper.campaign(find(id), current.idOrNull()); }

    /** Tab "Chiến dịch của chúng tôi". */
    @Transactional(readOnly = true)
    public List<CampaignDto> mine() {
        User u = current.get();
        RescueOrganization o = orgContext.allowLocked(u);
        return campaignMapper.campaigns(campaigns.findByOrgIdOrderByCreatedAtDesc(o.getId()), u.getId());
    }

    private FundraisingCampaign find(Long id) {
        return campaigns.findById(id).orElseThrow(() -> ApiException.notFound("Chiến dịch không tồn tại."));
    }

    private FundraisingCampaign own(Long id, User u) {
        FundraisingCampaign c = find(id);
        if (!c.getOrg().getUser().getId().equals(u.getId())) throw ApiException.forbidden("Bạn chỉ thao tác được trên chiến dịch của tổ chức mình.");
        return c;
    }

    // ---------------- Tạo ----------------
    private static String req(String v, String field) {
        if (v == null || v.isBlank()) throw ApiException.bad("MISSING_FIELD", "Vui lòng nhập " + field + ".");
        return v.trim();
    }

    /** Tên ngân hàng nhận tiền: chọn từ danh sách ở giao diện hoặc tự gõ ("Ngân hàng khác"), tối đa 100 ký tự. */
    private static String bankName(String v) {
        String b = req(v, "ngân hàng nhận tiền");
        if (b.length() > 100) throw ApiException.bad("INVALID_BANK", "Tên ngân hàng tối đa 100 ký tự.");
        return b;
    }

    /** Tên chủ tài khoản nhận tiền: bắt buộc, tối đa 255 ký tự (kích thước cột). */
    private static String accountHolder(String v) {
        String h = req(v, "tên chủ tài khoản");
        if (h.length() > Validators.TEXT_MAX) throw ApiException.bad("INVALID_ACCOUNT_HOLDER", Validators.tooLongMsg("Tên chủ tài khoản", Validators.TEXT_MAX));
        return h;
    }

    /** Số tài khoản nhận tiền: 6–20 chữ số (khớp kiểm tra ở giao diện). */
    private static String accountNumber(String v) {
        String n = req(v, "tài khoản nhận tiền");
        if (!n.matches("\\d{6,20}")) throw ApiException.bad("INVALID_ACCOUNT", "Số tài khoản nhận tiền phải gồm 6–20 chữ số.");
        return n;
    }

    @Transactional
    public CampaignDto create(CampaignRequest r, MultipartFile avatar, List<MultipartFile> images) {
        User u = current.get();
        RescueOrganization o = orgContext.active(u);
        FundraisingCampaign c = new FundraisingCampaign();
        c.setOrg(o);
        c.setName(req(r.name(), "tên chiến dịch"));
        if (c.getName().length() > 200) throw ApiException.bad("INVALID_NAME", "Tên chiến dịch tối đa 200 ký tự.");
        c.setPurpose(req(r.purpose(), "mục đích chiến dịch"));
        c.setSupportArea(req(r.supportArea(), "khu vực được hỗ trợ"));
        c.setTargetAudience(req(r.targetAudience(), "đối tượng được hỗ trợ"));
        c.setOrgInfo(r.orgInfo() == null || r.orgInfo().isBlank() ? o.getName() : r.orgInfo().trim());
        if (r.targetAmount() == null || r.targetAmount() <= 0) throw ApiException.bad("INVALID_TARGET", "Số tiền mục tiêu phải lớn hơn 0.");
        c.setTargetAmount(r.targetAmount());
        c.setBankName(bankName(r.bankName()));
        c.setAccountHolder(accountHolder(r.accountHolder()));
        c.setAccountNumber(accountNumber(r.accountNumber()));
        validateTimes(r.startDate(), r.endDate(), true);
        c.setStartDate(r.startDate()); c.setEndDate(r.endDate());
        c.setStatus(r.startDate().isAfter(Instant.now()) ? CampaignStatus.UPCOMING : CampaignStatus.ACTIVE);
        // Ảnh đại diện bắt buộc: là ảnh của thẻ chiến dịch trên danh sách
        if (avatar == null || avatar.isEmpty()) throw ApiException.bad("MISSING_AVATAR", "Vui lòng chọn ảnh đại diện cho chiến dịch.");
        c.setAvatarUrl(storage.store(avatar, "campaigns", FileStorageService.IMAGES).url());
        campaigns.save(c);
        addMedia(c, FileStorageService.nonEmpty(images));
        return campaignMapper.campaign(c, u.getId());
    }

    private void validateTimes(Instant start, Instant end, boolean checkStartNotPast) {
        if (start == null || end == null) throw ApiException.bad("MISSING_FIELD", "Vui lòng chọn thời gian bắt đầu và kết thúc.");
        if (checkStartNotPast && start.isBefore(LocalDate.now(VN).atStartOfDay(VN).toInstant()))
            throw ApiException.bad("INVALID_START", "Thời gian bắt đầu không được ở quá khứ.");
        if (!end.isAfter(start)) throw ApiException.bad("INVALID_END", "Thời gian kết thúc phải sau thời gian bắt đầu.");
    }

    private void addMedia(FundraisingCampaign c, List<MultipartFile> files) {
        long existing = media.countByCampaignId(c.getId());
        if (existing + files.size() > MAX_MEDIA) throw ApiException.bad("TOO_MANY_FILES", "Tối đa " + MAX_MEDIA + " ảnh minh họa.");
        int order = (int) existing;
        for (MultipartFile f : files) {
            CampaignMedia m = new CampaignMedia();
            m.setCampaign(c); m.setMediaUrl(storage.store(f, "campaigns", FileStorageService.IMAGES).url()); m.setSortOrder(order++);
            media.save(m);
        }
    }

    // ---------------- Sửa theo trạng thái ----------------
    private static boolean changed(Object oldV, Object newV) { return newV != null && !newV.equals(oldV); }
    private static boolean changedTime(Instant oldV, Instant newV) { return newV != null && newV.toEpochMilli() != oldV.toEpochMilli(); }

    @Transactional
    public CampaignDto update(Long id, CampaignRequest r) {
        User u = current.get();
        orgContext.active(u);
        FundraisingCampaign c = own(id, u);
        switch (c.getStatus()) {
            case ENDED -> throw ApiException.conflict("CAMPAIGN_ENDED", c.getEndReason() == EndReason.EXPIRED
                    ? "Chiến dịch đã hết hạn: chỉ được gia hạn, không được chỉnh sửa nội dung."
                    : "Chiến dịch đã kết thúc và không thể chỉnh sửa hoặc gia hạn. Hãy tạo chiến dịch mới.");
            case ACTIVE -> {
                // Chiến dịch tạo trước khi có trường ngân hàng (bankName rỗng) được bổ sung một lần; đã có thì không đổi được
                boolean bankChanged = c.getBankName() != null && changed(c.getBankName(), r.bankName() == null ? null : r.bankName().trim());
                if (changed(c.getTargetAmount(), r.targetAmount()) || bankChanged
                        || changed(c.getAccountHolder(), r.accountHolder()) || changed(c.getAccountNumber(), r.accountNumber())
                        || changedTime(c.getStartDate(), r.startDate()))
                    throw ApiException.bad("FIELD_LOCKED", "Chiến dịch đang diễn ra không được sửa số tiền mục tiêu, tài khoản nhận tiền và thời gian bắt đầu.");
                if (c.getBankName() == null && r.bankName() != null && !r.bankName().isBlank()) c.setBankName(bankName(r.bankName()));
                if (changedTime(c.getEndDate(), r.endDate())) {
                    if (!r.endDate().isAfter(c.getEndDate())) throw ApiException.bad("INVALID_END", "Thời gian kết thúc mới phải lớn hơn thời gian kết thúc hiện tại.");
                    c.setEndDate(r.endDate());
                }
            }
            case UPCOMING -> {
                if (r.targetAmount() != null) { if (r.targetAmount() <= 0) throw ApiException.bad("INVALID_TARGET", "Số tiền mục tiêu phải lớn hơn 0."); c.setTargetAmount(r.targetAmount()); }
                if (r.bankName() != null) c.setBankName(bankName(r.bankName()));
                if (r.accountHolder() != null) c.setAccountHolder(accountHolder(r.accountHolder()));
                if (r.accountNumber() != null) c.setAccountNumber(accountNumber(r.accountNumber()));
                Instant s = r.startDate() != null ? r.startDate() : c.getStartDate();
                Instant e = r.endDate() != null ? r.endDate() : c.getEndDate();
                validateTimes(s, e, r.startDate() != null && changedTime(c.getStartDate(), r.startDate()));
                c.setStartDate(s); c.setEndDate(e);
                if (!s.isAfter(Instant.now())) c.setStatus(CampaignStatus.ACTIVE);
            }
        }
        if (r.name() != null) {
            c.setName(req(r.name(), "tên chiến dịch"));
            if (c.getName().length() > 200) throw ApiException.bad("INVALID_NAME", "Tên chiến dịch tối đa 200 ký tự.");
        }
        if (r.purpose() != null) c.setPurpose(req(r.purpose(), "mục đích chiến dịch"));
        if (r.supportArea() != null) c.setSupportArea(req(r.supportArea(), "khu vực được hỗ trợ"));
        if (r.targetAudience() != null) c.setTargetAudience(req(r.targetAudience(), "đối tượng được hỗ trợ"));
        if (r.orgInfo() != null) c.setOrgInfo(r.orgInfo().isBlank() ? c.getOrg().getName() : r.orgInfo().trim());
        return campaignMapper.campaign(c, u.getId());
    }

    /** Đổi ảnh bìa chiến dịch. Tệp ảnh bìa cũ được xóa sau khi lưu thành công để không tồn đọng tệp thừa. */
    @Transactional
    public CampaignDto setAvatar(Long id, MultipartFile f) {
        User u = current.get();
        orgContext.active(u);
        FundraisingCampaign c = own(id, u);
        editableMedia(c);
        String old = c.getAvatarUrl();
        c.setAvatarUrl(storage.store(f, "campaigns", FileStorageService.IMAGES).url());
        storage.deleteAfterCommit(old);
        return campaignMapper.campaign(c, u.getId());
    }

    @Transactional
    public CampaignDto addImages(Long id, List<MultipartFile> files) {
        User u = current.get();
        orgContext.active(u);
        FundraisingCampaign c = own(id, u);
        editableMedia(c);
        addMedia(c, FileStorageService.nonEmpty(files));
        return campaignMapper.campaign(c, u.getId());
    }

    @Transactional
    public void removeImage(Long id, Long mediaId) {
        User u = current.get();
        orgContext.active(u);
        FundraisingCampaign c = own(id, u);
        editableMedia(c);
        CampaignMedia m = media.findById(mediaId).orElseThrow(() -> ApiException.notFound("Ảnh không tồn tại."));
        if (!m.getCampaign().getId().equals(id)) throw ApiException.forbidden("Ảnh không thuộc chiến dịch này.");
        media.delete(m);
        storage.deleteAfterCommit(m.getMediaUrl()); // xóa cả tệp ảnh, sau khi xóa dòng dữ liệu thành công
    }

    private void editableMedia(FundraisingCampaign c) {
        if (c.getStatus() == CampaignStatus.ENDED) throw ApiException.conflict("CAMPAIGN_ENDED", "Chiến dịch đã kết thúc, không thể chỉnh sửa.");
    }

    // ---------------- Kết thúc / gia hạn ----------------
    @Transactional
    public CampaignDto end(Long id) {
        User u = current.get();
        orgContext.allowLocked(u);
        FundraisingCampaign c = own(id, u);
        if (c.getStatus() == CampaignStatus.ENDED) throw ApiException.conflict("CAMPAIGN_ENDED", "Chiến dịch đã kết thúc.");
        c.setStatus(CampaignStatus.ENDED);
        c.setEndReason(EndReason.ORGANIZATION_ENDED);
        c.setEndedAt(Instant.now());
        return campaignMapper.campaign(c, u.getId());
    }

    /** Chỉ chiến dịch ENDED do hết thời gian (EXPIRED) mới được gia hạn; các nguyên nhân khác phải tạo chiến dịch mới. */
    @Transactional
    public CampaignDto extend(Long id, Instant newEnd) {
        User u = current.get();
        orgContext.active(u);
        FundraisingCampaign c = own(id, u);
        if (c.getStatus() != CampaignStatus.ENDED || c.getEndReason() != EndReason.EXPIRED)
            throw ApiException.conflict("NOT_EXTENDABLE", "Chỉ chiến dịch kết thúc do hết thời gian mới được gia hạn.");
        if (!newEnd.isAfter(Instant.now())) throw ApiException.bad("INVALID_END", "Thời gian kết thúc mới phải lớn hơn thời điểm hiện tại.");
        c.setEndDate(newEnd);
        c.setStatus(CampaignStatus.ACTIVE);
        c.setEndReason(null);
        c.setEndedAt(null);
        return campaignMapper.campaign(c, u.getId());
    }

    /*
     * Cập nhật trạng thái theo thời gian (CampaignScheduler gọi mỗi phút): UPCOMING -> ACTIVE khi đến giờ bắt đầu,
     * ACTIVE -> ENDED (EXPIRED) khi hết hạn. Trước đây mọi chiến dịch nằm chung một transaction: một khoản ủng hộ được cộng
     * tiền đúng lúc đó (chiến dịch có @Version) làm hỏng cả lượt chạy. Nay scheduler lấy danh sách id rồi xử lý TỪNG chiến dịch
     * trong transaction riêng, khóa dòng và kiểm tra lại điều kiện trên dữ liệu vừa khóa (giống RescueScheduler).
     */
    @Transactional(readOnly = true)
    public List<Long> idsToStart(Instant now) { return campaigns.findIdsToStart(CampaignStatus.UPCOMING, now); }

    @Transactional(readOnly = true)
    public List<Long> idsToExpire(Instant now) { return campaigns.findIdsToEnd(CampaignStatus.ACTIVE, now); }

    /** Chiến dịch sắp diễn ra đã tới giờ bắt đầu: ACTIVE, hoặc ENDED (EXPIRED) nếu thời gian kết thúc cũng đã qua. */
    @Transactional
    public void startIfDue(Long id, Instant now) {
        FundraisingCampaign c = campaigns.findByIdForUpdate(id).orElse(null);
        if (c == null || c.getStatus() != CampaignStatus.UPCOMING || c.getStartDate().isAfter(now)) return; // tổ chức vừa sửa / kết thúc
        if (c.getEndDate().isAfter(now)) c.setStatus(CampaignStatus.ACTIVE);
        else { c.setStatus(CampaignStatus.ENDED); c.setEndReason(EndReason.EXPIRED); c.setEndedAt(c.getEndDate()); }
    }

    /** Chiến dịch đang diễn ra đã hết thời gian: ENDED (EXPIRED) và báo tổ chức (có thể gia hạn). */
    @Transactional
    public void expireIfDue(Long id, Instant now) {
        FundraisingCampaign c = campaigns.findByIdForUpdate(id).orElse(null);
        // Có thể vừa được gia hạn, vừa đạt mục tiêu hoặc vừa bị tổ chức / Admin kết thúc
        if (c == null || c.getStatus() != CampaignStatus.ACTIVE || c.getEndDate().isAfter(now)) return;
        c.setStatus(CampaignStatus.ENDED);
        c.setEndReason(EndReason.EXPIRED);
        c.setEndedAt(c.getEndDate());
        notifications.notify(c.getOrg().getUser(), NotificationType.CAMPAIGN, "Chiến dịch hết hạn",
                "Chiến dịch \"" + c.getName() + "\" đã hết thời gian. Bạn có thể gia hạn.", "CAMPAIGN", c.getId());
    }

    // ---------------- Báo cáo chiến dịch ----------------
    @Transactional
    public MessageResponse report(Long id, ReportRequest r) {
        User u = current.getActive();
        FundraisingCampaign c = find(id);
        if (c.getStatus() == CampaignStatus.ENDED) throw ApiException.conflict("CAMPAIGN_ENDED", "Chỉ báo cáo được chiến dịch sắp diễn ra hoặc đang diễn ra.");
        if (reports.existsByCampaignIdAndReporterId(id, u.getId())) throw ApiException.conflict("ALREADY_REPORTED", "Bạn đã báo cáo chiến dịch này rồi.");
        CampaignReportReason reason;
        try { reason = CampaignReportReason.valueOf(r.reason().trim().toUpperCase()); }
        catch (Exception e) { throw ApiException.bad("Lý do không hợp lệ (PROFITEERING, MISINFORMATION, OTHER)."); }
        if (reason == CampaignReportReason.OTHER && (r.description() == null || r.description().isBlank()))
            throw ApiException.bad("DESCRIPTION_REQUIRED", "Vui lòng mô tả khi chọn lý do khác.");
        CampaignReport cr = new CampaignReport();
        cr.setCampaign(c); cr.setReporter(u); cr.setReason(reason); cr.setDescription(r.description());
        reports.save(cr);
        // Báo Admin, gộp theo chiến dịch (như báo cáo bài viết)
        long pending = reports.countByCampaignIdAndStatus(id, ReportStatus.PENDING);
        String body = "Chiến dịch \"" + c.getName() + "\" của " + c.getOrg().getName() + " có " + pending
                + " báo cáo chờ xử lý (mới nhất: " + reason.label + ").";
        notifications.notifyAdminsGrouped(NotificationType.REPORT_NEW, NotificationPublisher.reportGroupKey("campaign", id),
                "Chiến dịch bị báo cáo", n -> body, "CAMPAIGN", id);
        return new MessageResponse("Đã ghi nhận báo cáo của bạn.");
    }
}
