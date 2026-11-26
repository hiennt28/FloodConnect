package vn.floodconnect.incident.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.incident.dto.IncidentDtos.ActiveSosList;
import vn.floodconnect.incident.dto.IncidentDtos.CreateSosResponse;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.dto.IncidentDtos.MapData;
import vn.floodconnect.incident.dto.IncidentDtos.MarkSafeRequest;
import vn.floodconnect.incident.dto.IncidentDtos.SosCreateRequest;
import vn.floodconnect.incident.dto.IncidentDtos.SosUpdateRequest;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.service.ActiveSosService;
import vn.floodconnect.incident.service.MapService;
import vn.floodconnect.incident.service.SosService;
import java.util.List;

/** Chức năng SOS của người dân (mọi tài khoản đã đăng nhập) + bản đồ cứu hộ. */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SosController {
    private final SosService sos;
    private final MapService map;
    private final ActiveSosService active;

    /** Ca SOS chỉ gồm chữ (mô tả) và vị trí, không kèm ảnh. Nếu có ca tương tự (500 m / 30 phút) và forceCreate != true:
     *  trả duplicateWarning=true, chưa tạo ca. */
    @PostMapping("/sos")
    public CreateSosResponse create(@RequestBody SosCreateRequest r) { return sos.create(r); }

    @GetMapping("/sos/mine") public List<IncidentDto> mine(@RequestParam(required = false) IncidentStatus status) { return sos.mine(status); }

    @GetMapping("/sos")
    public PageResponse<IncidentDto> all(@RequestParam(required = false) IncidentStatus status, @RequestParam(required = false) DangerLevel level,
                                         @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return sos.all(status, level, page, size); }

    /** Mọi ca đang hoạt động (luôn đủ, trần app.sos.active-max); ca đã kết thúc xem theo trang ở GET /api/sos?status=COMPLETED. */
    @GetMapping("/sos/active") public ActiveSosList active() { return active.list(); }

    @GetMapping("/sos/{id}") public IncidentDto detail(@PathVariable Long id) { return sos.detail(id); }

    /** Sửa mô tả ca SOS (vị trí không đổi được), chỉ khi ca còn Chờ xử lý; Đã phân công chỉ hủy được, Đã tiếp nhận chỉ báo an toàn. */
    @PutMapping("/sos/{id}")
    public IncidentDto update(@PathVariable Long id, @RequestBody SosUpdateRequest r) { return sos.update(id, r); }

    @PostMapping("/sos/{id}/cancel") public IncidentDto cancel(@PathVariable Long id) { return sos.cancel(id); }

    /** Người dân báo "Tôi đã an toàn" cho ca Đã tiếp nhận: ca chuyển Hoàn thành, tổ chức được báo dừng cứu hộ. Ghi chú không bắt buộc. */
    @PostMapping("/sos/{id}/safe")
    public IncidentDto markSafe(@PathVariable Long id, @Valid @RequestBody(required = false) MarkSafeRequest r) {
        return sos.markSafe(id, r == null ? null : r.note());
    }

    // ---- bản đồ cứu hộ (marker theo vai trò; ca SOS trên bản đồ của Admin: /api/admin/sos/map) ----
    @GetMapping("/map/markers") public MapData markers() { return map.markers(); }
}
