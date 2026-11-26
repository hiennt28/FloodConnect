package vn.floodconnect.rescue.service;

import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.CriteriaBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.incident.service.StatusFilter;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.rescue.dto.RescueDtos.AdminMapCase;
import vn.floodconnect.rescue.dto.RescueDtos.AdminSosMap;
import vn.floodconnect.rescue.dto.RescueDtos.OrgRef;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Bản đồ điều phối của Admin.
 * - Ca đang hoạt động (Chờ xử lý, Đã phân công, Đã tiếp nhận, kể cả ca chờ xác minh) luôn hiện đủ, dù gửi đã lâu: đây là việc cần làm.
 * - Ca đã kết thúc (Hoàn thành, Đã hủy, Không hợp lệ) chỉ để xem lại: chỉ lấy ca kết thúc trong `days` ngày gần đây.
 * - Mỗi lần trả tối đa `app.map.max-markers` ca (mặc định 1000): ca đang hoạt động trước, phần còn lại cho ca kết thúc gần nhất.
 *   Trả kèm tổng số ca khớp bộ lọc để giao diện báo "đang hiển thị X / Y ca" khi chạm trần.
 * Lọc hoàn toàn trong CSDL (chỉ mục incidents(status, created_at)), không tải cả bảng ca rồi lọc bằng Java.
 */
@Service
public class AdminSosMapService {
    public static final int DEFAULT_DAYS = 7;
    public static final int MAX_DAYS = 90;

    private final IncidentRepository incidents;
    private final int limit;

    public AdminSosMapService(IncidentRepository incidents, @Value("${app.map.max-markers:1000}") int limit) {
        this.incidents = incidents;
        this.limit = Math.max(1, limit);
    }

    /**
     * @param status ACTIVE (mặc định của giao diện) | ALL hoặc rỗng (mọi trạng thái) | một trạng thái cụ thể
     * @param level  mức nguy hiểm; null = mọi mức
     * @param days   khoảng thời gian cho ca đã kết thúc, 1..90 ngày; null = 7. Không áp dụng cho ca đang hoạt động
     */
    @Transactional(readOnly = true)
    public AdminSosMap cases(String status, DangerLevel level, Integer days) {
        Set<IncidentStatus> wanted = statuses(status);
        int d = days == null ? DEFAULT_DAYS : Math.max(1, Math.min(days, MAX_DAYS));
        Instant since = Instant.now().minus(Duration.ofDays(d));

        Set<IncidentStatus> active = EnumSet.noneOf(IncidentStatus.class), ended = EnumSet.noneOf(IncidentStatus.class);
        for (IncidentStatus s : wanted) (SosService.ACTIVE.contains(s) ? active : ended).add(s);

        List<Incident> found = new ArrayList<>();
        long total = 0;
        if (!active.isEmpty()) {
            Page<Incident> p = incidents.findAll(filter(active, level), PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
            found.addAll(p.getContent());
            total += p.getTotalElements();
        }
        if (!ended.isEmpty()) {
            Specification<Incident> spec = filter(ended, level).and(endedSince(since));
            int room = limit - found.size();
            if (room > 0) {
                Page<Incident> p = incidents.findAll(spec, PageRequest.of(0, room)); // thứ tự: kết thúc gần nhất trước (đặt trong endedSince)
                found.addAll(p.getContent());
                total += p.getTotalElements();
            } else total += incidents.count(spec); // đã đủ trần: chỉ đếm để báo tổng số
        }
        return new AdminSosMap(found.stream().map(AdminSosMapService::toCase).toList(), total, limit, d);
    }

    /** Chuỗi trạng thái từ giao diện sang tập trạng thái cần lấy (ACTIVE | ENDED | ALL | một trạng thái, xem StatusFilter). */
    static Set<IncidentStatus> statuses(String status) { return StatusFilter.parse(status); }

    private static Specification<Incident> filter(Set<IncidentStatus> st, DangerLevel level) {
        return (root, q, cb) -> {
            Predicate p = root.get("status").in(st);
            return level == null ? p : cb.and(p, cb.equal(root.get("dangerLevel"), level));
        };
    }

    /**
     * Thời điểm kết thúc: hoàn thành (completedAt), không hợp lệ (invalidatedAt); ca đã hủy không có cột riêng nên dùng lần cập nhật
     * cuối (chính là lúc hủy, ca đã kết thúc không còn bị sửa). Câu đếm không cần sắp xếp nên chỉ thêm ORDER BY cho câu lấy dữ liệu.
     */
    private static Specification<Incident> endedSince(Instant since) {
        return (root, q, cb) -> {
            Expression<Instant> endedAt = endedAt(root, cb);
            if (q.getResultType() != Long.class && q.getResultType() != long.class) q.orderBy(cb.desc(endedAt));
            return cb.greaterThanOrEqualTo(endedAt, since);
        };
    }

    private static Expression<Instant> endedAt(Root<Incident> root, CriteriaBuilder cb) {
        return cb.<Instant>coalesce().value(root.<Instant>get("completedAt")).value(root.<Instant>get("invalidatedAt"))
                .value(root.<Instant>get("updatedAt")).value(root.<Instant>get("createdAt"));
    }

    private static AdminMapCase toCase(Incident i) {
        RescueOrganization o = i.getAssignedOrg(); // nạp theo lô (default_batch_fetch_size), không một truy vấn mỗi ca
        return new AdminMapCase(i.getId(), i.getCode(), i.getLatitude(), i.getLongitude(), i.getStatus(), i.getDangerLevel(),
                i.isNeedsVerification(), i.getAddress(), o == null ? null : new OrgRef(o.getId(), o.getName()),
                i.getCreatedAt(), i.getCompletedAt(), i.getInvalidatedAt());
    }
}
