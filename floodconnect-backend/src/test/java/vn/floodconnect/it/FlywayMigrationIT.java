package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Flyway dựng CSDL từ đầu (V1, V2, V3...) và Hibernate kiểm tra (ddl-auto=validate) entity khớp CSDL.
 * Việc ứng dụng khởi động được cho các bài test này đã là bằng chứng "validate" thành công.
 */
class FlywayMigrationIT extends AbstractIT {
    @Autowired Environment env;

    @Test void moiMigrationDeuChayThanhCong() {
        List<Map<String, Object>> rows = jdbc.queryForList("select version, success from flyway_schema_history where version is not null order by installed_rank");
        List<String> versions = rows.stream().map(r -> String.valueOf(r.get("version"))).toList();
        assertTrue(versions.containsAll(List.of("1", "2", "3")), "đã chạy: " + versions);
        assertTrue(rows.stream().allMatch(r -> Boolean.TRUE.equals(r.get("success"))), "có migration thất bại: " + rows);
    }

    @Test void hibernateChiKiemTraKhongTuSuaCsdl() {
        assertEquals("validate", env.getProperty("spring.jpa.hibernate.ddl-auto"));
    }

    @Test void coDuCacPhanMaHibernateKhongTaoDuoc() {
        assertEquals(3321, jdbc.queryForObject("select count(*) from admin_wards", Integer.class), "dữ liệu địa giới (V2)");
        assertEquals(2, jdbc.queryForObject("select count(*) from information_schema.columns where column_name = 'geog' "
                + "and table_name in ('incidents', 'rescue_organizations')", Integer.class), "cột vị trí PostGIS");
        assertEquals(1, jdbc.queryForObject("select count(*) from pg_indexes where indexname = 'uq_incidents_one_active_per_citizen'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from pg_constraint where conname = 'uk3wqaabxbp24ko89s7lkjxch2v'", Integer.class),
                "ràng buộc unique sai trên mã OTP phải bị V3 xóa");
        assertEquals(8, jdbc.queryForObject("select count(*) from pg_indexes where indexname in ('idx_incidents_status_created', "
                + "'idx_incidents_assigned_org', 'idx_posts_status_created', 'idx_comments_post', 'idx_notifications_user', "
                + "'idx_donations_campaign', 'idx_campaigns_status_start', 'idx_auth_tokens_user_type')", Integer.class));
    }

    /** Địa giới dùng được thật: Hồ Gươm nằm trong một phường của Hà Nội. */
    @Test void traPhuongXaTheoToaDo() throws Exception {
        mvc.perform(get("/api/geo/locate").param("lat", "21.0285").param("lng", "105.8522"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ward.provinceName").value("Thành phố Hà Nội"));
    }
}
