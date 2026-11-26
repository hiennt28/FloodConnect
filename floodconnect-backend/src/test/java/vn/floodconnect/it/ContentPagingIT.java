package vn.floodconnect.it;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #12 (đợt 7c): chiến dịch "Tải thêm" không trùng / sót khi nhiều chiến dịch cùng ngày bắt đầu, một bình luận theo mã (mở từ
 * thông báo), số tổ chức sẵn sàng do máy chủ đếm.
 */
class ContentPagingIT extends AbstractIT {
    private User citizen;
    private RescueOrganization team;
    private long post, hiddenPost, topComment, reply, deletedComment;

    private long id(String sql, Object... args) { return jdbc.queryForObject(sql + " returning id", Long.class, args); }

    @BeforeEach void setUp() {
        citizen = user(Role.CITIZEN);
        team = activeOrg(user(Role.RESCUE_ORG), 16.0, 108.2);
        RescueOrganization busy = activeOrg(user(Role.RESCUE_ORG), 16.1, 108.2);
        busy.setOperatingStatus(OperatingStatus.BUSY); orgs.save(busy);
        activeOrg(user(Role.RESCUE_ORG), 16.2, 108.2);
        // 30 chiến dịch cùng một thời điểm bắt đầu: chỉ sắp theo ngày thì thứ tự giữa các trang không xác định
        for (int k = 0; k < 30; k++)
            id("insert into fundraising_campaigns(org_id, name, target_amount, raised_amount, start_date, end_date, status, version, created_at, updated_at) "
                    + "values (?, ?, 1000000, 0, timestamp '2026-11-01 00:00:00+00', now() + interval '5 day', 'ACTIVE', 0, now(), now())", team.getId(), "Chiến dịch " + k);

        post = id("insert into posts(author_id, content, status, edited, created_at, updated_at) values (?, 'Bài', 'VISIBLE', false, now(), now())", citizen.getId());
        topComment = id("insert into comments(post_id, author_id, content, status, edited, created_at, updated_at) values (?, ?, 'Gốc', 'VISIBLE', false, now(), now())", post, citizen.getId());
        reply = id("insert into comments(post_id, author_id, parent_comment_id, content, status, edited, created_at, updated_at) values (?, ?, ?, 'Trả lời', 'VISIBLE', false, now(), now())",
                post, citizen.getId(), topComment);
        deletedComment = id("insert into comments(post_id, author_id, content, status, edited, created_at, updated_at) values (?, ?, 'Đã xóa', 'DELETED', false, now(), now())", post, citizen.getId());
        hiddenPost = id("insert into posts(author_id, content, status, edited, created_at, updated_at) values (?, 'Bị gỡ', 'DELETED', false, now(), now())", citizen.getId());
    }

    @Test void chienDichTaiThemKhongTrungKhongSot() throws Exception {
        Set<Integer> ids = new HashSet<>();
        for (int page = 0; page < 3; page++) {
            String body = mvc.perform(get("/api/campaigns").param("status", "ACTIVE").param("page", String.valueOf(page)).param("size", "12")
                    .header("Authorization", bearer(citizen))).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            List<Integer> got = JsonPath.read(body, "$.items[*].id");
            ids.addAll(got);
            assertEquals(30, (int) JsonPath.read(body, "$.total"));
        }
        assertEquals(30, ids.size());
    }

    @Test void motBinhLuanTheoMa() throws Exception {
        mvc.perform(get("/api/comments/" + reply)).andExpect(status().isOk())          // khách xem được như danh sách bình luận
                .andExpect(jsonPath("$.parentCommentId").value(topComment))
                .andExpect(jsonPath("$.content").value("Trả lời"));
        mvc.perform(get("/api/comments/" + topComment)).andExpect(jsonPath("$.replyCount").value(1));
        mvc.perform(get("/api/comments/" + deletedComment)).andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true))
                .andExpect(jsonPath("$.content").value("Bình luận đã bị xóa"));
        long onHidden = id("insert into comments(post_id, author_id, content, status, edited, created_at, updated_at) values (?, ?, 'x', 'VISIBLE', false, now(), now())",
                hiddenPost, citizen.getId());
        mvc.perform(get("/api/comments/" + onHidden)).andExpect(status().isNotFound()); // bài đã bị gỡ
        mvc.perform(get("/api/comments/999999")).andExpect(status().isNotFound());
    }

    @Test void soToChucSanSangDoMayChuDem() throws Exception {
        mvc.perform(get("/api/public/available-orgs")).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(2));
    }
}
