package vn.floodconnect.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.auth.service.MailService;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.util.Format;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.service.RescueService;
import vn.floodconnect.user.entity.AccountLock;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.AccountLockRepository;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.EnumSet;

/** Khóa / khôi phục tài khoản (người dân và tổ chức), kèm hệ quả với chiến dịch của tổ chức. */
@Service
@RequiredArgsConstructor
public class AccountLockService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final UserRepository users;
    private final AccountLockRepository locks;
    private final RescueOrganizationRepository orgs;
    private final FundraisingCampaignRepository campaigns;
    private final NotificationPublisher notifications;
    private final MailService mail;
    private final RescueService rescueService;

    @Transactional
    public void lock(User target, User admin, String reason, int months) {
        Instant now = Instant.now();
        Instant until = now.atZone(VN).plusMonths(months).toInstant(); // cộng tháng theo lịch Việt Nam, không theo UTC
        target.setStatus(UserStatus.LOCKED);
        target.setLockedUntil(until);
        AccountLock l = new AccountLock();
        l.setUser(target); l.setLockedBy(admin); l.setReason(reason);
        l.setDurationDays((int) ChronoUnit.DAYS.between(now, until));
        l.setLockedAt(now); l.setLockedUntil(until);
        locks.save(l);

        orgs.findByUserId(target.getId()).ifPresent(o -> {
            if (o.getStatus() == OrgStatus.ACTIVE) {
                o.setStatus(OrgStatus.LOCKED);
                o.setLockedAt(now);
                o.setOperatingStatus(OperatingStatus.INACTIVE);
                rescueService.releaseAssigned(o, "Tổ chức bị khóa");
            }
            // Chiến dịch đang diễn ra (và sắp diễn ra) kết thúc với nguyên nhân ACCOUNT_LOCKED, không tự mở lại khi khôi phục
            for (FundraisingCampaign c : campaigns.findByOrgIdAndStatusIn(o.getId(), EnumSet.of(CampaignStatus.ACTIVE, CampaignStatus.UPCOMING))) {
                c.setStatus(CampaignStatus.ENDED);
                c.setEndReason(EndReason.ACCOUNT_LOCKED);
                c.setEndedAt(now);
            }
        });
        String msg = "Tài khoản của bạn đã bị khóa" + (reason == null || reason.isBlank() ? "" : " với lý do: " + reason)
                + ". Thời gian được khôi phục: " + Format.dateTime(until) + ".";
        notifications.notify(target, NotificationType.ACCOUNT, "Tài khoản bị khóa", msg, "USER", target.getId());
        mail.send(target.getEmail(), "FloodConnect - Tài khoản bị khóa", msg);
    }

    @Transactional
    public void unlock(User target, User adminOrNull) {
        Instant now = Instant.now();
        target.setStatus(UserStatus.ACTIVE);
        target.setLockedUntil(null);
        locks.findByUserIdAndUnlockedAtIsNull(target.getId()).forEach(l -> { l.setUnlockedAt(now); l.setUnlockedBy(adminOrNull); });
        var org = orgs.findByUserId(target.getId());
        org.ifPresent(o -> { if (o.getStatus() == OrgStatus.LOCKED) o.setStatus(OrgStatus.ACTIVE); });
        String msg = (adminOrNull == null
                ? "Tài khoản của bạn đã hết thời hạn khóa, các chức năng đã được mở lại."
                : "Tài khoản của bạn đã được Admin khôi phục, các chức năng đã được mở lại.")
                // Khi khóa, tổ chức bị chuyển sang Không hoạt động và mở khóa không tự bật lại
                + (org.isPresent() ? " Tổ chức đang ở trạng thái Không hoạt động: hãy chuyển sang Sẵn sàng để nhận ca mới." : "");
        notifications.notify(target, NotificationType.ACCOUNT, "Tài khoản được khôi phục", msg, "USER", target.getId());
        mail.send(target.getEmail(), "FloodConnect - Tài khoản được khôi phục", msg);
    }

    /** Gọi định kỳ: tự mở khóa tài khoản hết hạn. */
    @Transactional
    public int unlockExpired() {
        var expired = users.findByStatusAndLockedUntilBefore(UserStatus.LOCKED, Instant.now());
        for (User u : expired) {
            unlock(u, null);
            // Tài khoản tổ chức: Admin mở trang chi tiết tổ chức; người dân: trang quản lý người dân (frontend mở sẵn hồ sơ)
            var org = orgs.findByUserId(u.getId());
            notifications.notifyAdmins(NotificationType.ACCOUNT, "Tài khoản tự động mở khóa",
                    "Tài khoản " + org.map(o -> "tổ chức " + o.getName()).orElse(u.getFullName()) + " đã tự động mở khóa khi hết hạn.",
                    org.isPresent() ? "ORG" : "USER", org.map(o -> o.getId()).orElse(u.getId()));
        }
        return expired.size();
    }
}
