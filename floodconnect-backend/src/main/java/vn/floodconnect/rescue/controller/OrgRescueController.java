package vn.floodconnect.rescue.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.rescue.dto.RescueDtos.RejectRequest;
import java.util.List;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.rescue.service.RescueService;

/** Tổ chức cứu hộ xử lý ca SOS (role RESCUE_ORG). */
@RestController
@RequestMapping("/api/org")
@RequiredArgsConstructor
public class OrgRescueController {
    private final RescueService rescue;

    // ---- ca SOS ----
    @GetMapping("/sos/suggested") public List<IncidentDto> suggested() { return rescue.suggested(); }
    /** filter = ACTIVE (Đã phân công + Đã tiếp nhận) | PENDING | ASSIGNED | ACCEPTED | COMPLETED | ALL */
    @GetMapping("/sos") public List<IncidentDto> cases(@RequestParam(defaultValue = "ALL") String filter) { return rescue.orgCases(filter); }
    /** Lịch sử ca của tổ chức (Hoàn thành, Đã hủy), mới nhất trước, theo trang. */
    @GetMapping("/sos/history")
    public PageResponse<IncidentDto> history(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) { return rescue.history(page, size); }
    @PostMapping("/sos/{id}/claim") public IncidentDto claim(@PathVariable Long id) { return rescue.claim(id); }
    @PostMapping("/sos/{id}/accept") public IncidentDto accept(@PathVariable Long id) { return rescue.acceptAssigned(id); }
    @PostMapping("/sos/{id}/reject") public IncidentDto reject(@PathVariable Long id, @RequestBody(required = false) RejectRequest r) { return rescue.rejectAssigned(id, r == null ? null : r.reason()); }
    @PostMapping("/sos/{id}/complete") public IncidentDto complete(@PathVariable Long id) { return rescue.complete(id); }
}
