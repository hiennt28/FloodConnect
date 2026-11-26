package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.Map;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #15 trên CSDL thật: tổ chức đã duyệt tự sửa thông tin, ghim trụ sở kiểm tra bằng bảng địa giới PostGIS thật.
 * Mục 4(a): người ngoài chỉ thấy trụ sở tới Phường/Xã, Tỉnh; không có số nhà và tọa độ chính xác.
 */
class OrgProfileIT extends AbstractIT {
    /** Hồ Gươm, Hà Nội */
    private static final double LAT = 21.0285, LNG = 105.8522;

    private User orgUser;
    private RescueOrganization org;
    private String wardCode;

    @BeforeEach void setUp() {
        orgUser = user(Role.RESCUE_ORG);
        org = activeOrg(orgUser, 16.0544, 108.2022);
        org.setStreetAddress("Nhà riêng trưởng nhóm, 5 Kiệt 12"); orgs.save(org);
        wardCode = jdbc.queryForObject("select code from admin_wards where ST_Contains(geom, ST_SetSRID(ST_MakePoint(?, ?), 4326))",
                String.class, LNG, LAT);
    }

    private String body(String street, double lat, double lng) {
        return "{\"description\":\"Cứu hộ bằng ca-nô\",\"websiteUrl\":\"https://doi-a.vn\",\"wardCode\":\"" + wardCode + "\","
                + "\"latitude\":" + lat + ",\"longitude\":" + lng + ",\"streetAddress\":\"" + street + "\","
                + "\"maxConcurrentCases\":3,\"serviceRadiusKm\":20}"; // bắt buộc từ V10
    }

    @Test void toChucDoiTruSoMoi() throws Exception {
        mvc.perform(put("/api/org/me").header("Authorization", bearer(orgUser)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("12 Lê Thái Tổ", LAT, LNG)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.province").value("Thành phố Hà Nội"))
                .andExpect(jsonPath("$.streetAddress").value("12 Lê Thái Tổ"));
        Map<String, Object> row = jdbc.queryForMap("select latitude, longitude, street_address, website_url from rescue_organizations where id = ?", org.getId());
        assertEquals(LAT, ((Number) row.get("latitude")).doubleValue(), 1e-9);
        assertEquals("https://doi-a.vn", row.get("website_url"));
        // Cột vị trí PostGIS tự cập nhật theo tọa độ mới: tổ chức được tìm thấy quanh trụ sở mới
        assertEquals(1, orgs.findAvailableWithin(LAT, LNG, 1000).size());
    }

    @Test void ghimNgoaiPhuongXaDaChonBiTuChoi() throws Exception {
        mvc.perform(put("/api/org/me").header("Authorization", bearer(orgUser)).contentType(MediaType.APPLICATION_JSON)
                        .content(body("12 Lê Thái Tổ", 16.0544, 108.2022))) // ghim ở Đà Nẵng, xã chọn ở Hà Nội
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.location").exists());
    }

    @Test void nguoiDanKhongDungDuocApiCuaToChuc() throws Exception {
        mvc.perform(put("/api/org/me").header("Authorization", bearer(user(Role.CITIZEN))).contentType(MediaType.APPLICATION_JSON)
                .content(body("12 Lê Thái Tổ", LAT, LNG))).andExpect(status().isForbidden());
    }

    /** Khách (chưa đăng nhập) xem hồ sơ công khai: có Phường/Xã, Tỉnh nhưng không có số nhà, tọa độ trụ sở. */
    @Test void hoSoCongKhaiChiCoPhuongXaVaTinh() throws Exception {
        org.setWard("Phường Hải Châu"); org.setProvince("Thành phố Đà Nẵng"); orgs.save(org);
        mvc.perform(get("/api/users/" + orgUser.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.organization.ward").value("Phường Hải Châu"))
                .andExpect(jsonPath("$.organization.province").value("Thành phố Đà Nẵng"))
                .andExpect(jsonPath("$.organization.streetAddress").value(nullValue()))
                .andExpect(jsonPath("$.organization.latitude").value(nullValue()))
                .andExpect(jsonPath("$.organization.longitude").value(nullValue()));
        // Chính tổ chức vẫn nhận đầy đủ ở API của mình
        mvc.perform(get("/api/org/me").header("Authorization", bearer(orgUser)))
                .andExpect(jsonPath("$.streetAddress").value("Nhà riêng trưởng nhóm, 5 Kiệt 12"))
                .andExpect(jsonPath("$.latitude").value(16.0544));
    }
}
