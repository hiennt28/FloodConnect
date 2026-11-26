package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.text.Normalizer;
import java.util.List;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tìm kiếm không phân biệt dấu và hoa/thường trên CSDL thật (unaccent, Flyway V5): người dùng báo gõ "yen" không ra
 * "Đội cứu hộ phường Yên Hòa". Gọi đúng API mà ô tìm kiếm của giao diện gọi.
 */
class SearchIT extends AbstractIT {
    private User citizen;

    @BeforeEach void setUp() {
        RescueOrganization yenHoa = activeOrg(user(Role.RESCUE_ORG), 21.02, 105.80);
        yenHoa.setName("Đội cứu hộ phường Yên Hòa"); orgs.save(yenHoa);
        RescueOrganization other = activeOrg(user(Role.RESCUE_ORG), 16.05, 108.20);
        other.setName("Hội Chữ thập đỏ quận Hoàng Mai"); orgs.save(other);
        citizen = user(Role.CITIZEN);
        citizen.setFullName("Nguyễn Văn An"); users.save(citizen);
    }

    private void expectOrg(String q, int total) throws Exception {
        mvc.perform(get("/api/users/search").param("q", q).param("type", "ORG"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").value(total));
    }

    @Test void yenVoiMoiCachGoDeuRaDoiCuuHoYenHoa() throws Exception {
        for (String q : List.of("yen", "yên", "Yen", "Yên", "YÊN", "yen hoa", "YÊN HÒA", "phuong yen"))
            mvc.perform(get("/api/users/search").param("q", q).param("type", "ORG"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.items[0].organization.name").value("Đội cứu hộ phường Yên Hòa"));
    }

    @Test void chuDDuocBoDauNhuCacChuKhac() throws Exception {
        expectOrg("doi cuu ho", 1);
        expectOrg("ĐỘI", 1);
        expectOrg("do", 2); // "Đội ..." và "... đỏ ..."
    }

    @Test void kieuGoDauToHopVanKhop() throws Exception {
        expectOrg(Normalizer.normalize("Yên Hòa", Normalizer.Form.NFD), 1);
    }

    @Test void timNguoiDanKhongDau() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "nguyen van an").param("type", "CITIZEN"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].user.id").value(citizen.getId()));
    }

    @Test void kyTuDacBietKhongThanhKyTuDaiDien() throws Exception {
        expectOrg("%", 0);
        expectOrg("_", 0);
        expectOrg("khong co to chuc nay", 0);
    }
}
