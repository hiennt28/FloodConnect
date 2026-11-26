package vn.floodconnect.it;

import org.springframework.beans.factory.annotation.Autowired;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;

/** Dữ liệu chung cho test bản đồ điều phối của Admin: tạo ca với thời điểm gửi / kết thúc tùy ý (sửa thẳng trong CSDL). */
abstract class AbstractAdminMapIT extends AbstractIT {
    @Autowired protected IncidentRepository incidents;
    private int n = 0;

    /** Ca gửi cách đây createdDaysAgo ngày; ca đã kết thúc thì kết thúc cách đây endedDaysAgo ngày. Mã ca MAP-1, MAP-2... theo thứ tự tạo. */
    protected Incident sos(IncidentStatus st, DangerLevel level, int createdDaysAgo, Integer endedDaysAgo) {
        return sos(user(Role.CITIZEN), st, level, createdDaysAgo, endedDaysAgo);
    }

    /** Như trên nhưng cho người gửi có sẵn (tạo nhiều ca đã kết thúc nhanh hơn: không tạo tài khoản và băm mật khẩu cho mỗi ca). */
    protected Incident sos(User citizen, IncidentStatus st, DangerLevel level, int createdDaysAgo, Integer endedDaysAgo) {
        Incident i = new Incident();
        i.setCode("MAP-" + (++n)); i.setCitizen(citizen); i.setDescription("Nước ngập");
        i.setLatitude(16.05 + n * 0.01); i.setLongitude(108.2); i.setStatus(st); i.setDangerLevel(level); i.setAddress("Địa chỉ " + n);
        i = incidents.save(i);
        Instant now = Instant.now();
        Timestamp created = Timestamp.from(now.minus(Duration.ofDays(createdDaysAgo)));
        jdbc.update("update incidents set created_at = ?, updated_at = ? where id = ?", created, created, i.getId());
        if (endedDaysAgo != null) {
            Timestamp ended = Timestamp.from(now.minus(Duration.ofDays(endedDaysAgo)));
            switch (st) {
                case COMPLETED -> jdbc.update("update incidents set completed_at = ? where id = ?", ended, i.getId());
                case INVALID -> jdbc.update("update incidents set invalidated_at = ? where id = ?", ended, i.getId());
                default -> jdbc.update("update incidents set updated_at = ? where id = ?", ended, i.getId()); // ca hủy: lần cập nhật cuối
            }
        }
        return i;
    }
}
