package vn.floodconnect.stats.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.stats.dto.PublicStatsDtos.PublicStats;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Thống kê công khai: đếm đúng nguồn, lưu tạm 5 phút để khách tải lại liên tục không làm cơ sở dữ liệu đếm lại. */
class PublicStatsServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final RescueOrganizationRepository orgs = mock(RescueOrganizationRepository.class);
    private final FundraisingCampaignRepository campaigns = mock(FundraisingCampaignRepository.class);
    private final DonationRepository donations = mock(DonationRepository.class);
    private final PublicStatsService service = new PublicStatsService(incidents, orgs, campaigns, donations);
    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-11-10T08:00:00Z"));

    @BeforeEach void setUp() {
        service.clock = now::get;
        when(incidents.countByStatus(IncidentStatus.COMPLETED)).thenReturn(12L);
        when(orgs.countByStatus(OrgStatus.ACTIVE)).thenReturn(5L);
        when(campaigns.countByStatus(CampaignStatus.ACTIVE)).thenReturn(3L);
        when(donations.sumAmountByStatus(DonationStatus.SUCCESS)).thenReturn(2_000_000L);
    }

    @Test void demCaHoanThanhToChucHoatDongChienDichDangChayVaTienUngHoThanhCong() {
        PublicStats s = service.stats();
        assertEquals(new PublicStats(12, 5, 3, 2_000_000, now.get()), s);
    }

    @Test void trongNamPhutDungLaiKetQuaQuaNamPhutThiTinhLai() {
        PublicStats first = service.stats();
        now.set(now.get().plus(PublicStatsService.TTL).minusSeconds(1));
        assertSame(first, service.stats());
        verify(incidents, times(1)).countByStatus(IncidentStatus.COMPLETED);

        now.set(first.updatedAt().plus(PublicStatsService.TTL));
        when(incidents.countByStatus(IncidentStatus.COMPLETED)).thenReturn(13L);
        PublicStats second = service.stats();
        assertEquals(13, second.completedSos());
        assertEquals(now.get(), second.updatedAt());
        verify(incidents, times(2)).countByStatus(IncidentStatus.COMPLETED);
    }
}
