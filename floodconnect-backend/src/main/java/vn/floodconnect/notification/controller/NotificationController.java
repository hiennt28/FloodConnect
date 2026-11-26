package vn.floodconnect.notification.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.notification.dto.NotificationDtos.NotificationDto;
import vn.floodconnect.notification.service.NotificationService;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService service;

    @GetMapping public PageResponse<NotificationDto> list(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) { return service.list(page, size); }
    @GetMapping("/unread-count") public Map<String, Long> unread() { return Map.of("count", service.unreadCount()); }
    @PutMapping("/{id}/read") public MessageResponse read(@PathVariable Long id) { service.markRead(id); return new MessageResponse("OK"); }
    @PutMapping("/read-all") public Map<String, Integer> readAll() { return Map.of("updated", service.markAllRead()); }
}
