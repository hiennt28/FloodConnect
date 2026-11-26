package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Mục #29 trên CSDL thật: tài khoản chưa xác thực không hiện ra ngoài; khi bị xóa (đăng ký bỏ dở) thì follow không bị mồ côi. */
class UnverifiedAccountIT extends AbstractIT {

    private User unverified(String name) {
        User u = user(Role.CITIZEN);
        u.setFullName(name); u.setEmailVerified(false);
        return users.save(u);
    }

    @Test void timKiemVaHoSoCongKhaiBoQuaTaiKhoanChuaXacThuc() throws Exception {
        User ok = user(Role.CITIZEN);
        ok.setFullName("Nguyễn Đã Xác Thực"); users.save(ok);
        User pending = unverified("Nguyễn Chưa Xác Thực");
        mvc.perform(get("/api/users/search").param("q", "Nguyễn"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].user.id").value(ok.getId()));
        mvc.perform(get("/api/users/" + pending.getId())).andExpect(status().isNotFound());
    }

    /** Đăng ký lại bằng email của một đăng ký bỏ dở quá 24 giờ: tài khoản cũ bị xóa, khóa ngoại (V4) xóa luôn lượt theo dõi liên quan. */
    @Test void xoaDangKyBoDoThiXoaLuonLuotTheoDoi() throws Exception {
        User follower = user(Role.CITIZEN);
        User stale = unverified("Đăng ký bỏ dở");
        // Dữ liệu từ trước khi có quy tắc chặn theo dõi tài khoản chưa xác thực
        jdbc.update("insert into follows (follower_id, following_id, created_at) values (?, ?, now())", follower.getId(), stale.getId());
        jdbc.update("update users set created_at = now() - interval '25 hours' where id = ?", stale.getId());

        String body = "{\"fullName\":\"Người mới\",\"email\":\"" + stale.getEmail() + "\",\"phone\":\"0988123456\","
                + "\"password\":\"Demo12345\",\"confirmPassword\":\"Demo12345\"}";
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk());

        assertEquals(0, jdbc.queryForObject("select count(*) from users where id = ?", Integer.class, stale.getId()));
        assertEquals(0, jdbc.queryForObject("select count(*) from follows where following_id = ?", Integer.class, stale.getId()),
                "lượt theo dõi tài khoản đã xóa phải bị xóa theo");
    }
}
