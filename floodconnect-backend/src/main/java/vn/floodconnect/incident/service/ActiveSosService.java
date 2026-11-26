package vn.floodconnect.incident.service;

import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.incident.dto.IncidentDtos.ActiveSosList;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;

/**
 * Mọi ca SOS đang hoạt động (Chờ xử lý, Đã phân công, Đã tiếp nhận), dùng cho trang Điều phối của Admin, tab "Tất cả" của
 * người dân, bản đồ và khối "SOS gần bạn". Ca đang hoạt động là việc cần làm nên luôn trả ĐỦ, kể cả ca gửi đã lâu
 * (trước đây giao diện chỉ tải 100 ca mới nhất mọi trạng thái, ca chờ cứu gửi sớm bị ca đã xong đẩy ra ngoài).
 * Ca đã kết thúc không ở đây: xem theo trang qua GET /api/sos?status=COMPLETED (công khai) hoặc /api/admin/sos?status=ENDED.
 * Trần an toàn app.sos.active-max (mặc định 1000) kèm tổng số để giao diện báo khi chạm trần.
 * Admin thấy cả ca nhãn KHONG đang chờ xác minh; người khác không thấy. Ẩn thông tin theo quyền do IncidentMapper đảm nhận.
 */
@Service
public class ActiveSosService {
    private final IncidentRepository incidents;
    private final IncidentMapper incidentMapper;
    private final CurrentUser current;
    private final int limit;

    public ActiveSosService(IncidentRepository incidents, IncidentMapper incidentMapper,
                            CurrentUser current, @Value("${app.sos.active-max:1000}") int limit) {
        this.incidents = incidents;
        this.incidentMapper = incidentMapper;
        this.current = current;
        this.limit = Math.max(1, limit);
    }

    @Transactional(readOnly = true)
    public ActiveSosList list() {
        User u = current.get();
        boolean admin = u.getRole() == Role.ADMIN;
        Specification<Incident> spec = (root, q, cb) -> {
            Predicate p = root.get("status").in(StatusFilter.ACTIVE);
            return admin ? p : cb.and(p, cb.notEqual(root.get("dangerLevel"), DangerLevel.KHONG));
        };
        Page<Incident> page = incidents.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));
        return new ActiveSosList(incidentMapper.toDtos(page.getContent(), u), page.getTotalElements(), limit);
    }
}
