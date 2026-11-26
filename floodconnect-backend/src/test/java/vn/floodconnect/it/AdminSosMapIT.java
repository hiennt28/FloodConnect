package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Bản đồ điều phối của Admin (GET /api/admin/sos/map) trên CSDL thật: ca đang hoạt động hiện đủ dù gửi đã lâu,
 * ca đã kết thúc chỉ trong số ngày đã chọn (tính theo lúc kết thúc), lọc theo mức, chỉ Admin gọi được.
 */
class AdminSosMapIT extends AbstractAdminMapIT {
    private User admin;
    private Incident oldPending, recentDone, oldDone, recentCancelled, oldInvalid;

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        oldPending = sos(IncidentStatus.PENDING, DangerLevel.KHAN_CAP, 30, null);         // gửi 30 ngày trước, vẫn chờ xử lý
        sos(IncidentStatus.ACCEPTED, DangerLevel.CAO, 0, null);
        recentDone = sos(IncidentStatus.COMPLETED, DangerLevel.CAO, 12, 2);                // gửi 12 ngày trước, xong 2 ngày trước
        oldDone = sos(IncidentStatus.COMPLETED, DangerLevel.CAO, 15, 10);
        recentCancelled = sos(IncidentStatus.CANCELLED, DangerLevel.THAP, 3, 3);
        oldInvalid = sos(IncidentStatus.INVALID, DangerLevel.KHONG, 20, 20);
    }

    private ResultActions map(String... params) throws Exception {
        var req = get("/api/admin/sos/map").header("Authorization", bearer(admin));
        for (int k = 0; k + 1 < params.length; k += 2) req = req.param(params[k], params[k + 1]);
        return mvc.perform(req).andExpect(status().isOk());
    }

    @Test void macDinhLaMoiCaDangHoatDongKeCaCaGuiDaLau() throws Exception {
        map().andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.cases[*].status", containsInAnyOrder("PENDING", "ACCEPTED")))
                .andExpect(jsonPath("$.cases[?(@.code == '" + oldPending.getCode() + "')].address").value("Địa chỉ 1"));
    }

    @Test void moiTrangThaiChiLayCaKetThucTrong7Ngay() throws Exception {
        map("status", "ALL").andExpect(jsonPath("$.days").value(7)).andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.cases[*].code", containsInAnyOrder(oldPending.getCode(), "MAP-2", recentDone.getCode(), recentCancelled.getCode())));
        map("status", "ALL", "days", "30").andExpect(jsonPath("$.total").value(6));
    }

    @Test void caKetThucTinhTheoLucKetThucKhongTheoLucGui() throws Exception {
        // recentDone gửi 12 ngày trước nhưng mới hoàn thành 2 ngày trước: vẫn nằm trong 7 ngày
        map("status", "COMPLETED").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.cases[0].code").value(recentDone.getCode()));
        // 30 ngày: kết thúc gần nhất trước
        map("status", "COMPLETED", "days", "30").andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.cases[0].code").value(recentDone.getCode()))
                .andExpect(jsonPath("$.cases[1].code").value(oldDone.getCode()));
        map("status", "INVALID", "days", "30").andExpect(jsonPath("$.cases[0].code").value(oldInvalid.getCode()));
    }

    @Test void locTheoMuc() throws Exception {
        map("status", "ALL", "days", "30", "level", "CAO").andExpect(jsonPath("$.total").value(3));
    }

    @Test void trangThaiSaiBao400() throws Exception {
        mvc.perform(get("/api/admin/sos/map").param("status", "DONE").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test void chiAdminGoiDuoc() throws Exception {
        mvc.perform(get("/api/admin/sos/map").header("Authorization", bearer(user(Role.CITIZEN)))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/sos/map")).andExpect(status().isUnauthorized());
    }

    @Test void diemToChucVanTraVeChoAdmin() throws Exception {
        activeOrg(user(Role.RESCUE_ORG), 16.1, 108.2);
        mvc.perform(get("/api/map/markers").header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.markers.length()").value(1))
                .andExpect(jsonPath("$.markers[0].kind").value("ORG")); // ca SOS của Admin không còn tải ở đây
    }
}
