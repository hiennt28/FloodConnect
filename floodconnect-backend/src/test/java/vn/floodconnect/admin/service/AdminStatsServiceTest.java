package vn.floodconnect.admin.service;

import org.junit.jupiter.api.Test;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.post.repository.PostReportRepository;
import vn.floodconnect.admin.dto.AdminDtos.DayCount;
import vn.floodconnect.admin.dto.AdminDtos.DurationStat;
import vn.floodconnect.admin.dto.AdminDtos.SosTimeline;
import vn.floodconnect.admin.dto.AdminDtos.StatsOverview;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Thống kê theo thời gian: đủ mọi ngày, đúng giờ Việt Nam, không có dữ liệu thì trung bình là null. */
class AdminStatsServiceTest {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final DonationRepository donations = mock(DonationRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final PostReportRepository postReports = mock(PostReportRepository.class);
    private final CampaignReportRepository campaignReports = mock(CampaignReportRepository.class);
    private final AdminStatsService service = new AdminStatsService(mock(UserRepository.class), mock(RescueOrganizationRepository.class),
            incidents, donations, assignments, postReports, campaignReports);

    private static List<Object[]> rows(Object[]... r) { return Arrays.asList(r); }

    @Test void fillDaysDienDuNgayTrongBang0VaGiuThuTu() {
        LocalDate d1 = LocalDate.of(2026, 11, 1);
        List<DayCount> out = AdminStatsService.fillDays(d1, d1.plusDays(3),
                Map.of(d1, 2L, d1.plusDays(2), 5L), Map.of(d1.plusDays(3), 1L));
        assertEquals(4, out.size());
        assertEquals(List.of(d1, d1.plusDays(1), d1.plusDays(2), d1.plusDays(3)), out.stream().map(DayCount::date).toList());
        assertEquals(List.of(2L, 0L, 5L, 0L), out.stream().map(DayCount::created).toList());
        assertEquals(List.of(0L, 0L, 0L, 1L), out.stream().map(DayCount::completed).toList());
    }

    @Test void byDayDocDuocCaJavaSqlDateVaLocalDate() {
        LocalDate d = LocalDate.of(2026, 11, 9);
        Map<LocalDate, Long> m = AdminStatsService.byDay(rows(new Object[] { java.sql.Date.valueOf(d), 3L },
                new Object[] { d.plusDays(1), BigDecimal.valueOf(4) }));
        assertEquals(Map.of(d, 3L, d.plusDays(1), 4L), m);
    }

    @Test void durationKhongCoDuLieuThiTrungBinhLaNull() {
        assertEquals(new DurationStat(null, 0), AdminStatsService.duration(rows()));
        assertEquals(new DurationStat(null, 0), AdminStatsService.duration(rows(new Object[] { null, 0L })));
        assertEquals(new DurationStat(12.3, 4), AdminStatsService.duration(rows(new Object[] { new BigDecimal("12.3456"), 4L })));
    }

    @Test void timelineGioiHanSoNgayVaBatDauTuNuaDemGioVietNam() {
        when(incidents.countCreatedByDay(any())).thenReturn(rows());
        when(incidents.countCompletedByDay(any())).thenReturn(rows());
        when(incidents.avgMinutesUntilFirstAssignment(any())).thenReturn(rows());
        when(incidents.avgMinutesUntilCompleted(any())).thenReturn(rows());
        when(assignments.avgMinutesOrgResponse(any())).thenReturn(rows());

        SosTimeline t = service.sosTimeline(1000);
        assertEquals(AdminStatsService.MAX_TIMELINE_DAYS, t.days());
        assertEquals(AdminStatsService.MAX_TIMELINE_DAYS, t.points().size());
        LocalDate today = LocalDate.now(VN);
        assertEquals(today, t.points().get(t.points().size() - 1).date());
        Instant expectedFrom = today.minusDays(AdminStatsService.MAX_TIMELINE_DAYS - 1L).atStartOfDay(VN).toInstant();
        verify(incidents).countCreatedByDay(expectedFrom);
        verify(assignments).avgMinutesOrgResponse(expectedFrom);

        assertEquals(1, service.sosTimeline(0).days());
    }

    @Test void overviewDemCaDangMoVaCongTienUngHoThanhCong() {
        when(incidents.countByStatusIn(List.of(IncidentStatus.PENDING, IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED))).thenReturn(7L);
        when(donations.sumAmountByStatus(DonationStatus.SUCCESS)).thenReturn(1_500_000L);
        when(postReports.countReportedPosts(ReportStatus.PENDING)).thenReturn(4L);
        when(campaignReports.countReportedCampaigns(ReportStatus.PENDING)).thenReturn(2L);
        assertEquals(new StatsOverview(7, 1_500_000, 4, 2), service.overview());
    }

    @Test void sosStatusStatsCoDuMoiTrangThai() {
        when(incidents.countByStatusBetween(any(), any())).thenReturn(rows(new Object[] { IncidentStatus.COMPLETED, 3L }));
        Map<String, Long> m = service.sosStatusStats("month", LocalDate.of(2026, 11, 10));
        assertEquals(IncidentStatus.values().length, m.size());
        assertEquals(3L, m.get("COMPLETED"));
        assertEquals(0L, m.get("PENDING"));
        verify(incidents).countByStatusBetween(LocalDate.of(2026, 11, 1).atStartOfDay(VN).toInstant(),
                LocalDate.of(2026, 12, 1).atStartOfDay(VN).toInstant());
    }
}
