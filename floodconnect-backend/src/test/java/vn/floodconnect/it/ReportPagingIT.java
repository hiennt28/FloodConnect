package vn.floodconnect.it;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #12 (đợt 7b): danh sách báo cáo vi phạm theo trang, số báo cáo chờ trên trang Tổng quan do máy chủ đếm, tab "người dân vi phạm"
 * truy vấn thẳng. 25 bài bị báo cáo (nhiều bài cùng số báo cáo) để kiểm tra hai trang liền nhau không trùng / không sót bài.
 */
class ReportPagingIT extends AbstractIT {
    private User admin, violator;
    private long locked;

    private long id(String sql, Object... args) { return jdbc.queryForObject(sql + " returning id", Long.class, args); }
    private long post(User author) {
        return id("insert into posts(author_id, content, status, edited, created_at, updated_at) values (?, 'Bài', 'VISIBLE', false, now(), now())", author.getId());
    }

    @BeforeEach void setUp() {
        admin = user(Role.ADMIN);
        User author = user(Role.CITIZEN);
        List<User> reporters = List.of(user(Role.CITIZEN), user(Role.CITIZEN), user(Role.CITIZEN));
        for (int k = 0; k < 25; k++) {
            long p = post(author);
            for (int r = 0; r <= k % 3; r++) // 1, 2 hoặc 3 báo cáo: nhiều bài trùng số báo cáo
                id("insert into post_reports(post_id, reporter_id, reason, status, created_at) values (?, ?, 'SPAM', 'PENDING', now())", p, reporters.get(r).getId());
        }
        long handled = post(author); // báo cáo đã xử lý xong: không tính là đang chờ
        id("insert into post_reports(post_id, reporter_id, reason, status, created_at) values (?, ?, 'SPAM', 'COMPLETED', now())", handled, reporters.get(0).getId());

        long org = activeOrg(user(Role.RESCUE_ORG), 16.0, 108.2).getId();
        for (int k = 0; k < 3; k++) {
            long c = id("insert into fundraising_campaigns(org_id, name, target_amount, raised_amount, start_date, end_date, status, version, created_at, updated_at) "
                    + "values (?, 'Chiến dịch', 1000000, 0, now(), now() + interval '5 day', 'ACTIVE', 0, now(), now())", org);
            id("insert into campaign_reports(campaign_id, reporter_id, reason, status, created_at) values (?, ?, 'OTHER', 'PENDING', now())", c, reporters.get(0).getId());
        }

        // Tab "Người dân vi phạm": chỉ người dân đang hoạt động có bài bị Admin xóa
        violator = user(Role.CITIZEN);
        id("insert into post_moderations(post_id, admin_id, decision, created_at) values (?, ?, 'REMOVE', now())", post(violator), admin.getId());
        User lockedUser = user(Role.CITIZEN);
        locked = lockedUser.getId();
        id("insert into post_moderations(post_id, admin_id, decision, created_at) values (?, ?, 'REMOVE', now())", post(lockedUser), admin.getId());
        jdbc.update("update users set status = 'LOCKED' where id = ?", locked);
        id("insert into post_moderations(post_id, admin_id, decision, created_at) values (?, ?, 'KEEP', now())", post(reporters.get(1)), admin.getId());
    }

    private String body(String url, Object... params) throws Exception {
        var req = get(url).header("Authorization", bearer(admin));
        for (int k = 0; k + 1 < params.length; k += 2) req = req.param((String) params[k], String.valueOf(params[k + 1]));
        return mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test void baiBiBaoCaoChiaTrangKhongTrungKhongSot() throws Exception {
        String p0 = body("/api/admin/post-reports/posts", "page", 0, "size", 20), p1 = body("/api/admin/post-reports/posts", "page", 1, "size", 20);
        assertEquals(25, (int) JsonPath.read(p0, "$.total"));          // đếm số BÀI, không phải số dòng báo cáo
        assertEquals(false, JsonPath.read(p0, "$.last"));
        assertEquals(5, (int) JsonPath.read(p1, "$.items.length()"));
        assertEquals(true, JsonPath.read(p1, "$.last"));
        Set<Integer> ids = new HashSet<>(JsonPath.read(p0, "$.items[*].post.id"));
        ids.addAll(JsonPath.read(p1, "$.items[*].post.id"));
        assertEquals(25, ids.size());
        List<Integer> counts = JsonPath.read(p0, "$.items[*].pendingReports");
        assertEquals(3, (int) counts.get(0));                                // nhiều báo cáo trước
    }

    @Test void tungBaoCaoChiaTrang() throws Exception {
        String p = body("/api/admin/post-reports", "page", 2, "size", 20);
        assertEquals(50, (int) JsonPath.read(p, "$.total"));           // 9×1 + 8×2 + 8×3 = 49 đang chờ + 1 đã xử lý
        assertEquals(10, (int) JsonPath.read(p, "$.items.length()"));
    }

    @Test void chienDichBiBaoCaoChiaTrang() throws Exception {
        String p = body("/api/admin/campaign-reports/campaigns", "page", 1, "size", 2);
        assertEquals(3, (int) JsonPath.read(p, "$.total"));
        assertEquals(1, (int) JsonPath.read(p, "$.items.length()"));
    }

    @Test void tongQuanDemBaoCaoChoTrongCsdl() throws Exception {
        mvc.perform(get("/api/admin/stats/overview").header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.pendingReportedPosts").value(25))
                .andExpect(jsonPath("$.pendingReportedCampaigns").value(3));
    }

    @Test void tabViPhamChiCoNguoiDanDangHoatDongCoBaiBiXoa() throws Exception {
        mvc.perform(get("/api/admin/citizens").param("filter", "VIOLATING").header("Authorization", bearer(admin))).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(violator.getId()))
                .andExpect(jsonPath("$[0].removedPosts").value(1));
        mvc.perform(get("/api/admin/citizens").param("filter", "LOCKED").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$[0].userId").value(locked));
    }
}
