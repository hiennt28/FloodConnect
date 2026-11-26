package vn.floodconnect.it;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Đếm số câu SQL mỗi API danh sách gửi tới CSDL (Hibernate Statistics) để phát hiện lỗi N+1: số câu không được tăng theo số bản
 * ghi trong trang. Dữ liệu mẫu: 10 bài (2 ảnh, 3 lượt thích, 3 bình luận mỗi bài), 1 bài có 10 bình luận × 2 trả lời,
 * 6 chiến dịch (2 ảnh), báo cáo vi phạm, 8 tổ chức, 1 ca SOS cần điều phối. Bảng kết quả in ra cuối lần chạy.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class QueryCountIT extends AbstractIT {
    /** Số câu ĐỌC tối đa cho mỗi API, không phụ thuộc số bản ghi (gồm cả câu đọc tài khoản đăng nhập của bộ lọc JWT). */
    private static final int MAX = 10;
    private static final Map<String, long[]> RESULTS = new LinkedHashMap<>();

    @Autowired EntityManagerFactory emf;
    @Autowired IncidentRepository incidents;

    private User viewer, admin;
    private final List<User> orgUsers = new ArrayList<>(), citizens = new ArrayList<>();
    private final List<Long> posts = new ArrayList<>(), campaigns = new ArrayList<>();
    private long busyPost, firstComment, sosId;

    private long id(String sql, Object... args) { return jdbc.queryForObject(sql + " returning id", Long.class, args); }

    @BeforeEach void seed() {
        admin = user(Role.ADMIN);
        for (int k = 0; k < 8; k++) { User u = user(Role.RESCUE_ORG); activeOrg(u, 16.0 + k * 0.01, 108.2); orgUsers.add(u); }
        for (int k = 0; k < 6; k++) citizens.add(user(Role.CITIZEN));
        viewer = citizens.get(0);
        jdbc.update("insert into follows(follower_id, following_id, created_at) values (?, ?, now()), (?, ?, now())",
                viewer.getId(), orgUsers.get(0).getId(), viewer.getId(), citizens.get(1).getId());

        for (int k = 0; k < 10; k++) {
            User author = k % 2 == 0 ? orgUsers.get(k % 8) : citizens.get(k % 6);
            long p = id("insert into posts(author_id, content, status, edited, created_at, updated_at) values (?, ?, 'VISIBLE', false, now() - (? * interval '1 minute'), now())",
                    author.getId(), "Bài " + k, k);
            posts.add(p);
            for (int m = 0; m < 2; m++)
                jdbc.update("insert into post_media(post_id, media_url, media_type, sort_order, created_at) values (?, ?, 'IMAGE', ?, now())", p, "https://x/" + p + "-" + m + ".jpg", m);
            for (int l = 0; l < 3; l++) jdbc.update("insert into post_likes(post_id, user_id, created_at) values (?, ?, now())", p, citizens.get(l).getId());
            for (int c = 0; c < 3; c++)
                id("insert into comments(post_id, author_id, content, status, edited, created_at, updated_at) values (?, ?, 'Bình luận', 'VISIBLE', false, now(), now())",
                        p, (c % 2 == 0 ? orgUsers.get(c) : citizens.get(c)).getId());
        }
        busyPost = posts.get(0);
        for (int c = 0; c < 10; c++) {
            long top = id("insert into comments(post_id, author_id, content, status, edited, created_at, updated_at) values (?, ?, 'Gốc', 'VISIBLE', false, now(), now())",
                    busyPost, orgUsers.get(c % 8).getId());
            if (c == 0) firstComment = top;
            for (int r = 0; r < 2; r++)
                id("insert into comments(post_id, author_id, parent_comment_id, reply_to_user_id, content, status, edited, created_at, updated_at) "
                        + "values (?, ?, ?, ?, 'Trả lời', 'VISIBLE', false, now(), now())", busyPost, orgUsers.get((c + r + 1) % 8).getId(), top, orgUsers.get(c % 8).getId());
        }

        List<RescueOrganization> all = orgs.findAll();
        for (int k = 0; k < 6; k++) {
            long c = id("insert into fundraising_campaigns(org_id, name, target_amount, raised_amount, start_date, end_date, status, version, created_at, updated_at) "
                    + "values (?, ?, 1000000, 0, now() - interval '1 day', now() + interval '10 day', 'ACTIVE', 0, now(), now())", all.get(k % 8).getId(), "Chiến dịch " + k);
            campaigns.add(c);
            for (int m = 0; m < 2; m++)
                jdbc.update("insert into campaign_media(campaign_id, media_url, sort_order, created_at) values (?, ?, ?, now())", c, "https://x/c" + c + "-" + m + ".jpg", m);
        }
        for (int k = 0; k < 5; k++)
            for (User rep : List.of(viewer, orgUsers.get(k)))
                id("insert into post_reports(post_id, reporter_id, reason, status, created_at) values (?, ?, 'SPAM', 'PENDING', now())", posts.get(k), rep.getId());
        for (int k = 0; k < 3; k++)
            for (User rep : List.of(viewer, orgUsers.get(k + 1)))
                id("insert into campaign_reports(campaign_id, reporter_id, reason, status, created_at) values (?, ?, 'OTHER', 'PENDING', now())", campaigns.get(k), rep.getId());

        Incident i = new Incident();
        i.setCode("QC-1"); i.setCitizen(citizens.get(2)); i.setDescription("Nước ngập sâu, cần thuyền");
        i.setLatitude(16.02); i.setLongitude(108.2); i.setStatus(IncidentStatus.PENDING); i.setDangerLevel(DangerLevel.CAO);
        sosId = incidents.save(i).getId();
        for (int k = 0; k < 3; k++) { // vài tổ chức đang bận ca khác
            Incident busy = new Incident();
            busy.setCode("QC-B" + k); busy.setCitizen(citizens.get(3 + k)); busy.setDescription("Ngập"); busy.setLatitude(16.03); busy.setLongitude(108.21);
            busy.setStatus(IncidentStatus.ACCEPTED); busy.setDangerLevel(DangerLevel.THAP); busy.setAssignedOrg(all.get(k));
            incidents.save(busy);
        }
        jdbc.update("insert into incident_assignments(incident_id, org_id, type, status, created_at) values (?, ?, 'ADMIN_ASSIGN', 'REJECTED', now())", sosId, all.get(7).getId());
    }

    /** Số câu ĐỌC (SELECT) của một lần gọi; câu ghi (lưu điểm matching...) tính riêng vì là dữ liệu thật phải ghi. */
    private void count(String name, MockHttpServletRequestBuilder req, User as) throws Exception {
        Statistics st = emf.unwrap(SessionFactory.class).getStatistics();
        st.clear();
        mvc.perform(req.header("Authorization", bearer(as))).andExpect(status().isOk());
        long writes = st.getEntityInsertCount() + st.getEntityUpdateCount() + st.getEntityDeleteCount();
        RESULTS.put(name, new long[] {st.getPrepareStatementCount() - writes, writes});
    }

    @Test void soCauSqlKhongTangTheoSoBanGhi() throws Exception {
        count("Bảng tin (10 bài)", get("/api/posts").param("size", "10"), viewer);
        count("Bài của một người", get("/api/users/" + orgUsers.get(0).getId() + "/posts"), viewer);
        count("Bình luận (13 bình luận gốc)", get("/api/posts/" + busyPost + "/comments").param("size", "50"), viewer);
        count("Trả lời bình luận", get("/api/comments/" + firstComment + "/replies"), viewer);
        count("Danh sách chiến dịch (6)", get("/api/campaigns").param("status", "ACTIVE"), viewer);
        count("Chiến dịch của tổ chức", get("/api/org/campaigns"), orgUsers.get(0));
        count("Danh bạ tổ chức (8)", get("/api/users/search").param("q", "").param("type", "ORG").param("size", "100"), viewer);
        count("Tìm người dân (6)", get("/api/users/search").param("q", "").param("type", "CITIZEN").param("size", "100"), viewer);
        count("Admin: bài bị báo cáo (5)", get("/api/admin/post-reports/posts").param("size", "100"), admin);
        count("Admin: từng báo cáo bài (10)", get("/api/admin/post-reports").param("size", "100"), admin);
        count("Admin: chi tiết báo cáo bài", get("/api/admin/posts/" + posts.get(0) + "/reports"), admin);
        count("Admin: chiến dịch bị báo cáo (3)", get("/api/admin/campaign-reports/campaigns").param("size", "100"), admin);
        count("Admin: từng báo cáo chiến dịch (6)", get("/api/admin/campaign-reports").param("size", "100"), admin);
        count("Admin: tổ chức đề xuất (8 tổ chức)", get("/api/admin/sos/" + sosId + "/candidates"), admin);
        List<String> tooMany = new ArrayList<>();
        RESULTS.forEach((k, v) -> { if (v[0] > MAX) tooMany.add(k + " = " + v[0]); });
        assertTrue(tooMany.isEmpty(), "Quá " + MAX + " câu SQL: " + tooMany);
    }

    /** Gộp truy vấn không được làm sai dữ liệu: các con số / cờ của từng bản ghi vẫn đúng như khi tra riêng từng bản ghi. */
    @Test void ketQuaVanDungSauKhiGopTruyVan() throws Exception {
        String orgName = orgs.findByUserId(orgUsers.get(0).getId()).orElseThrow().getName();
        String bp = "$.items[?(@.id == " + busyPost + ")]";
        mvc.perform(get("/api/posts").param("size", "10").header("Authorization", bearer(viewer))).andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath(bp + ".likeCount").value(3))
                .andExpect(jsonPath(bp + ".likedByMe").value(true))
                .andExpect(jsonPath(bp + ".commentCount").value(33))            // 3 + 10 gốc + 20 trả lời
                .andExpect(jsonPath(bp + ".media.length()").value(2))
                .andExpect(jsonPath(bp + ".author.organizationName").value(orgName))
                .andExpect(jsonPath("$.items[?(@.id == " + posts.get(1) + ")].author.organizationName").value(org.hamcrest.Matchers.contains((Object) null)));
        mvc.perform(get("/api/posts").param("size", "10").header("Authorization", bearer(citizens.get(5)))) // chưa thích bài nào
                .andExpect(jsonPath(bp + ".likedByMe").value(false));
        mvc.perform(get("/api/posts/" + busyPost + "/comments").param("size", "50").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.items.length()").value(13))
                .andExpect(jsonPath("$.items[?(@.id == " + firstComment + ")].replyCount").value(2))
                .andExpect(jsonPath("$.items[?(@.id == " + firstComment + ")].author.organizationName").value(orgName));
        mvc.perform(get("/api/comments/" + firstComment + "/replies").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].replyCount").value(0))
                .andExpect(jsonPath("$.items[0].replyTo.organizationName").value(orgName));
        mvc.perform(get("/api/campaigns").param("status", "ACTIVE").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.items[?(@.id == " + campaigns.get(0) + ")].reportedByMe").value(true))
                .andExpect(jsonPath("$.items[?(@.id == " + campaigns.get(4) + ")].reportedByMe").value(false))
                .andExpect(jsonPath("$.items[?(@.id == " + campaigns.get(0) + ")].media.length()").value(2));
        mvc.perform(get("/api/users/search").param("q", "").param("type", "ORG").param("size", "100").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.items.length()").value(8))
                .andExpect(jsonPath("$.items[?(@.user.id == " + orgUsers.get(0).getId() + ")].followedByMe").value(true))
                .andExpect(jsonPath("$.items[?(@.user.id == " + orgUsers.get(1).getId() + ")].followedByMe").value(false))
                .andExpect(jsonPath("$.items[?(@.user.id == " + orgUsers.get(0).getId() + ")].user.organizationName").value(orgName));
        mvc.perform(get("/api/users/search").param("q", "").param("type", "CITIZEN").param("size", "100").header("Authorization", bearer(viewer)))
                .andExpect(jsonPath("$.items[?(@.user.id == " + citizens.get(1).getId() + ")].followedByMe").value(true));
        mvc.perform(get("/api/admin/post-reports/posts").param("size", "100").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.items.length()").value(5))
                .andExpect(jsonPath("$.items[*].pendingReports", org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(2))))
                .andExpect(jsonPath("$.items[?(@.post.id == " + busyPost + ")].post.likedByMe").value(false));
        mvc.perform(get("/api/admin/post-reports").param("size", "100").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.items.length()").value(10))
                .andExpect(jsonPath("$.items[?(@.reporter.id == " + orgUsers.get(0).getId() + ")].reporter.organizationName").value(orgName));
        mvc.perform(get("/api/admin/sos/" + sosId + "/candidates").header("Authorization", bearer(admin)))
                .andExpect(jsonPath("$.length()").value(7))                    // 8 tổ chức trừ tổ chức đã từ chối ca
                .andExpect(jsonPath("$[?(@.org.id == " + orgs.findAll().get(0).getId() + ")].matching.activeCases").value(1)); // cùng thứ tự với seed()
    }

    /** application.yml: cấu hình Hibernate phải nằm dưới spring.jpa.properties (từng bị đặt nhầm dưới spring.flyway, mất tác dụng). */
    @Test void cauHinhHibernateCoHieuLuc() {
        Map<String, Object> p = emf.getProperties();
        org.junit.jupiter.api.Assertions.assertEquals("100", String.valueOf(p.get("hibernate.default_batch_fetch_size")));
        org.junit.jupiter.api.Assertions.assertEquals("UTC", String.valueOf(p.get("hibernate.jdbc.time_zone")));
    }

    @AfterAll static void print() {
        System.out.println("\n===== Số câu SQL mỗi lần gọi API (đọc / ghi) =====");
        RESULTS.forEach((k, v) -> System.out.printf("%-40s %4d %4d%n", k, v[0], v[1]));
    }
}
