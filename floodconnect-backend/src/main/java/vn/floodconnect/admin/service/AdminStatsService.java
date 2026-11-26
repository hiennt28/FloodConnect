package vn.floodconnect.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.campaign.repository.CampaignReportRepository;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.post.repository.PostReportRepository;
import vn.floodconnect.admin.dto.AdminDtos.DayCount;
import vn.floodconnect.admin.dto.AdminDtos.DurationStat;
import vn.floodconnect.admin.dto.AdminDtos.SosTimeline;
import vn.floodconnect.admin.dto.AdminDtos.StatsOverview;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.UserRepository;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/** Thống kê cho trang tổng quan của Admin. */
@Service
@RequiredArgsConstructor
public class AdminStatsService {
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int MAX_TIMELINE_DAYS = 90;
    private static final List<IncidentStatus> OPEN = List.of(IncidentStatus.PENDING, IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED);

    private final UserRepository users;
    private final RescueOrganizationRepository orgs;
    private final IncidentRepository incidents;
    private final DonationRepository donations;
    private final IncidentAssignmentRepository assignments;
    private final PostReportRepository postReports;
    private final CampaignReportRepository campaignReports;

    // =====================================================================
    //  Thống kê (trang tổng quan)
    // =====================================================================
    @Transactional(readOnly = true)
    public Map<String, Long> citizenStats() {
        Map<String, Long> m = new LinkedHashMap<>();
        m.put("ACTIVE", users.countByRoleAndStatus(Role.CITIZEN, UserStatus.ACTIVE));
        m.put("LOCKED", users.countByRoleAndStatus(Role.CITIZEN, UserStatus.LOCKED));
        return m;
    }

    /** period = day | month | year; date (yyyy-MM-dd) mặc định hôm nay (giờ Việt Nam). */
    @Transactional(readOnly = true)
    public Map<String, Long> sosStats(String period, LocalDate date) {
        Instant[] r = range(period, date);
        Map<String, Long> m = new LinkedHashMap<>();
        for (DangerLevel l : List.of(DangerLevel.KHAN_CAP, DangerLevel.CAO, DangerLevel.TRUNG_BINH, DangerLevel.THAP, DangerLevel.KHONG)) m.put(l.name(), 0L);
        for (Object[] row : incidents.countByLevelBetween(r[0], r[1]))
            m.put(((DangerLevel) row[0]).name(), (Long) row[1]);
        return m;
    }

    /** Số ca theo trạng thái, cùng khoảng thời gian với sosStats. Trước đây giao diện tự đếm trên 100 ca mới nhất nên sai khi có nhiều ca. */
    @Transactional(readOnly = true)
    public Map<String, Long> sosStatusStats(String period, LocalDate date) {
        Instant[] r = range(period, date);
        Map<String, Long> m = new LinkedHashMap<>();
        for (IncidentStatus s : IncidentStatus.values()) m.put(s.name(), 0L);
        for (Object[] row : incidents.countByStatusBetween(r[0], r[1]))
            m.put(((IncidentStatus) row[0]).name(), (Long) row[1]);
        return m;
    }

    /** Ô KPI: đếm/cộng trên toàn bộ dữ liệu trong cơ sở dữ liệu, không phụ thuộc số bản ghi giao diện đã tải. */
    @Transactional(readOnly = true)
    public StatsOverview overview() {
        // Báo cáo chờ xử lý: số bài / chiến dịch đang có báo cáo chưa kết luận (một bài nhiều báo cáo tính một), đếm trong CSDL
        return new StatsOverview(incidents.countByStatusIn(OPEN), donations.sumAmountByStatus(DonationStatus.SUCCESS),
                postReports.countReportedPosts(ReportStatus.PENDING), campaignReports.countReportedCampaigns(ReportStatus.PENDING));
    }

    /** Biểu đồ theo ngày cho N ngày gần nhất (tính cả hôm nay, giờ Việt Nam) và thời gian phản hồi trong cùng khoảng. */
    @Transactional(readOnly = true)
    public SosTimeline sosTimeline(int days) {
        int n = Math.max(1, Math.min(days, MAX_TIMELINE_DAYS));
        LocalDate today = LocalDate.now(VN);
        LocalDate first = today.minusDays(n - 1L);
        Instant from = first.atStartOfDay(VN).toInstant();
        List<DayCount> points = fillDays(first, today, byDay(incidents.countCreatedByDay(from)), byDay(incidents.countCompletedByDay(from)));
        return new SosTimeline(n, points,
                duration(incidents.avgMinutesUntilFirstAssignment(from)),
                duration(assignments.avgMinutesOrgResponse(from)),
                duration(incidents.avgMinutesUntilCompleted(from)));
    }

    /** Điền đủ từng ngày từ first đến last; ngày không có ca = 0 để đường biểu đồ không nối tắt qua ngày trống. */
    static List<DayCount> fillDays(LocalDate first, LocalDate last, Map<LocalDate, Long> created, Map<LocalDate, Long> completed) {
        List<DayCount> out = new ArrayList<>();
        for (LocalDate d = first; !d.isAfter(last); d = d.plusDays(1))
            out.add(new DayCount(d, created.getOrDefault(d, 0L), completed.getOrDefault(d, 0L)));
        return out;
    }

    /** Hàng [ngày, số lượng] từ truy vấn native. Tùy driver, cột ngày về dạng java.sql.Date hoặc LocalDate. */
    static Map<LocalDate, Long> byDay(List<Object[]> rows) {
        Map<LocalDate, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            LocalDate d = r[0] instanceof java.sql.Date sd ? sd.toLocalDate() : (LocalDate) r[0];
            m.put(d, ((Number) r[1]).longValue());
        }
        return m;
    }

    /** Hàng [số phút trung bình, số lượng]. Không có dữ liệu thì trung bình là null (giao diện hiện "—", không hiện "0 phút"). */
    static DurationStat duration(List<Object[]> rows) {
        if (rows.isEmpty()) return new DurationStat(null, 0);
        Object[] r = rows.get(0);
        long count = r[1] == null ? 0 : ((Number) r[1]).longValue();
        Double avg = r[0] == null || count == 0 ? null : Math.round(((Number) r[0]).doubleValue() * 10) / 10.0;
        return new DurationStat(avg, count);
    }

    /** [from, to) của period = day | month | year chứa ngày date (mặc định hôm nay, giờ Việt Nam). */
    private static Instant[] range(String period, LocalDate date) {
        LocalDate d = date == null ? LocalDate.now(VN) : date;
        String p = period == null ? "day" : period.toLowerCase();
        LocalDate from = switch (p) {
            case "month" -> d.withDayOfMonth(1);
            case "year" -> d.withDayOfYear(1);
            default -> d;
        };
        LocalDate to = switch (p) {
            case "month" -> from.plusMonths(1);
            case "year" -> from.plusYears(1);
            default -> from.plusDays(1);
        };
        return new Instant[] { from.atStartOfDay(VN).toInstant(), to.atStartOfDay(VN).toInstant() };
    }

    @Transactional(readOnly = true)
    public Map<String, Object> orgStats() {
        Map<String, Object> m = new LinkedHashMap<>();
        for (OrgStatus s : OrgStatus.values()) m.put(s.name(), orgs.countByStatus(s));
        Map<String, Long> op = orgs.findByStatus(OrgStatus.ACTIVE).stream()
                .collect(Collectors.groupingBy(o -> o.getOperatingStatus().name(), LinkedHashMap::new, Collectors.counting()));
        m.put("activeByOperatingStatus", op);
        return m;
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> topOrgs() {
        return donations.topOrgs(DonationStatus.SUCCESS, PageRequest.of(0, 10)).stream()
                .map(r -> Map.<String, Object>of("id", r[0], "name", r[1], "totalAmount", r[2])).toList();
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> topDonors() {
        return donations.topDonors(DonationStatus.SUCCESS, PageRequest.of(0, 10)).stream()
                .map(r -> Map.<String, Object>of("id", r[0], "name", r[1], "totalAmount", r[2])).toList();
    }
}
