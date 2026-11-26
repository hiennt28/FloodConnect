package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.in;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Trần số ca của bản đồ Admin (app.map.max-markers = 3 trong test này): ca đang hoạt động được ưu tiên, tổng số vẫn đếm đủ. */
@TestPropertySource(properties = "app.map.max-markers=3")
class AdminSosMapCapIT extends AbstractAdminMapIT {
    private User admin;

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        sos(IncidentStatus.PENDING, DangerLevel.CAO, 20, null);
        sos(IncidentStatus.ACCEPTED, DangerLevel.CAO, 0, null);
        sos(IncidentStatus.COMPLETED, DangerLevel.CAO, 5, 2);
        sos(IncidentStatus.CANCELLED, DangerLevel.CAO, 4, 4);
    }

    private ResultActions all() throws Exception {
        return mvc.perform(get("/api/admin/sos/map").param("status", "ALL").header("Authorization", bearer(admin))).andExpect(status().isOk());
    }

    @Test void conChoThiLapBangCaKetThucGanNhat() throws Exception {
        all().andExpect(jsonPath("$.limit").value(3))
                .andExpect(jsonPath("$.cases.length()").value(3))
                .andExpect(jsonPath("$.cases[2].code").value("MAP-3")) // 2 ca đang hoạt động + ca kết thúc gần nhất (2 ngày trước)
                .andExpect(jsonPath("$.total").value(4));
    }

    @Test void chamTranThiChiCaDangHoatDongVaVanBaoTongSo() throws Exception {
        sos(IncidentStatus.PENDING, DangerLevel.THAP, 1, null);
        sos(IncidentStatus.ASSIGNED, DangerLevel.THAP, 1, null); // 4 ca đang hoạt động > trần 3
        all().andExpect(jsonPath("$.cases.length()").value(3))
                .andExpect(jsonPath("$.cases[*].status", everyItem(in(new String[] {"PENDING", "ASSIGNED", "ACCEPTED"}))))
                .andExpect(jsonPath("$.total").value(6)); // 4 đang hoạt động + 2 kết thúc trong 7 ngày (chỉ đếm)
    }
}
