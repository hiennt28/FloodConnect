package vn.floodconnect.it;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.security.JwtService;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.List;

/**
 * Lớp nền cho kiểm thử tích hợp: khởi động ứng dụng thật (Spring, Hibernate, Flyway, Spring Security) trên CSDL PostgreSQL
 * + PostGIS riêng cho test (floodconnect_test, xem application-it.yml) và gọi API qua MockMvc như frontend gọi.
 *
 * Trước MỖI bài test: xóa sạch dữ liệu mọi bảng (trừ địa giới và lịch sử Flyway) để các bài test không ảnh hưởng nhau.
 * Không dùng @Transactional cho bài test: để mỗi lời gọi API commit/rollback y như khi chạy thật (lỗi #1 chỉ lộ ra như vậy).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("it")
public abstract class AbstractIT {
    /** Bảng không xóa giữa các bài test: dữ liệu địa giới (do Flyway V2 nạp), lịch sử Flyway, bảng hệ tọa độ của PostGIS. */
    private static final List<String> KEEP = List.of("admin_wards", "flyway_schema_history", "spatial_ref_sys");

    @Autowired protected MockMvc mvc;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected UserRepository users;
    @Autowired protected RescueOrganizationRepository orgs;
    @Autowired protected PasswordEncoder encoder;
    @Autowired protected JwtService jwt;

    @BeforeEach
    void cleanDatabase() {
        String db = jdbc.queryForObject("select current_database()", String.class);
        // Chốt an toàn: tuyệt đối không xóa dữ liệu của CSDL thật
        if (db == null || !db.endsWith("_test"))
            throw new IllegalStateException("Kiểm thử tích hợp chỉ chạy trên CSDL có tên kết thúc bằng _test, đang nối tới: " + db);
        List<String> tables = jdbc.queryForList("select tablename from pg_tables where schemaname = 'public'", String.class)
                .stream().filter(t -> !KEEP.contains(t)).toList();
        if (!tables.isEmpty()) jdbc.execute("TRUNCATE " + String.join(", ", tables) + " RESTART IDENTITY CASCADE");
    }

    private int seq = 0;

    /** Tạo tài khoản đã xác thực email, mật khẩu "Demo12345". */
    protected User user(Role role) {
        seq++;
        User u = new User();
        u.setFullName(role + " " + seq); u.setEmail(role.name().toLowerCase() + seq + "@it.test"); u.setPhone("0911" + String.format("%06d", seq));
        u.setPasswordHash(encoder.encode("Demo12345")); u.setRole(role); u.setEmailVerified(true);
        return users.save(u);
    }

    /** Tổ chức đã duyệt (ACTIVE), Sẵn sàng nhận ca, trụ sở tại (lat, lng). */
    protected RescueOrganization activeOrg(User u, double lat, double lng) {
        RescueOrganization o = new RescueOrganization();
        o.setUser(u); o.setName("Đội cứu hộ " + u.getId()); o.setOrgType(OrgType.VOLUNTEER);
        o.setStatus(OrgStatus.ACTIVE); o.setOperatingStatus(OperatingStatus.AVAILABLE); o.setLatitude(lat); o.setLongitude(lng);
        return orgs.save(o);
    }

    protected String bearer(User u) { return "Bearer " + jwt.generate(u); }
}
