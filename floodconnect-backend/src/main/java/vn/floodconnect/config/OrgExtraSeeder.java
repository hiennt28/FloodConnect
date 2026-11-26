package vn.floodconnect.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgDocSlot;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.List;

/**
 * Bổ sung 30 tổ chức cứu hộ mẫu (chỉ tổ chức, không tạo ca SOS) (Hà Nội) để thử AI Matching. Chạy khi SEED_DATA=true, SAU SampleDataSeeder.
 *
 * Khác SampleDataSeeder (đã seed một lần thì bỏ qua toàn bộ): seeder này tạo TỪNG tổ chức nếu email
 * seed.orgxNN@floodconnect.demo chưa có, nên CSDL đã seed từ trước vẫn nhận thêm, và chạy lại nhiều lần vẫn an toàn.
 * Không xóa / sửa dữ liệu đang có. Mật khẩu mọi tài khoản: Demo12345.
 *
 * Mô tả được viết cho việc thử matching: mỗi tổ chức một chuyên môn rõ (để điểm ngữ nghĩa S phân biệt được), nhiều tổ chức
 * cùng nhóm năng lực ở gần nhau, có câu "không làm …" (để thử phủ định), có tổ chức đang bận / tạm dừng / chờ duyệt.
 * Số ca xử lý cùng lúc (1–12) và bán kính hoạt động (5–60 km) khác nhau để thấy rõ điểm tải W và vùng phục vụ của L.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class OrgExtraSeeder implements CommandLineRunner {
    static final String EMAIL = "seed.orgx%02d@floodconnect.demo";
    static final String PROVINCE = "Thành phố Hà Nội";

    private final UserRepository users;
    private final RescueOrganizationRepository orgs;
    private final OrganizationDocumentRepository orgDocs;
    private final PasswordEncoder encoder;
    private final TransactionTemplate tx;

    /** ward: tên phường/xã khớp danh sách vn-admin-2025.json (cùng các địa điểm của SampleDataSeeder). */
    /** cases: số ca xử lý cùng lúc; radiusKm: bán kính hoạt động (km) – hai giá trị tổ chức tự khai báo, AI Matching dùng. */
    record Seed(String name, OrgType type, OrgStatus status, OperatingStatus op, int cases, double radiusKm,
                String ward, double lat, double lng, String areas, String desc) {}

    static final List<Seed> SEEDS = List.of(
            // ---------------- Cứu nạn trên nước
            new Seed("Đội cứu nạn đường thủy Long Biên", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 10, 40,
                    "Phường Long Biên", 21.0478, 105.8862, "Long Biên, Gia Lâm, ven sông Hồng và sông Đuống",
                    "Đội cứu nạn chuyên nghiệp có 3 ca nô và 6 xuồng máy. Cứu người mắc kẹt trong nhà ngập, trên mái nhà, ven sông. "
                            + "Có thợ lặn và dây cứu sinh, quen cứu người ở vùng nước chảy xiết. Trực 24/7."),
            new Seed("Nhóm xuồng tình nguyện Thanh Trì", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 2, 10,
                    "Xã Thanh Trì", 20.9581, 105.8452, "Thanh Trì, Hoàng Mai",
                    "Nhóm thanh niên tình nguyện có 4 xuồng nhỏ chèo tay. Đưa người già, trẻ em từ nhà ngập ra chỗ cao trong ngõ nhỏ, "
                            + "nước tĩnh. Không có thợ lặn, không đi vào vùng nước chảy xiết."),
            new Seed("Đội cứu hộ ven sông Đuống", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.BUSY, 4, 25,
                    "Xã Đông Anh", 21.1392, 105.8521, "Đông Anh, Gia Lâm",
                    "Có xuồng máy, áo phao và xe tải lội nước cao. Cứu nạn và di dời người dân vùng bãi ven sông Đuống. "
                            + "Hiện đang hỗ trợ ca lớn nên tạm thời bận."),
            // ---------------- Y tế
            new Seed("Đội y tế lưu động Hoàng Mai", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 6, 25,
                    "Phường Hoàng Mai", 20.9763, 105.8604, "Hoàng Mai, Thanh Trì",
                    "Đội y tế có bác sĩ, điều dưỡng và xe cứu thương. Sơ cứu chấn thương, cầm máu, băng bó vết thương, "
                            + "cấp cứu người bị điện giật, đuối nước tại hiện trường."),
            new Seed("Nhóm hỗ trợ bệnh nhân mạn tính Hà Nội", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 3, 30,
                    "Phường Hoàng Mai", 20.9712, 105.8531, "Nội thành Hà Nội",
                    "Hỗ trợ y tế cho người bệnh mạn tính trong mùa lũ: đưa đón người chạy thận, điều trị ung thư đến bệnh viện đúng lịch, "
                            + "mang thuốc tiểu đường, huyết áp đến tận nhà. Không nhận sơ cứu chấn thương tại hiện trường."),
            new Seed("Bác sĩ nhi tình nguyện Long Biên", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 2, 15,
                    "Phường Long Biên", 21.0423, 105.8936, "Long Biên, Gia Lâm",
                    "Nhóm bác sĩ nhi khoa tình nguyện. Khám và xử trí trẻ em sốt cao, co giật, tiêu chảy, mất nước trong vùng ngập. "
                            + "Có nữ hộ sinh hỗ trợ sản phụ và trẻ sơ sinh."),
            new Seed("Trạm y tế dã chiến Chương Mỹ", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 8, 30,
                    "Phường Chương Mỹ", 20.9231, 105.7012, "Chương Mỹ, Mỹ Đức, Thạch Thất",
                    "Trạm y tế dã chiến tại điểm sơ tán: khám bệnh, phát thuốc, phun khử trùng nguồn nước sau lũ. "
                            + "Có cáng và xe cứu thương chuyển người bệnh nặng lên bệnh viện huyện."),
            // ---------------- Lương thực, nước uống
            new Seed("Bếp cơm nghĩa tình Gia Lâm", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 5, 15,
                    "Xã Gia Lâm", 21.0235, 105.9372, "Gia Lâm, Long Biên",
                    "Bếp ăn tình nguyện nấu suất ăn số lượng lớn, mỗi ngày 800 đến 1.000 suất cơm hộp cho điểm sơ tán và khu đông người. "
                            + "Không vận chuyển vào vùng bị cô lập bằng đường thủy."),
            new Seed("Nhóm Mẹ và Bé vùng lũ", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 3, 20,
                    "Phường Hoàng Mai", 20.9790, 105.8592, "Hoàng Mai, Thanh Trì, Long Biên",
                    "Cứu trợ nhu yếu phẩm cho gia đình có con nhỏ: sữa cho trẻ sơ sinh, tã bỉm, cháo dinh dưỡng, đồ dùng cho sản phụ. "
                            + "Giao tận nhà bằng xe máy ở khu nước nông."),
            new Seed("Đội nước sạch Đông Anh", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 4, 30,
                    "Xã Đông Anh", 21.1331, 105.8444, "Đông Anh, Sóc Sơn",
                    "Cung cấp nước uống: chở nước đóng bình, lắp máy lọc nước di động tại các xóm có giếng bị nhiễm bẩn. "
                            + "Phát kèm viên lọc nước và hướng dẫn xử lý nước sau lũ."),
            new Seed("Kho cứu trợ Sóc Sơn", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 6, 50,
                    "Xã Sóc Sơn", 21.2554, 105.8511, "Sóc Sơn, Đông Anh",
                    "Kho lương thực phát gạo, mì gói, lương khô theo hộ gia đình. Có xe tải và xe địa hình đưa hàng vào thôn bị chia cắt."),
            // ---------------- Sạt lở, cây đổ
            new Seed("Đội ứng cứu sạt lở Ba Vì", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 5, 35,
                    "Xã Ba Vì", 21.1998, 105.4271, "Ba Vì, Thạch Thất, vùng đồi núi phía tây",
                    "Chuyên ứng cứu sạt lở đồi núi: đào bới cứu người bị vùi lấp, đánh giá nguy cơ sạt taluy, sơ tán nhà ở chân đồi. "
                            + "Có máy xúc, máy ủi và thiết bị dò tìm."),
            new Seed("Đội dọn cây đổ nội thành", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 6, 12,
                    "Phường Long Biên", 21.0461, 105.8817, "Nội thành Hà Nội",
                    "Cưa cắt cây đổ, dọn đường phố sau bão, có xe cẩu gỡ cây đè lên nhà và ô tô. "
                            + "Chỉ làm trong nội thành, không nhận việc ở vùng đồi núi."),
            new Seed("Đội thông đường Thạch Thất", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.BUSY, 3, 20,
                    "Xã Thạch Thất", 21.0389, 105.5642, "Thạch Thất, Ba Vì",
                    "Dọn đường, khơi thông cống rãnh, giải phóng đường cho xe cứu thương vào thôn sau sạt lở nhỏ."),
            // ---------------- Tìm người
            new Seed("Đội tìm kiếm cứu nạn Mỹ Đức", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 4, 30,
                    "Xã Mỹ Đức", 20.7012, 105.7488, "Mỹ Đức, Chương Mỹ",
                    "Tìm kiếm người mất tích trong rừng, trên núi và người bị nước lũ cuốn trôi theo suối. "
                            + "Có thiết bị định vị, flycam và người thông thạo địa hình."),
            new Seed("Nhóm kết nối người thân mất liên lạc", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 10, 60,
                    "Phường Long Biên", 21.0447, 105.8879, "Toàn thành phố",
                    "Tiếp nhận thông tin, gọi điện, đăng tin và phối hợp chính quyền xã để tìm người thân mất liên lạc trong mưa lũ. "
                            + "Không có lực lượng hiện trường."),
            // ---------------- Di dời, chỗ ở tạm
            new Seed("Đội di dời người yếu thế Chương Mỹ", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 3, 15,
                    "Phường Chương Mỹ", 20.9178, 105.7093, "Chương Mỹ, Thanh Oai",
                    "Di dời người già, người nằm liệt, người khuyết tật ra khỏi vùng ngập: có cáng, xe lăn, xe nâng và người đã được tập huấn."),
            new Seed("Xe khách sơ tán Gia Lâm", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 8, 40,
                    "Xã Gia Lâm", 21.0168, 105.9431, "Gia Lâm, Long Biên, Đông Anh",
                    "Có 10 xe khách và xe tải chở số đông người dân, công nhân, sinh viên ra điểm sơ tán an toàn."),
            new Seed("Mái ấm tạm cư Thanh Trì", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.INACTIVE, 2, 20,
                    "Xã Thanh Trì", 20.9532, 105.8389, "Thanh Trì, Hoàng Mai",
                    "Bố trí chỗ ở tạm, nhà tạm và lều bạt cho gia đình mất nhà; có khu riêng cho người mang theo vật nuôi. "
                            + "Hiện tạm dừng tiếp nhận vì đã kín chỗ."),
            // ---------------- Bổ sung: cùng khu vực nhưng năng lực tiếp nhận khác nhau (thử điểm tải W và bán kính hoạt động)
            new Seed("Đội cứu hộ xuồng lớn Hoàng Mai", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 12, 30,
                    "Phường Hoàng Mai", 20.9725, 105.8590, "Hoàng Mai, Thanh Trì, Long Biên",
                    "Đơn vị cứu nạn có 8 xuồng máy và 40 cán bộ chia thành nhiều tổ, xử lý được nhiều ca cùng lúc. "
                            + "Cứu người mắc kẹt trong nhà ngập, sơ tán người già, trẻ em ra điểm an toàn."),
            new Seed("Tổ xuồng nhỏ phường Hoàng Mai", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 1, 5,
                    "Phường Hoàng Mai", 20.9748, 105.8552, "Trong phường Hoàng Mai",
                    "Tổ dân phố tự lập, có 1 xuồng chèo tay và 4 người. Chỉ hỗ trợ đưa người ra khỏi nhà ngập trong phạm vi phường."),
            new Seed("Đội y tế cơ động Thanh Trì", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 5, 20,
                    "Xã Thanh Trì", 20.9560, 105.8430, "Thanh Trì, Hoàng Mai",
                    "Có bác sĩ, điều dưỡng và xe cứu thương gầm cao. Sơ cứu, cấp cứu ban đầu, chuyển bệnh nhân chạy thận, "
                            + "người có bệnh nền lên bệnh viện khi đường bị ngập."),
            new Seed("Nhóm sơ cứu sinh viên Y Hà Nội", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 2, 10,
                    "Phường Hoàng Mai", 20.9790, 105.8520, "Hoàng Mai",
                    "Sinh viên y khoa tình nguyện sơ cứu vết thương nhẹ, đo huyết áp, phát thuốc thông thường. "
                            + "Không có phương tiện vận chuyển bệnh nhân."),
            new Seed("Đội cứu hộ liên huyện phía Nam", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 10, 60,
                    "Xã Mỹ Đức", 20.7000, 105.7500, "Mỹ Đức, Chương Mỹ, Thanh Oai, Ứng Hòa, Phú Xuyên",
                    "Lực lượng cứu hộ phụ trách nhiều huyện phía Nam thành phố, có ca nô, xe lội nước và đội tìm kiếm. "
                            + "Ứng cứu cả các xã xa trung tâm."),
            new Seed("Đội phản ứng nhanh Gia Lâm", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 4, 15,
                    "Xã Gia Lâm", 21.0210, 105.9380, "Gia Lâm, Long Biên",
                    "Có 2 xuồng máy và xe tải nhỏ. Cứu người mắc kẹt, chở nhu yếu phẩm vào các thôn bị chia cắt."),
            new Seed("Hội tình nguyện xe bán tải Đông Anh", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 3, 25,
                    "Xã Đông Anh", 21.1370, 105.8500, "Đông Anh, Sóc Sơn, Mê Linh",
                    "Nhóm chủ xe bán tải gầm cao, chở người và đồ đạc ra khỏi vùng ngập nông. Không có xuồng, không vào được vùng nước sâu."),
            new Seed("Đội cứu hộ miền núi Thạch Thất", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 6, 35,
                    "Xã Thạch Thất", 21.0370, 105.5620, "Thạch Thất, Quốc Oai, Ba Vì",
                    "Chuyên ứng cứu sạt lở đất, đá lăn, lũ quét ở vùng đồi núi phía tây; có máy xúc nhỏ và thiết bị tìm kiếm người bị vùi lấp."),
            new Seed("Nhóm cứu trợ lương thực Long Biên", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 8, 20,
                    "Phường Long Biên", 21.0460, 105.8890, "Long Biên, Gia Lâm",
                    "Phát gạo, mì, nước uống, sữa cho các hộ bị cô lập. Có điểm tập kết hàng và xe chở đến tận nơi."),
            new Seed("Đội y tế và xuồng Chương Mỹ", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.BUSY, 4, 25,
                    "Phường Chương Mỹ", 20.9210, 105.7030, "Chương Mỹ, Mỹ Đức",
                    "Kết hợp xuồng máy và nhân viên y tế: vừa đưa người ra khỏi nhà ngập vừa sơ cứu tại chỗ. Hiện đang bận nhiều ca."),
            new Seed("Nhóm cứu hộ cộng đồng Ba Vì", OrgType.VOLUNTEER, OrgStatus.PENDING, OperatingStatus.INACTIVE, 2, 15,
                    "Xã Ba Vì", 21.1945, 105.4189, "Ba Vì",
                    "Nhóm tình nguyện địa phương mới thành lập, hỗ trợ cứu trợ và di dời ở các thôn ven núi. Đang chờ Admin duyệt hồ sơ."));

    @Override
    public void run(String... args) {
        int added = tx.execute(st -> seed());
        if (added > 0) log.warn("[seed] Đã thêm {} tổ chức mẫu (seed.orgxNN@floodconnect.demo, mật khẩu {}).", added, SampleDataSeeder.PASSWORD);
        else log.info("[seed] {} tổ chức bổ sung đã có, bỏ qua.", SEEDS.size());
    }

    private int seed() {
        String hash = null;
        int added = 0;
        for (int i = 0; i < SEEDS.size(); i++) {
            String email = String.format(EMAIL, i + 1);
            if (users.existsByEmailIgnoreCase(email)) continue;
            if (hash == null) hash = encoder.encode(SampleDataSeeder.PASSWORD);
            Seed s = SEEDS.get(i);
            User u = new User();
            u.setFullName("Đại diện " + s.name()); u.setEmail(email); u.setPhone(String.format("0933%06d", i + 1));
            u.setPasswordHash(hash); u.setRole(Role.RESCUE_ORG); u.setEmailVerified(true);
            u = users.save(u);
            RescueOrganization o = new RescueOrganization();
            o.setUser(u); o.setName(s.name()); o.setOrgType(s.type());
            o.setProvince(PROVINCE); o.setWard(s.ward()); o.setStreetAddress("Số " + (5 + i * 3) + " đường Ven Đê");
            o.setDescription(s.desc()); o.setLatitude(s.lat()); o.setLongitude(s.lng()); o.setServiceAreas(s.areas());
            o.setStatus(s.status()); o.setOperatingStatus(s.op());
            o.setMaxConcurrentCases(s.cases()); o.setServiceRadiusKm(s.radiusKm());
            o = orgs.save(o);
            int d = 1;
            for (OrgDocSlot slot : OrgDocumentService.requiredSlots(s.type())) {   // hồ sơ mẫu (ảnh giữ chỗ, không phải giấy tờ thật)
                OrganizationDocument doc = new OrganizationDocument();
                doc.setOrg(o); doc.setSlot(slot); doc.setDocLabel(OrgDocumentService.label(s.type(), slot));
                doc.setStorageKey("https://placehold.co/600x400?text=Ho+so+" + d++); doc.setFileType("IMAGE");
                orgDocs.save(doc);
            }
            added++;
        }
        return added;
    }
}
