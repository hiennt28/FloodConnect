package vn.floodconnect.stats.controller;

import lombok.RequiredArgsConstructor;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.floodconnect.stats.dto.PublicStatsDtos.PublicStats;
import vn.floodconnect.stats.service.PublicStatsService;

/** API công khai (không cần JWT, xem SecurityConfig: GET /api/public/**). Chỉ trả con số tổng hợp. */
@RestController
@RequestMapping("/api/public")
@RequiredArgsConstructor
public class PublicStatsController {
    private final PublicStatsService stats;

    @GetMapping("/stats") public PublicStats stats() { return stats.stats(); }
    /** Số tổ chức đang Sẵn sàng nhận ca (ô "Đội sẵn sàng" ở Bảng tin). Đếm mỗi lần gọi, không lưu đệm như /stats: trạng thái đổi thường xuyên. */
    @GetMapping("/available-orgs") public Map<String, Long> availableOrgs() { return Map.of("count", stats.availableOrgs()); }
}
