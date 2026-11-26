package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.in;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #12: ca đang hoạt động luôn tải đủ, ca đã kết thúc xem theo trang. Tình huống gốc: một ca Chờ xử lý gửi 30 ngày trước và
 * 120 ca đã hoàn thành mới hơn; danh sách cũ (100 ca mới nhất mọi trạng thái) làm mất ca đang chờ cứu này.
 */
class ActiveSosIT extends AbstractAdminMapIT {
    private User admin, citizen, orgUser;
    private RescueOrganization team;
    private Incident oldPending, needsReview, accepted;

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        citizen = user(Role.CITIZEN);
        orgUser = user(Role.RESCUE_ORG);
        team = activeOrg(orgUser, 16.05, 108.2);
        oldPending = sos(IncidentStatus.PENDING, DangerLevel.CAO, 30, null);
        needsReview = sos(IncidentStatus.PENDING, DangerLevel.KHONG, 1, null);       // chờ Admin xác minh: chỉ Admin thấy
        accepted = sos(IncidentStatus.ACCEPTED, DangerLevel.KHAN_CAP, 2, null);
        assign(accepted);
        for (int k = 0; k < 120; k++) sos(citizen, IncidentStatus.COMPLETED, DangerLevel.THAP, 0, 0);
        Incident done = sos(IncidentStatus.COMPLETED, DangerLevel.CAO, 5, 4);
        assign(done);
        Incident cancelled = sos(IncidentStatus.CANCELLED, DangerLevel.CAO, 6, 6);
        assign(cancelled);
    }

    /** Gán ca cho tổ chức bằng SQL (save lại thực thể sẽ ghi đè thời điểm gửi vừa sửa trong CSDL). */
    private void assign(Incident i) { jdbc.update("update incidents set assigned_org_id = ? where id = ?", team.getId(), i.getId()); }

    @Test void danhSachCuBoSotCaChoCuuGuiSom() throws Exception {
        mvc.perform(get("/api/sos").param("size", "100").header("Authorization", bearer(citizen)))
                .andExpect(jsonPath("$.items[*].code", not(hasItem(oldPending.getCode()))));
    }

    @Test void nguoiDanThayDuCaDangHoatDongTruCaChoXacMinh() throws Exception {
        mvc.perform(get("/api/sos/active").header("Authorization", bearer(citizen))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.limit").value(1000))
                .andExpect(jsonPath("$.items[*].code", hasItem(oldPending.getCode())))
                .andExpect(jsonPath("$.items[*].code", not(hasItem(needsReview.getCode()))))
                .andExpect(jsonPath("$.items[*].description", everyItem(nullValue()))); // ca của người khác: ẩn mô tả
    }

    @Test void adminThayCaCaChoXacMinh() throws Exception {
        mvc.perform(get("/api/sos/active").header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.items[*].code", hasItem(needsReview.getCode())));
    }

    @Test void caDaHoanThanhXemTheoTrang() throws Exception {
        mvc.perform(get("/api/sos").param("status", "COMPLETED").param("page", "1").param("size", "10").header("Authorization", bearer(citizen)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(121)).andExpect(jsonPath("$.items.length()").value(10));
    }

    @Test void adminXemCaDaKetThucTheoTrang() throws Exception {
        mvc.perform(get("/api/admin/sos").param("status", "ENDED").param("size", "20").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(122))
                .andExpect(jsonPath("$.items.length()").value(20))
                .andExpect(jsonPath("$.items[*].status", everyItem(in(new String[] {"COMPLETED", "CANCELLED", "INVALID"}))));
        mvc.perform(get("/api/admin/sos").param("status", "PENDING").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(2));
        mvc.perform(get("/api/admin/sos").param("status", "DONE").header("Authorization", bearer(admin)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_STATUS"));
    }

    @Test void toChucTaiCaDangGiaoVaLichSuTheoTrang() throws Exception {
        mvc.perform(get("/api/org/sos").param("filter", "ACTIVE").header("Authorization", bearer(orgUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].code").value(accepted.getCode()));
        mvc.perform(get("/api/org/sos/history").param("size", "1").header("Authorization", bearer(orgUser))).andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.last").value(false))
                .andExpect(jsonPath("$.items[0].status").value("COMPLETED")); // mới nhất trước
    }
}
