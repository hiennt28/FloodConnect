package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #6 trên CSDL thật, đi đúng luồng đăng ký tổ chức của giao diện: CCCD lưu riêng tư (không vào thư mục /files công khai, API không
 * trả đường dẫn tệp); chỉ Admin và chính tổ chức tải được qua /api/org-docs/{id}/file, người khác 404, khách 401.
 */
class OrgDocsIT extends AbstractIT {
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");

    /** Gửi đăng ký nhóm thiện nguyện (bắt buộc CCCD hai mặt) qua API; description null = không gửi mô tả. */
    private org.springframework.test.web.servlet.ResultActions register(String email, String description) throws Exception {
        double lat = 21.0285, lng = 105.8522; // Hồ Gươm
        String ward = jdbc.queryForObject("select code from admin_wards where ST_Contains(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))", String.class, lng, lat);
        String data = "{\"account\":{\"fullName\":\"Trưởng nhóm\",\"email\":\"" + email + "\",\"phone\":\"0977" + (100000 + email.length()) + "\","
                + "\"password\":\"Demo12345\",\"confirmPassword\":\"Demo12345\"},\"orgName\":\"Nhóm Áo Xanh\",\"orgType\":\"VOLUNTEER\","
                + "\"wardCode\":\"" + ward + "\",\"streetAddress\":\"5 Hàng Bài\",\"latitude\":" + lat + ",\"longitude\":" + lng
                + ",\"maxConcurrentCases\":3,\"serviceRadiusKm\":20" // bắt buộc từ V10
                + (description == null ? "" : ",\"description\":\"" + description + "\"") + "}";
        return mvc.perform(multipart("/api/auth/register-org")
                .file(new MockMultipartFile("data", "", "application/json", data.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("idFront", "truoc.png", "image/png", PNG))
                .file(new MockMultipartFile("idBack", "sau.png", "image/png", PNG)));
    }

    /** Đăng ký thành công, trả mã tổ chức. */
    private Long registerVolunteerGroup(String email) throws Exception {
        register(email, "Nhóm thiện nguyện có 2 xuồng, tiếp tế lương thực").andExpect(status().isOk());
        return jdbc.queryForObject("select o.id from rescue_organizations o join users u on u.id = o.user_id where u.email = ?", Long.class, email);
    }

    /** Mô tả tổ chức bắt buộc khi đăng ký: AI Matching suy năng lực (điểm C) từ mô tả. */
    @Test void dangKyThieuMoTaBiTuChoi() throws Exception {
        register("nhom.khongmota@it.test", null)
                .andExpect(status().isBadRequest())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.errors.description").exists());
        register("nhom.motatrang@it.test", "   ").andExpect(status().isBadRequest());
        assertEquals(0, jdbc.queryForObject("select count(*) from rescue_organizations", Integer.class));
    }

    @Test void cccdLuuRiengTuVaChiAdminVoiChinhToChucMoDuoc() throws Exception {
        Long orgId = registerVolunteerGroup("nhom.aoxanh@it.test");
        Long docId = jdbc.queryForObject("select min(id) from organization_documents where org_id = ?", Long.class, orgId);
        String key = jdbc.queryForObject("select storage_key from organization_documents where id = ?", String.class, docId);
        assertTrue(key.startsWith("local:org-docs/"), "CSDL lưu mã nội bộ, không phải đường dẫn: " + key);
        assertFalse(Files.exists(Path.of("target/it-uploads/org-docs", key.substring(key.lastIndexOf('/') + 1))),
                "tệp CCCD không được nằm trong thư mục công khai /files");

        User owner = users.findById(jdbc.queryForObject("select user_id from rescue_organizations where id = ?", Long.class, orgId)).orElseThrow();
        owner.setEmailVerified(true); users.save(owner);
        User admin = user(Role.ADMIN);

        // Chi tiết tổ chức (Admin) có danh sách giấy tờ nhưng KHÔNG có đường dẫn tệp
        mvc.perform(get("/api/admin/orgs/" + orgId).header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.documents.length()").value(2))
                .andExpect(jsonPath("$.organization.documents[0].fileUrl").doesNotExist());

        for (User allowed : new User[] { admin, owner }) {
            byte[] body = mvc.perform(get("/api/org-docs/" + docId + "/file").header("Authorization", bearer(allowed)))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", startsWith("image/png"))) // backend gắn thêm ;charset=UTF-8 cho mọi phản hồi, vô hại
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andReturn().getResponse().getContentAsByteArray();
            assertArrayEquals(PNG, body);
        }
        User otherOrg = user(Role.RESCUE_ORG);
        activeOrg(otherOrg, 16.05, 108.2);
        for (User denied : new User[] { otherOrg, user(Role.CITIZEN) })
            mvc.perform(get("/api/org-docs/" + docId + "/file").header("Authorization", bearer(denied))).andExpect(status().isNotFound());
        mvc.perform(get("/api/org-docs/" + docId + "/file")).andExpect(status().isUnauthorized());
    }

    @Test void anhMauCuChuyenHuongChoNguoiCoQuyen() throws Exception {
        User orgUser = user(Role.RESCUE_ORG);
        var org = activeOrg(orgUser, 16.05, 108.2);
        jdbc.update("insert into organization_documents (org_id, doc_label, storage_key, file_type, created_at) values (?, 'Mẫu', ?, 'IMAGE', now())",
                org.getId(), "https://placehold.co/600x400?text=Ho+so+1");
        Long docId = jdbc.queryForObject("select id from organization_documents where org_id = ?", Long.class, org.getId());
        mvc.perform(get("/api/org-docs/" + docId + "/file").header("Authorization", bearer(user(Role.ADMIN))))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", startsWith("https://placehold.co/")));
        mvc.perform(get("/api/org-docs/" + docId + "/file").header("Authorization", bearer(user(Role.CITIZEN))))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("Location")); // người không có quyền không nhận được cả đường dẫn
    }
}
