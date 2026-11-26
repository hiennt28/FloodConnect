package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import vn.floodconnect.common.util.TokenUtil;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * REVIEW-004 đợt 3 trên CSDL thật: chỉ Admin gốc quản lý tài khoản Admin; Admin mới tự đặt mật khẩu bằng mã gửi qua email;
 * Admin bị vô hiệu hóa bị chặn ngay (kể cả access token cũ còn hạn); Admin chưa kích hoạt không bị tự xóa; ràng buộc của V11.
 */
class AdminAccountsIT extends AbstractIT {

    private User root() {
        User r = user(Role.ADMIN);
        r.setRootAdmin(true);
        return users.save(r);
    }

    private static String json(String s) { return s.replace('\'', '"'); }

    @Test void adminThuongKhongQuanLyDuocTaiKhoanAdmin() throws Exception {
        root();
        User normal = user(Role.ADMIN);
        mvc.perform(get("/api/admin/admins").header("Authorization", bearer(normal)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ROOT_ADMIN_ONLY"));
        mvc.perform(post("/api/admin/admins").header("Authorization", bearer(normal)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'Người mới','email':'moi@it.test','phone':'0988000111'}")))
                .andExpect(status().isForbidden());
        assertFalse(users.findByEmailIgnoreCase("moi@it.test").isPresent());
        // Admin thường vẫn làm nghiệp vụ bình thường
        mvc.perform(get("/api/admin/sos").header("Authorization", bearer(normal))).andExpect(status().isOk());
    }

    @Test void adminGocTaoAdminMoiNguoiDoTuDatMatKhauRoiDangNhap() throws Exception {
        User root = root();
        mvc.perform(post("/api/admin/admins").header("Authorization", bearer(root)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'Trần Điều Phối','email':'DieuPhoi@It.Test','phone':'0988000222'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("INVITED"))
                .andExpect(jsonPath("$.rootAdmin").value(false));
        User created = users.findByEmailIgnoreCase("dieuphoi@it.test").orElseThrow();
        assertEquals(Role.ADMIN, created.getRole());
        assertFalse(created.isEmailVerified());
        assertEquals(1, jdbc.queryForObject("select count(*) from auth_tokens where user_id = ? and type = 'PASSWORD_RESET'",
                Integer.class, created.getId()), "phải gửi mã đặt mật khẩu");

        // Mã thật chỉ có trong email: thay bằng mã đã biết để đi tiếp đúng luồng "Đặt lại mật khẩu" như người nhận
        jdbc.update("update auth_tokens set token_hash = ? where user_id = ?", TokenUtil.sha256("246810"), created.getId());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'email':'dieuphoi@it.test','code':'246810','newPassword':'DieuPhoi2026','confirmPassword':'DieuPhoi2026'}")))
                .andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'identifier':'dieuphoi@it.test','password':'DieuPhoi2026'}")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN")).andExpect(jsonPath("$.user.rootAdmin").value(false));

        mvc.perform(get("/api/admin/admins").header("Authorization", bearer(root)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rootAdmin").value(true)) // Admin gốc đứng đầu
                .andExpect(jsonPath("$[1].state").value("ACTIVE"));
    }

    @Test void taoAdminTrungEmailHoacSoDienThoaiBiTuChoi() throws Exception {
        User root = root();
        User citizen = user(Role.CITIZEN);
        mvc.perform(post("/api/admin/admins").header("Authorization", bearer(root)).contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'A','email':'" + citizen.getEmail() + "','phone':'" + citizen.getPhone() + "'}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.phone").exists());
    }

    @Test void adminBiVoHieuHoaBiChanNgayVaMoLaiDuoc() throws Exception {
        User root = root();
        User other = user(Role.ADMIN);
        String oldToken = bearer(other); // access token cấp trước khi bị vô hiệu hóa, còn hạn

        mvc.perform(post("/api/admin/admins/" + other.getId() + "/disable").header("Authorization", bearer(root))
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'reason':'Hết nhiệm kỳ trực'}")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("DISABLED"))
                .andExpect(jsonPath("$.disabledReason").value("Hết nhiệm kỳ trực"));

        mvc.perform(get("/api/admin/sos").header("Authorization", oldToken))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_DISABLED"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'identifier':'" + other.getEmail() + "','password':'Demo12345'}")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ADMIN_DISABLED"));

        mvc.perform(post("/api/admin/admins/" + other.getId() + "/enable").header("Authorization", bearer(root)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.state").value("ACTIVE"));
        mvc.perform(get("/api/admin/sos").header("Authorization", oldToken)).andExpect(status().isOk());
        Map<String, Object> lock = jdbc.queryForMap("select reason, unlocked_at, unlocked_by_id from account_locks where user_id = ?", other.getId());
        assertEquals("Hết nhiệm kỳ trực", lock.get("reason"));
        assertNotNull(lock.get("unlocked_at"));
        assertEquals(root.getId(), ((Number) lock.get("unlocked_by_id")).longValue());
    }

    @Test void khongAiVoHieuHoaDuocAdminGoc() throws Exception {
        User root = root();
        mvc.perform(post("/api/admin/admins/" + root.getId() + "/disable").header("Authorization", bearer(root))
                        .contentType(MediaType.APPLICATION_JSON).content(json("{'reason':'Thử'}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("CANNOT_DISABLE_ROOT"));
        assertFalse(users.findById(root.getId()).orElseThrow().isLocked());
    }

    /** Admin mới chưa kịp đặt mật khẩu quá 24 giờ: không bị coi là "đăng ký bỏ dở" và bị xóa khi có người đăng ký trùng email. */
    @Test void adminChuaKichHoatKhongBiTuXoa() throws Exception {
        User invited = user(Role.ADMIN);
        invited.setEmailVerified(false); users.save(invited);
        jdbc.update("update users set created_at = now() - interval '25 hours' where id = ?", invited.getId());

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(json("{'fullName':'Người dân','email':'" + invited.getEmail() + "','phone':'0988000333',"
                                + "'password':'Demo12345','confirmPassword':'Demo12345'}")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.email").exists());
        assertTrue(users.findById(invited.getId()).isPresent());
    }

    /** V11: chỉ tài khoản ADMIN mới là Admin gốc, và chỉ có đúng một Admin gốc. */
    @Test void rangBuocCuaAdminGoc() {
        root();
        User citizen = user(Role.CITIZEN);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update users set root_admin = true where id = ?", citizen.getId()));
        User second = user(Role.ADMIN);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("update users set root_admin = true where id = ?", second.getId()));
    }
}
