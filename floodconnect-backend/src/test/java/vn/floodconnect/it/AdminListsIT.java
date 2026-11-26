package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostModeration;
import vn.floodconnect.post.repository.PostModerationRepository;
import vn.floodconnect.post.repository.PostRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Tab "Đang hoạt động" (tổ chức) và "Tất cả người dân" (tìm không dấu, email, SĐT, phân trang) của Admin, trên CSDL thật. */
class AdminListsIT extends AbstractIT {
    @Autowired PostRepository posts;
    @Autowired PostModerationRepository moderations;

    private User admin, an, binh;

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        an = user(Role.CITIZEN); an.setFullName("Nguyễn Văn An"); an.setEmail("an.nguyen@it.test"); an.setPhone("0912345678"); users.save(an);
        binh = user(Role.CITIZEN); binh.setFullName("Trần Thị Bình"); users.save(binh);
        User locked = user(Role.CITIZEN); locked.setFullName("Lê Văn Khóa"); locked.setStatus(UserStatus.LOCKED); users.save(locked);
        User pending = user(Role.CITIZEN); pending.setFullName("Phạm Chưa Xác Thực"); pending.setEmailVerified(false); users.save(pending);
        // An có 1 bài bị Admin xóa
        Post p = new Post(); p.setAuthor(an); p.setContent("Tin sai sự thật"); posts.save(p);
        PostModeration m = new PostModeration(); m.setPost(p); m.setAdmin(admin); m.setDecision(ModerationDecision.REMOVE); m.setReason("Tin giả");
        moderations.save(m);
    }

    private org.springframework.test.web.servlet.ResultActions search(String q) throws Exception {
        return mvc.perform(get("/api/admin/citizens/search").param("q", q).header("Authorization", bearer(admin)));
    }

    @Test void tatCaNguoiDanGomCaBiKhoaVaChuaXacThuc() throws Exception {
        search("").andExpect(status().isOk()).andExpect(jsonPath("$.total").value(4));
    }

    @Test void timKhongDauTheoTenEmailHoacSoDienThoai() throws Exception {
        search("nguyen van an").andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].userId").value(an.getId()))
                .andExpect(jsonPath("$.items[0].removedPosts").value(1)) // số bài bị xóa đếm gộp đúng
                .andExpect(jsonPath("$.items[0].phone").value("0912345678"));
        search("AN.NGUYEN@").andExpect(jsonPath("$.total").value(1));
        search("2345678").andExpect(jsonPath("$.total").value(1));
        search("binh").andExpect(jsonPath("$.items[0].userId").value(binh.getId()));
        search("chua xac thuc").andExpect(jsonPath("$.items[0].emailVerified").value(false));
    }

    @Test void phanTrang() throws Exception {
        mvc.perform(get("/api/admin/citizens/search").param("size", "3").param("page", "1").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.total").value(4))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test void chiAdminDungDuoc() throws Exception {
        mvc.perform(get("/api/admin/citizens/search").header("Authorization", bearer(an))).andExpect(status().isForbidden());
    }

    @Test void tabDangHoatDongCoMoiToChucDaDuyetKeCaKhongViPham() throws Exception {
        RescueOrganization a = activeOrg(user(Role.RESCUE_ORG), 16.05, 108.2);
        a.setName("Đội A"); a.setWard("Phường Hải Châu"); a.setProvince("Thành phố Đà Nẵng"); orgs.save(a);
        RescueOrganization b = activeOrg(user(Role.RESCUE_ORG), 16.06, 108.2);
        b.setName("Đội B"); b.setOperatingStatus(OperatingStatus.BUSY); orgs.save(b);
        RescueOrganization pending = activeOrg(user(Role.RESCUE_ORG), 16.07, 108.2);
        pending.setStatus(OrgStatus.PENDING); orgs.save(pending);
        mvc.perform(get("/api/admin/orgs").param("filter", "ACTIVE").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[*].name", containsInAnyOrder("Đội A", "Đội B")))
                .andExpect(jsonPath("$[0].area").value("Phường Hải Châu, Thành phố Đà Nẵng"))
                .andExpect(jsonPath("$[1].operatingStatus").value("BUSY"))
                .andExpect(jsonPath("$[0].totalViolations").value(0));
    }
}
