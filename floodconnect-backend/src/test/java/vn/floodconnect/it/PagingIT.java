package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Máy chủ tự giới hạn tham số phân trang client gửi lên (Paging): size tối đa 100, size ≤ 0 và page âm không gây lỗi,
 * trang quá xa trả trang rỗng thay vì lỗi tràn số. Gọi qua API thật trên CSDL thật.
 */
class PagingIT extends AbstractIT {
    private User admin;

    @BeforeEach void setUp() {
        for (int k = 0; k < 3; k++) user(Role.CITIZEN);
        admin = user(Role.ADMIN);
    }

    @Test void sizeQuaLonBiKepVe100() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "").param("type", "CITIZEN").param("size", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(Paging.MAX_SIZE))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/admin/citizens/search").param("q", "").param("size", "5000").header("Authorization", bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(Paging.MAX_SIZE));
    }

    @Test void sizeKhongVaPageAmKhongBaoLoi() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "").param("type", "CITIZEN").param("size", "0").param("page", "-3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test void trangQuaXaTraTrangRong() throws Exception {
        mvc.perform(get("/api/users/search").param("q", "").param("type", "CITIZEN")
                        .param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.total").value(3));
        mvc.perform(get("/api/posts").param("page", String.valueOf(Integer.MAX_VALUE)).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }
}
