package vn.floodconnect.it;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Phân quyền theo vai trò qua toàn bộ chuỗi bộ lọc Spring Security thật: khách / người dân / tổ chức / Admin. */
class SecurityMatrixIT extends AbstractIT {
    private User citizen, orgUser, admin;

    @BeforeEach void setUp() {
        citizen = user(Role.CITIZEN);
        orgUser = user(Role.RESCUE_ORG);
        activeOrg(orgUser, 16.0544, 108.2022);
        admin = user(Role.ADMIN);
    }

    private int code(MockHttpServletRequestBuilder req, User as) throws Exception {
        if (as != null) req.header("Authorization", bearer(as));
        return mvc.perform(req).andReturn().getResponse().getStatus();
    }

    private void expect(String path, int guest, int asCitizen, int asOrg, int asAdmin) throws Exception {
        org.junit.jupiter.api.Assertions.assertAll(path,
                () -> org.junit.jupiter.api.Assertions.assertEquals(guest, code(get(path), null), "khách"),
                () -> org.junit.jupiter.api.Assertions.assertEquals(asCitizen, code(get(path), citizen), "người dân"),
                () -> org.junit.jupiter.api.Assertions.assertEquals(asOrg, code(get(path), orgUser), "tổ chức"),
                () -> org.junit.jupiter.api.Assertions.assertEquals(asAdmin, code(get(path), admin), "Admin"));
    }

    @Test void apiAdminChiAdminVaoDuoc() throws Exception { expect("/api/admin/stats/overview", 401, 403, 403, 200); }

    @Test void apiToChucChiToChucVaoDuoc() throws Exception { expect("/api/org/me", 401, 403, 200, 403); }

    @Test void hoSoCuaToiCanDangNhap() throws Exception { expect("/api/users/me", 401, 200, 200, 200); }

    @Test void bangTinVaThongKeCongKhaiKhachXemDuoc() throws Exception {
        expect("/api/posts", 200, 200, 200, 200);
        expect("/api/public/stats", 200, 200, 200, 200);
    }

    @Test void khachKhongGuiDuocSos() throws Exception {
        mvc.perform(post("/api/sos").contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"ngập\",\"latitude\":16.05,\"longitude\":108.2}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    /** Mục #16: chỉ người dân gửi được SOS; tổ chức cứu hộ và Admin bị từ chối với mã lỗi riêng, không có ca nào được tạo. */
    @Test void chiNguoiDanGuiDuocSos() throws Exception {
        String body = "{\"description\":\"Nước ngập tới ngực, có trẻ nhỏ\",\"latitude\":16.0544,\"longitude\":108.2022,\"address\":\"Kiệt 12\"}";
        for (User notCitizen : new User[] { orgUser, admin })
            mvc.perform(post("/api/sos").header("Authorization", bearer(notCitizen)).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SOS_CITIZEN_ONLY"));
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject("select count(*) from incidents", Integer.class));

        mvc.perform(post("/api/sos").header("Authorization", bearer(citizen)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.incident.code").exists());
    }

    @Test void dangNhapThatRoiDungTokenDuocCap() throws Exception {
        String body = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"identifier\":\"" + citizen.getEmail() + "\",\"password\":\"Demo12345\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = body.replaceAll(".*\"accessToken\":\"([^\"]+)\".*", "$1");
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value(citizen.getEmail()));
    }

    /** Mục #2: token mang role ADMIN ký bằng khóa mẫu cũ (từng công khai trong mã nguồn) bị coi như khách. */
    @Test void tokenGiaKyBangKhoaMauCuBiTuChoi() throws Exception {
        String forged = Jwts.builder().subject(String.valueOf(admin.getId())).claim("role", "ADMIN")
                .expiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor("change-me-please-use-a-long-random-secret-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)))
                .compact();
        mvc.perform(get("/api/admin/stats/overview").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
    }
}
