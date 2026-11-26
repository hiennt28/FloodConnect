package vn.floodconnect.campaign.scheduler;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import vn.floodconnect.campaign.service.CampaignService;
import vn.floodconnect.campaign.service.DonationService;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tác vụ định kỳ: trạng thái chiến dịch, hết hạn giao dịch ủng hộ bỏ dở. */
class CampaignSchedulerTest {
    private final DonationService donations = mock(DonationService.class);
    private final CampaignService campaigns = mock(CampaignService.class);
    private final CampaignScheduler scheduler = new CampaignScheduler(campaigns, donations);

    /** Mục #25: mỗi chiến dịch xử lý riêng, một chiến dịch lỗi (vd. xung đột @Version) không chặn các chiến dịch khác. */
    @Test void campaignStatusesHandleEachCampaignSeparately() {
        when(campaigns.idsToStart(any())).thenReturn(List.of(1L, 2L));
        when(campaigns.idsToExpire(any())).thenReturn(List.of(3L, 4L));
        doThrow(new IllegalStateException("xung đột")).when(campaigns).startIfDue(eq(1L), any());
        doThrow(new IllegalStateException("xung đột")).when(campaigns).expireIfDue(eq(3L), any());

        scheduler.campaignStatuses();

        verify(campaigns).startIfDue(eq(2L), any());
        verify(campaigns).expireIfDue(eq(4L), any());
    }

    @Test void expiresDonationsOlderThanTheConfiguredMinutesOneByOne() {
        ReflectionTestUtils.setField(scheduler, "pendingExpireMinutes", 30L);
        ArgumentCaptor<Instant> before = ArgumentCaptor.forClass(Instant.class);
        when(donations.stalePendingIds(before.capture())).thenReturn(List.of(1L, 2L));
        when(donations.expireIfStale(eq(1L), any())).thenThrow(new IllegalStateException("lỗi DB"));

        scheduler.expireStaleDonations();

        long minutesAgo = Duration.between(before.getValue(), Instant.now()).toMinutes();
        assertTrue(minutesAgo >= 29 && minutesAgo <= 30);
        verify(donations).expireIfStale(2L, before.getValue()); // giao dịch lỗi không chặn giao dịch sau
    }
}
