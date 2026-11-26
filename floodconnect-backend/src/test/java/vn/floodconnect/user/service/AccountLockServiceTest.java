package vn.floodconnect.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.floodconnect.auth.service.MailService;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.service.RescueService;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.AccountLockRepository;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Khóa / mở khóa tổ chức: hệ quả với chiến dịch, ca được phân công và nội dung thông báo. */
class AccountLockServiceTest {
    private final RescueOrganizationRepository orgs = mock(RescueOrganizationRepository.class);
    private final FundraisingCampaignRepository campaigns = mock(FundraisingCampaignRepository.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final RescueService rescue = mock(RescueService.class);
    private final UserRepository users = mock(UserRepository.class);
    private final AccountLockService service = new AccountLockService(users, mock(AccountLockRepository.class), orgs,
            campaigns, notifications, mock(MailService.class), rescue);
    private final User orgUser = new User();
    private final RescueOrganization org = new RescueOrganization();

    @BeforeEach void setUp() {
        orgUser.setId(10L); orgUser.setRole(Role.RESCUE_ORG); orgUser.setEmail("org@test.vn");
        org.setId(5L); org.setUser(orgUser); org.setStatus(OrgStatus.ACTIVE); org.setOperatingStatus(OperatingStatus.AVAILABLE);
        when(orgs.findByUserId(10L)).thenReturn(Optional.of(org));
    }

    private String notifiedContent(String title) {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        verify(notifications).notify(eq(orgUser), eq(NotificationType.ACCOUNT), eq(title), content.capture(), eq("USER"), eq(10L));
        return content.getValue();
    }

    @Test void lockingOrgEndsItsCampaignsAndReleasesAssignedCases() {
        FundraisingCampaign active = new FundraisingCampaign(), upcoming = new FundraisingCampaign();
        active.setStatus(CampaignStatus.ACTIVE); upcoming.setStatus(CampaignStatus.UPCOMING);
        when(campaigns.findByOrgIdAndStatusIn(eq(5L), any())).thenReturn(List.of(active, upcoming));
        Instant before = Instant.now();

        service.lock(orgUser, new User(), "Vi phạm quy định cộng đồng", 1);

        assertEquals(UserStatus.LOCKED, orgUser.getStatus());
        assertTrue(orgUser.getLockedUntil().isAfter(before.plus(Duration.ofDays(27)))); // 1 tháng theo lịch: 28–31 ngày
        assertEquals(OrgStatus.LOCKED, org.getStatus());
        assertEquals(OperatingStatus.INACTIVE, org.getOperatingStatus());
        verify(rescue).releaseAssigned(org, "Tổ chức bị khóa");
        for (FundraisingCampaign c : List.of(active, upcoming)) {
            assertEquals(CampaignStatus.ENDED, c.getStatus());
            assertEquals(EndReason.ACCOUNT_LOCKED, c.getEndReason());
        }
        String content = notifiedContent("Tài khoản bị khóa");
        assertTrue(content.contains("Vi phạm quy định cộng đồng"));
        assertTrue(content.contains("Thời gian được khôi phục"));
    }

    @Test void unlockingOrgRemindsToSwitchBackToAvailable() {
        org.setStatus(OrgStatus.LOCKED); org.setOperatingStatus(OperatingStatus.INACTIVE);
        orgUser.setStatus(UserStatus.LOCKED);

        service.unlock(orgUser, null);

        assertEquals(UserStatus.ACTIVE, orgUser.getStatus());
        assertEquals(OrgStatus.ACTIVE, org.getStatus());
        assertTrue(notifiedContent("Tài khoản được khôi phục").contains("Sẵn sàng"));
    }

    @Test void autoUnlockOfAnOrgAccountPointsAdminsToTheOrgPage() {
        orgUser.setStatus(UserStatus.LOCKED); orgUser.setFullName("Nguyễn Văn B"); org.setName("Đội A"); org.setStatus(OrgStatus.LOCKED);
        when(users.findByStatusAndLockedUntilBefore(eq(UserStatus.LOCKED), any())).thenReturn(List.of(orgUser));
        service.unlockExpired();
        verify(notifications).notifyAdmins(eq(NotificationType.ACCOUNT), eq("Tài khoản tự động mở khóa"),
                eq("Tài khoản tổ chức Đội A đã tự động mở khóa khi hết hạn."), eq("ORG"), eq(5L));
    }
}
