package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Ghi chú xác minh (V7): ca AI xếp KHONG, Admin liên hệ người gửi rồi chọn mức khác thì bắt buộc ghi chú; ghi chú lưu vào cột
 * incidents.verification_note, mô tả gốc giữ nguyên; tổ chức và chủ ca thấy ghi chú, người dân khác thì không.
 */
class VerificationNoteIT extends AbstractAdminMapIT {
    private User admin, owner, orgUser;
    private Incident khong;

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        owner = user(Role.CITIZEN);
        orgUser = user(Role.RESCUE_ORG);
        activeOrg(orgUser, 16.05, 108.2);
        khong = sos(owner, IncidentStatus.PENDING, DangerLevel.KHONG, 0, null);
        jdbc.update("update incidents set description = 'hi mn', needs_verification = true where id = ?", khong.getId());
    }

    private String body(String level, String note) {
        return note == null ? "{\"newLevel\":\"" + level + "\"}" : "{\"newLevel\":\"" + level + "\",\"note\":\"" + note + "\"}";
    }

    @Test void xacMinhCaKhongBatBuocGhiChu() throws Exception {
        mvc.perform(post("/api/admin/sos/{id}/adjust-level", khong.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TRUNG_BINH", null)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_REQUIRED"));
        assertEquals("KHONG", jdbc.queryForObject("select danger_level from incidents where id = ?", String.class, khong.getId()));
    }

    @Test void ghiChuQuaDaiBiTuChoi() throws Exception {
        mvc.perform(post("/api/admin/sos/{id}/adjust-level", khong.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TRUNG_BINH", "a".repeat(1001))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.note").exists());
        assertNull(jdbc.queryForObject("select verification_note from incidents where id = ?", String.class, khong.getId()));
    }

    @Test void ghiChuDuocLuuVaHienChoToChucVaChuCa() throws Exception {
        String note = "Đã gọi lúc 21:40: nhà ngập 1 m, 2 người già, cần xuồng";
        mvc.perform(post("/api/admin/sos/{id}/adjust-level", khong.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("TRUNG_BINH", note)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dangerLevel").value("TRUNG_BINH"))
                .andExpect(jsonPath("$.verificationNote").value(note))
                .andExpect(jsonPath("$.description").value("hi mn")); // mô tả gốc giữ nguyên
        assertEquals(note, jdbc.queryForObject("select verification_note from incidents where id = ?", String.class, khong.getId()));

        for (User allowed : new User[] { orgUser, owner })
            mvc.perform(get("/api/sos/{id}", khong.getId()).header("Authorization", bearer(allowed)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.verificationNote").value(note))
                    .andExpect(jsonPath("$.verifiedAt").exists())
                    .andExpect(jsonPath("$.verifiedByName").doesNotExist());
        mvc.perform(get("/api/sos/{id}", khong.getId()).header("Authorization", bearer(user(Role.CITIZEN))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificationNote").doesNotExist());
    }

    @Test void caMucKhacKhongKhongNhanGhiChu() throws Exception {
        Incident thap = sos(IncidentStatus.PENDING, DangerLevel.THAP, 0, null); // người gửi khác: mỗi người chỉ một ca đang hoạt động
        mvc.perform(post("/api/admin/sos/{id}/adjust-level", thap.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CAO", "ghi chú")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("NOTE_NOT_ALLOWED"));
        mvc.perform(post("/api/admin/sos/{id}/adjust-level", thap.getId()).header("Authorization", bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON).content(body("CAO", null)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.verificationNote").doesNotExist());
    }
}
