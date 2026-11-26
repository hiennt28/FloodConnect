package vn.floodconnect.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.floodconnect.campaign.dto.CampaignDtos.CampaignRequest;
import vn.floodconnect.campaign.entity.CampaignMedia;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.repository.CampaignMediaRepository;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.service.OrgContext;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.campaign.entity.EndReason;

/** Tạo/sửa chiến dịch: quy tắc khớp với kiểm tra ở giao diện; tài khoản ngân hàng nhận tiền. */
class CampaignServiceTest {
    private final FundraisingCampaignRepository campaigns = mock(FundraisingCampaignRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final OrgContext orgContext = mock(OrgContext.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final CampaignReportRepository reports = mock(CampaignReportRepository.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final CampaignMediaRepository mediaRepo = mock(CampaignMediaRepository.class);
    private final CampaignService service = new CampaignService(campaigns, mediaRepo, reports,
            current, orgContext, storage, notifications, mock(CampaignMapper.class));
    private final MockMultipartFile avatar = new MockMultipartFile("avatar", "a.png", "image/png", new byte[]{1});

    private final User u = new User();
    private final RescueOrganization o = new RescueOrganization();

    @BeforeEach void setUp() {
        u.setId(9L);
        o.setName("Đội A"); o.setUser(u);
        when(current.get()).thenReturn(u);
        when(orgContext.active(u)).thenReturn(o);
        when(storage.store(any(), anyString(), any())).thenReturn(new FileStorageService.Stored("/files/a.png", "image/png"));
    }

    private static CampaignRequest request(String bank, String accountNumber, String orgInfo) {
        Instant start = Instant.now().plus(Duration.ofDays(1));
        return new CampaignRequest("Áo phao cho xóm Bàu", "Mua áo phao", "Hòa Vang", "Hộ dân vùng trũng", orgInfo,
                50_000_000L, bank, "Nguyen Van A", accountNumber, start, start.plus(Duration.ofDays(10)));
    }
    private static CampaignRequest request(String accountNumber, String orgInfo) { return request("MB Bank", accountNumber, orgInfo); }

    /** Chiến dịch đang diễn ra của tổ chức; chỉ gửi trường ngân hàng khi sửa. */
    private FundraisingCampaign active(String bank) {
        FundraisingCampaign c = new FundraisingCampaign();
        c.setId(4L); c.setOrg(o); c.setStatus(CampaignStatus.ACTIVE); c.setBankName(bank);
        c.setTargetAmount(50_000_000L); c.setAccountHolder("Nguyen Van A"); c.setAccountNumber("0123456789");
        c.setStartDate(Instant.now().minus(Duration.ofDays(1))); c.setEndDate(Instant.now().plus(Duration.ofDays(9)));
        when(campaigns.findById(4L)).thenReturn(Optional.of(c));
        return c;
    }
    private static CampaignRequest bankOnly(String bank) {
        return new CampaignRequest(null, null, null, null, null, null, bank, null, null, null, null);
    }

    @Test void avatarIsRequired() {
        ApiException e = assertThrows(ApiException.class, () -> service.create(request("0123456789", null), null, List.of()));
        assertEquals("MISSING_AVATAR", e.getCode());
        verify(campaigns, never()).save(any());
    }

    @Test void changingCoverDeletesOldCoverFileAfterCommit() {
        FundraisingCampaign c = active("MB Bank");
        c.setAvatarUrl("/files/campaigns/cu.jpg");
        service.setAvatar(4L, avatar);
        assertEquals("/files/a.png", c.getAvatarUrl());
        verify(storage).deleteAfterCommit("/files/campaigns/cu.jpg");
    }

    @Test void endedCampaignCoverCannotChangeAndNothingIsDeleted() {
        FundraisingCampaign c = active("MB Bank");
        c.setStatus(CampaignStatus.ENDED);
        c.setAvatarUrl("/files/campaigns/cu.jpg");
        assertThrows(ApiException.class, () -> service.setAvatar(4L, avatar));
        assertEquals("/files/campaigns/cu.jpg", c.getAvatarUrl());
        verify(storage, never()).deleteAfterCommit(any());
    }

    @Test void removingGalleryImageDeletesItsFileAfterCommit() {
        FundraisingCampaign c = active("MB Bank");
        CampaignMedia m = new CampaignMedia();
        m.setId(7L); m.setCampaign(c); m.setMediaUrl("/files/campaigns/anh-1.jpg");
        when(mediaRepo.findById(7L)).thenReturn(Optional.of(m));
        service.removeImage(4L, 7L);
        verify(mediaRepo).delete(m);
        verify(storage).deleteAfterCommit("/files/campaigns/anh-1.jpg");
    }

    @Test void imageOfAnotherCampaignIsNotRemovedNorDeleted() {
        active("MB Bank");
        FundraisingCampaign other = new FundraisingCampaign();
        other.setId(99L);
        CampaignMedia m = new CampaignMedia();
        m.setId(8L); m.setCampaign(other); m.setMediaUrl("/files/campaigns/khac.jpg");
        when(mediaRepo.findById(8L)).thenReturn(Optional.of(m));
        assertThrows(ApiException.class, () -> service.removeImage(4L, 8L));
        verify(mediaRepo, never()).delete(any());
        verify(storage, never()).deleteAfterCommit(any());
    }

    @Test void accountNumberMustBe6To20Digits() {
        for (String bad : new String[]{"12345", "123456789012345678901", "12a456789"}) {
            ApiException e = assertThrows(ApiException.class, () -> service.create(request(bad, null), avatar, List.of()));
            assertEquals("INVALID_ACCOUNT", e.getCode());
        }
        verify(campaigns, never()).save(any());
    }

    @Test void bankNameIsRequiredAndTrimmed() {
        ApiException e = assertThrows(ApiException.class, () -> service.create(request("  ", "0123456789", null), avatar, List.of()));
        assertEquals("MISSING_FIELD", e.getCode());
        service.create(request("  Vietcombank ", "0123456789", null), avatar, List.of());
        verify(campaigns).save(argThat((FundraisingCampaign c) -> c.getBankName().equals("Vietcombank")));
    }

    @Test void activeCampaignCreatedBeforeBankFieldCanAddItOnce() {
        FundraisingCampaign c = active(null);
        service.update(4L, bankOnly("BIDV"));
        assertEquals("BIDV", c.getBankName());

        ApiException e = assertThrows(ApiException.class, () -> service.update(4L, bankOnly("Agribank"))); // đã có: không đổi được nữa
        assertEquals("FIELD_LOCKED", e.getCode());
        assertEquals("BIDV", c.getBankName());
    }

    @Test void blankOrgInfoFallsBackToOrganizationName() {
        service.create(request("0123456789", "  "), avatar, List.of());
        verify(campaigns).save(argThat((FundraisingCampaign c) -> c.getOrgInfo().equals("Đội A") && c.getAvatarUrl().equals("/files/a.png")));
    }

    // ---------- Báo cáo chiến dịch: báo Admin, gộp theo chiến dịch ----------
    @Test void reportingACampaignNotifiesAdminsGroupedByCampaign() {
        FundraisingCampaign c = active("MB Bank");
        c.setName("Áo phao cho xóm Bàu");
        User reporter = new User(); reporter.setId(20L);
        when(current.getActive()).thenReturn(reporter);
        when(campaigns.findById(4L)).thenReturn(Optional.of(c));
        when(reports.countByCampaignIdAndStatus(4L, ReportStatus.PENDING)).thenReturn(1L);

        service.report(4L, new ReportRequest("PROFITEERING", null));

        verify(notifications).notifyAdminsGrouped(eq(NotificationType.REPORT_NEW), eq("report:campaign:4"), eq("Chiến dịch bị báo cáo"),
                argThat(f -> f.apply(1).equals("Chiến dịch \"Áo phao cho xóm Bàu\" của Đội A có 1 báo cáo chờ xử lý (mới nhất: Có dấu hiệu trục lợi).")),
                eq("CAMPAIGN"), eq(4L));
    }

    // ---------------- Trạng thái theo thời gian (#25: khóa từng chiến dịch rồi kiểm tra lại) ----------------
    private FundraisingCampaign campaign(CampaignStatus st, Instant start, Instant end) {
        FundraisingCampaign c = new FundraisingCampaign();
        c.setId(5L); c.setOrg(o); c.setName("Áo phao"); c.setStatus(st); c.setStartDate(start); c.setEndDate(end);
        when(campaigns.findByIdForUpdate(5L)).thenReturn(Optional.of(c));
        return c;
    }

    @Test void expiredActiveCampaignEndsAndNotifiesOrg() {
        Instant now = Instant.now();
        FundraisingCampaign c = campaign(CampaignStatus.ACTIVE, now.minus(Duration.ofDays(5)), now.minus(Duration.ofMinutes(1)));
        service.expireIfDue(5L, now);
        assertEquals(CampaignStatus.ENDED, c.getStatus());
        assertEquals(EndReason.EXPIRED, c.getEndReason());
        verify(notifications).notify(eq(u), eq(NotificationType.CAMPAIGN), eq("Chiến dịch hết hạn"), anyString(), eq("CAMPAIGN"), eq(5L));
    }

    /** Tổ chức vừa gia hạn sau khi tác vụ lấy danh sách: dữ liệu vừa khóa cho thấy chưa hết hạn, không được kết thúc nhầm. */
    @Test void campaignExtendedMeanwhileIsNotEnded() {
        Instant now = Instant.now();
        FundraisingCampaign c = campaign(CampaignStatus.ACTIVE, now.minus(Duration.ofDays(5)), now.plus(Duration.ofDays(3)));
        service.expireIfDue(5L, now);
        assertEquals(CampaignStatus.ACTIVE, c.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test void upcomingCampaignStartsWhenDue() {
        Instant now = Instant.now();
        FundraisingCampaign c = campaign(CampaignStatus.UPCOMING, now.minus(Duration.ofMinutes(1)), now.plus(Duration.ofDays(3)));
        service.startIfDue(5L, now);
        assertEquals(CampaignStatus.ACTIVE, c.getStatus());
    }

    @Test void accountHolderLongerThanColumnIsRejected() {
        CampaignRequest r = request("Vietcombank", "0123456789", null);
        CampaignRequest tooLong = new CampaignRequest(r.name(), r.purpose(), r.supportArea(), r.targetAudience(), r.orgInfo(), r.targetAmount(),
                r.bankName(), "A".repeat(256), r.accountNumber(), r.startDate(), r.endDate());
        ApiException e = assertThrows(ApiException.class, () -> service.create(tooLong, avatar, List.of()));
        assertEquals("INVALID_ACCOUNT_HOLDER", e.getCode());
    }
}
