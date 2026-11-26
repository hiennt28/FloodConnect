package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import vn.floodconnect.auth.entity.AuthToken;
import vn.floodconnect.auth.entity.AuthTokenType;
import vn.floodconnect.auth.repository.AuthTokenRepository;
import vn.floodconnect.common.util.TokenUtil;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Mục #1 với transaction thật: lần nhập sai mã phải được LƯU vào CSDL (trước đây bị rollback nên luôn "còn 4 lần thử").
 * Unit test không chứng minh được điều này vì kho dữ liệu là đồ giả.
 */
class OtpAttemptsIT extends AbstractIT {
    @Autowired AuthTokenRepository tokens;

    private AuthToken otp(User u, AuthTokenType type, String code) {
        AuthToken t = new AuthToken();
        t.setUser(u); t.setType(type); t.setTokenHash(TokenUtil.sha256(code)); t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10)));
        return tokens.save(t);
    }

    private Map<String, Object> row(AuthToken t) {
        return jdbc.queryForMap("select failed_attempts, used_at from auth_tokens where id = ?", t.getId());
    }

    private static String json(String email, String code) {
        return "{\"email\":\"" + email + "\",\"code\":\"" + code + "\"}";
    }

    @Test void nhapSaiNamLanThiCsdlLuuBoDemVaHuyMa() throws Exception {
        User u = user(Role.CITIZEN);
        u.setEmailVerified(false); users.save(u);
        AuthToken t = otp(u, AuthTokenType.EMAIL_VERIFY, "123456");

        for (int left = 4; left >= 1; left--)
            mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json(u.getEmail(), "000000")))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("WRONG_CODE"))
                    .andExpect(jsonPath("$.message").value("Mã xác thực không đúng. Còn " + left + " lần thử."));
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json(u.getEmail(), "000000")))
                .andExpect(jsonPath("$.code").value("TOKEN_INVALID"));

        Map<String, Object> r = row(t);
        assertEquals(5, ((Number) r.get("failed_attempts")).intValue(), "bộ đếm phải được lưu thật");
        assertNotNull(r.get("used_at"), "mã phải bị hủy sau lần sai thứ 5");

        // Mã đúng cũng không dùng được nữa
        mvc.perform(post("/api/auth/verify-email").contentType(MediaType.APPLICATION_JSON).content(json(u.getEmail(), "123456")))
                .andExpect(status().isBadRequest());
        assertFalse(users.findById(u.getId()).orElseThrow().isEmailVerified());
    }

    @Test void matKhauKhongKhopThiKhongTonLanThuRoiDoiDuocMatKhauBangCungMa() throws Exception {
        User u = user(Role.CITIZEN);
        AuthToken t = otp(u, AuthTokenType.PASSWORD_RESET, "654321");
        String mismatch = "{\"email\":\"" + u.getEmail() + "\",\"code\":\"654321\",\"newPassword\":\"Moi12345\",\"confirmPassword\":\"Khac12345\"}";
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(mismatch))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("PASSWORD_MISMATCH"));
        Map<String, Object> r = row(t);
        assertEquals(0, ((Number) r.get("failed_attempts")).intValue());
        assertNull(r.get("used_at"));

        String ok = "{\"email\":\"" + u.getEmail() + "\",\"code\":\"654321\",\"newPassword\":\"Moi12345\",\"confirmPassword\":\"Moi12345\"}";
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(ok)).andExpect(status().isOk());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + u.getEmail() + "\",\"password\":\"Moi12345\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    /** V3 xóa ràng buộc unique sai: hai người nhận trùng một mã 6 số ngẫu nhiên không còn làm lỗi lưu mã. */
    @Test void haiNguoiTrungMaOtpVanLuuDuoc() {
        User a = user(Role.CITIZEN), b = user(Role.CITIZEN);
        otp(a, AuthTokenType.EMAIL_VERIFY, "111111");
        assertDoesNotThrow(() -> otp(b, AuthTokenType.EMAIL_VERIFY, "111111"));
    }
}
