package vn.floodconnect.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.entity.PaymentMethod;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgDocSlot;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.post.entity.Comment;
import vn.floodconnect.post.entity.Follow;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostLike;
import vn.floodconnect.post.repository.CommentRepository;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.post.repository.PostLikeRepository;
import vn.floodconnect.post.repository.PostRepository;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.AssignmentType;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Sinh dữ liệu mẫu để demo. CHỈ chạy khi bật: SEED_DATA=true (app.seed.enabled=true).
 * - Không xóa/sửa dữ liệu đang có; chỉ THÊM tài khoản "seed.*@floodconnect.demo" và dữ liệu đi kèm.
 * - Chạy lại nhiều lần vẫn an toàn (đã có seed thì bỏ qua).
 * - Mật khẩu mọi tài khoản mẫu: Demo12345
 */
@Component
@Order(2)
@ConditionalOnProperty(name = "app.seed.enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class SampleDataSeeder implements CommandLineRunner {
    static final String PASSWORD = "Demo12345";
    static final String MARKER_EMAIL = "seed.citizen01@floodconnect.demo";

    private final UserRepository users;
    private final RescueOrganizationRepository orgs;
    private final OrganizationDocumentRepository orgDocs;
    private final PostRepository posts;
    private final CommentRepository comments;
    private final PostLikeRepository likes;
    private final FollowRepository follows;
    private final IncidentRepository incidents;
    private final IncidentAiAssessmentRepository assessments;
    private final IncidentAssignmentRepository assignments;
    private final FundraisingCampaignRepository campaigns;
    private final DonationRepository donations;
    private final PasswordEncoder encoder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    private final Random rnd = new Random(42);

    // ---------- dữ liệu nguồn ----------
    /** tên địa điểm, tên phường/xã đầy đủ (khớp danh sách vn-admin-2025.json), vĩ độ, kinh độ (Hà Nội và vùng lân cận hay ngập) */
    private record Place(String name, String ward, double lat, double lng) {}
    private static final String SEED_PROVINCE = "Thành phố Hà Nội"; // cùng tên với danh sách tỉnh, để form sửa hồ sơ tổ chức nhận ra
    private static final List<Place> PLACES = List.of(
            new Place("Thanh Trì, Hà Nội", "Xã Thanh Trì", 20.9556, 105.8415),
            new Place("Hoàng Mai, Hà Nội", "Phường Hoàng Mai", 20.9740, 105.8570),
            new Place("Long Biên, Hà Nội", "Phường Long Biên", 21.0450, 105.8900),
            new Place("Gia Lâm, Hà Nội", "Xã Gia Lâm", 21.0200, 105.9400),
            new Place("Đông Anh, Hà Nội", "Xã Đông Anh", 21.1360, 105.8480),
            new Place("Sóc Sơn, Hà Nội", "Xã Sóc Sơn", 21.2580, 105.8480),
            new Place("Chương Mỹ, Hà Nội", "Phường Chương Mỹ", 20.9200, 105.7050),
            new Place("Mỹ Đức, Hà Nội", "Xã Mỹ Đức", 20.6980, 105.7450),
            new Place("Thạch Thất, Hà Nội", "Xã Thạch Thất", 21.0360, 105.5600),
            new Place("Ba Vì, Hà Nội", "Xã Ba Vì", 21.1970, 105.4230));

    private static final String[] CITIZEN_NAMES = {
            "Nguyễn Thị Lan", "Trần Văn Hùng", "Lê Minh Tuấn", "Phạm Thu Hà", "Hoàng Quốc Bảo", "Vũ Ngọc Anh",
            "Đặng Thanh Tùng", "Bùi Thị Mai", "Đỗ Văn Khoa", "Ngô Phương Linh", "Dương Hữu Phúc", "Lý Thị Hạnh"};

    /** cases: số ca xử lý cùng lúc; radiusKm: bán kính hoạt động (km) – tổ chức tự khai báo, AI Matching dùng. */
    private record OrgSeed(String name, OrgType type, OrgStatus status, OperatingStatus op, int cases, double radiusKm,
                           int place, String desc) {}
    private static final List<OrgSeed> ORG_SEEDS = List.of(
            new OrgSeed("Trung tâm Ứng phó Thiên tai Hà Nội", OrgType.STATE, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 8, 40, 2,
                    "Đơn vị nhà nước phụ trách cứu hộ, cứu nạn khi có mưa lũ trên địa bàn thành phố."),
            new OrgSeed("Hội Chữ thập đỏ quận Hoàng Mai", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 5, 20, 1,
                    "Hỗ trợ lương thực, thuốc men và sơ tán người dân vùng ngập."),
            new OrgSeed("Đội xuồng cứu hộ Sông Hồng", OrgType.LOGISTICS, OrgStatus.ACTIVE, OperatingStatus.BUSY, 4, 25, 3,
                    "Có xuồng máy, phao cứu sinh và xe tải vận chuyển nhu yếu phẩm."),
            new OrgSeed("Nhóm thiện nguyện Áo Xanh Thủ đô", OrgType.VOLUNTEER, OrgStatus.ACTIVE, OperatingStatus.AVAILABLE, 3, 15, 6,
                    "Nhóm tình nguyện viên trẻ, hỗ trợ dọn dẹp, phát cơm và đưa người già ra khỏi vùng ngập."),
            new OrgSeed("Quỹ Hạt Giống Yêu Thương", OrgType.SOCIAL, OrgStatus.ACTIVE, OperatingStatus.INACTIVE, 3, 20, 8,
                    "Tổ chức xã hội vận động quyên góp cho người dân vùng lũ."),
            new OrgSeed("Nhóm thiện nguyện Mỹ Đức Chung Tay", OrgType.VOLUNTEER, OrgStatus.PENDING, OperatingStatus.INACTIVE, 2, 10, 7,
                    "Nhóm tình nguyện địa phương, đang chờ Admin xét duyệt hồ sơ."));

    private static final String[] POSTS = {
            "Nước sông Hồng đang lên nhanh, bà con khu vực bãi giữa chuẩn bị di dời đồ đạc lên cao nhé.",
            "Đường vào xã đã ngập khoảng 40cm, xe máy không qua được. Ai cần đi lại nên đi đường vòng.",
            "Sáng nay nhóm mình đã phát 200 suất cơm cho các hộ bị cô lập ở Thanh Trì. Cảm ơn mọi người đã chung tay!",
            "Cần gấp thuốc hạ sốt và nước sạch cho khu tập thể ở Hoàng Mai, khoảng 30 hộ đang bị ngập tầng 1.",
            "Cảnh báo: mưa lớn kéo dài đến tối nay, khu vực Chương Mỹ có nguy cơ sạt lở ven đồi.",
            "Mình vừa đi qua cầu Thanh Trì, nước còn chảy xiết, mọi người không nên chạy xe qua vùng nước chảy.",
            "Tìm tình nguyện viên có xuồng nhỏ hỗ trợ đón người già ở thôn 3, Mỹ Đức.",
            "Cập nhật: điểm sơ tán trường tiểu học đã tiếp nhận 85 người, còn thiếu chăn và mì gói.",
            "Nhà mình ở Đông Anh nước đã vào đến sân, đang chuẩn bị chuyển đồ. Cảm ơn hàng xóm đã giúp.",
            "Xin chia sẻ danh sách số điện thoại khẩn cấp theo khu vực để mọi người tiện liên hệ khi cần.",
            "Đội cứu hộ vừa đưa được 12 người dân ở Long Biên ra khu vực an toàn. Tất cả đều khỏe mạnh.",
            "Nước đã rút ở một số tuyến phố Gia Lâm, bà con cẩn thận bùn đất trơn trượt và dây điện đứt.",
            "Kêu gọi quyên góp áo phao và đèn pin cho đội cứu hộ địa phương. Mọi sự hỗ trợ đều quý giá.",
            "Mẹo nhỏ: khi nước ngập, hãy ngắt cầu dao điện trước, mang theo giấy tờ trong túi nilon kín.",
            "Có ai biết tình hình quốc lộ 6 đoạn qua Chương Mỹ không? Mình cần đưa mẹ đi khám.",
            "Chợ đầu mối hôm nay vẫn hoạt động, nhưng giá rau tăng khá cao. Mọi người cân nhắc dự trữ vừa đủ.",
            "Cảm ơn anh em đội thiện nguyện đã sang giúp nhà mình dọn bùn sau lũ, thật sự xúc động.",
            "Thông báo từ phường: 18h tối nay phát nước sạch và nhu yếu phẩm tại nhà văn hóa tổ dân phố.",
            "Khu vực Sóc Sơn đang mất điện diện rộng, ai có máy phát điện cho mượn sạc điện thoại giúp bà con nhé.",
            "Cập nhật mực nước: sông Đáy tại Ba Vì vượt báo động 2, người dân ven sông cần chú ý.",
            "Mình thấy nhiều bạn nhỏ vẫn lội nước chơi, mong phụ huynh nhắc nhở vì rất nguy hiểm.",
            "Đã có 5 gia đình ở thôn Đông được đón về nhà văn hóa an toàn, cảm ơn đội xuồng cứu hộ.",
            "Cần người hỗ trợ vận chuyển 300 thùng mì và nước đến điểm tập kết ở Thạch Thất.",
            "Chúc bà con vùng lũ bình an. Cả nhà cùng giữ vững tinh thần, nước rồi sẽ rút."};

    private static final String[] COMMENTS = {
            "Cảm ơn bạn đã chia sẻ thông tin!", "Mình cũng đang ở khu vực này, đúng như bạn nói.",
            "Cần hỗ trợ thêm không bạn? Mình có thể qua giúp.", "Bình an nhé bà con!",
            "Đã chia sẻ cho mọi người xung quanh.", "Tình hình hiện giờ thế nào rồi bạn?",
            "Cảm ơn đội cứu hộ rất nhiều!", "Mình xin góp một ít vào quỹ, cảm ơn nhóm đã tổ chức.",
            "Thông tin hữu ích quá, mình lưu lại.", "Chúc mọi người sớm vượt qua khó khăn."};

    private static final String[] REPLIES = {
            "Đúng rồi bạn, mình cũng thấy vậy.", "Cảm ơn bạn nhé!", "Mình sẽ liên hệ bạn qua tin nhắn.",
            "Ok bạn, giữ an toàn nhé.", "Có gì mình cập nhật thêm."};

    private record SosSeed(String desc, int place, DangerLevel ai, boolean verify, double conf,
                           IncidentStatus status, int minutesAgo, String reason) {}
    private static final List<SosSeed> SOS = List.of(
            new SosSeed("Nước dâng cao đến ngực, gia đình có 2 người già và 1 trẻ nhỏ cần sơ tán gấp.", 0, DangerLevel.KHAN_CAP, false, 0.94, IncidentStatus.PENDING, 8, "Có người già, trẻ nhỏ\nMực nước vượt ngực người lớn"),
            new SosSeed("Nhà ngập sâu 1m, cần thực phẩm và nước sạch cho 6 người.", 1, DangerLevel.CAO, false, 0.88, IncidentStatus.PENDING, 25, "Ngập sâu trên 1m\nThiếu lương thực, nước uống"),
            new SosSeed("Đường ngập, xe chết máy giữa đường, cần hỗ trợ kéo xe và đưa người ra.", 2, DangerLevel.TRUNG_BINH, false, 0.81, IncidentStatus.PENDING, 40, "Ngập đường, phương tiện hỏng"),
            new SosSeed("nước ngập", 3, DangerLevel.THAP, true, 0.55, IncidentStatus.PENDING, 55, "Nội dung quá ngắn, nên xác minh"),
            new SosSeed("Sạt lở đất sau nhà, có vết nứt lớn, cần di dời 4 hộ dân gần chân đồi.", 6, DangerLevel.KHAN_CAP, false, 0.91, IncidentStatus.ASSIGNED, 90, "Nguy cơ sạt lở ven đồi\nNhiều hộ dân bị ảnh hưởng"),
            new SosSeed("Bà cụ 80 tuổi bị cô lập trong nhà ngập, cần xuồng đến đón.", 7, DangerLevel.CAO, false, 0.9, IncidentStatus.ASSIGNED, 120, "Người cao tuổi bị cô lập"),
            new SosSeed("Có người bị sốt cao và ho nhiều, nhà đang ngập không đi viện được.", 4, DangerLevel.CAO, false, 0.86, IncidentStatus.ACCEPTED, 180, "Có người bệnh cần y tế\nGiao thông bị chia cắt"),
            new SosSeed("Nước tràn vào nhà, cần bao cát và hỗ trợ kê đồ đạc lên cao.", 5, DangerLevel.TRUNG_BINH, false, 0.78, IncidentStatus.ACCEPTED, 240, "Nước vào nhà, chưa có người nguy hiểm"),
            new SosSeed("Cả xóm 20 hộ bị cô lập, thiếu gạo và mì tôm từ hôm qua.", 8, DangerLevel.CAO, false, 0.87, IncidentStatus.ACCEPTED, 300, "Nhiều hộ thiếu lương thực"),
            new SosSeed("Nhà ngập 60cm, cần người hỗ trợ đưa 2 em nhỏ sang nhà cao tầng.", 0, DangerLevel.CAO, false, 0.89, IncidentStatus.COMPLETED, 1500, "Có trẻ nhỏ"),
            new SosSeed("Đường vào thôn ngập, cần xuồng chở người ốm ra trạm y tế.", 7, DangerLevel.CAO, false, 0.85, IncidentStatus.COMPLETED, 1800, "Cần vận chuyển y tế"),
            new SosSeed("Mất điện và ngập sân, cần đèn pin và nước uống cho gia đình.", 1, DangerLevel.TRUNG_BINH, false, 0.76, IncidentStatus.COMPLETED, 2400, "Thiếu nhu yếu phẩm"),
            new SosSeed("Cây đổ chắn lối vào ngõ, cần hỗ trợ dọn cây và đưa người ra ngoài.", 9, DangerLevel.THAP, false, 0.7, IncidentStatus.COMPLETED, 3000, "Ảnh hưởng nhẹ"),
            new SosSeed("Nước ngập nhẹ ở sân, cần hỗ trợ bơm nước.", 3, DangerLevel.THAP, false, 0.72, IncidentStatus.COMPLETED, 3600, "Mức độ nhẹ"),
            new SosSeed("Gửi nhầm yêu cầu, hiện đã được người nhà hỗ trợ.", 2, DangerLevel.THAP, false, 0.6, IncidentStatus.CANCELLED, 600, "Người dân tự hủy"),
            new SosSeed("Đã nước rút, không còn cần hỗ trợ nữa.", 4, DangerLevel.THAP, false, 0.65, IncidentStatus.CANCELLED, 1000, "Người dân tự hủy"));

    private record CampSeed(String name, String purpose, String area, String audience, long target, CampaignStatus status, int startDaysAgo, int endDaysLeft, int donations) {}
    private static final List<CampSeed> CAMPS = List.of(
            new CampSeed("Chung tay cứu trợ đồng bào vùng lũ Hà Nội", "Mua gạo, mì tôm, nước sạch và thuốc men cho các hộ bị cô lập.", "Thanh Trì, Hoàng Mai, Gia Lâm", "Hộ gia đình bị ngập, người già, trẻ em", 150_000_000L, CampaignStatus.ACTIVE, 6, 20, 14),
            new CampSeed("Áo phao cho đội cứu hộ địa phương", "Trang bị áo phao, đèn pin và xuồng nhỏ cho các đội cứu hộ cơ sở.", "Mỹ Đức, Chương Mỹ", "Đội cứu hộ tình nguyện", 80_000_000L, CampaignStatus.ACTIVE, 3, 12, 9),
            new CampSeed("Bữa cơm nghĩa tình sau lũ", "Nấu và phát 1.000 suất cơm mỗi ngày tại các điểm sơ tán.", "Long Biên, Đông Anh", "Người dân tại điểm sơ tán", 50_000_000L, CampaignStatus.ACTIVE, 2, 7, 6),
            new CampSeed("Dựng lại mái nhà sau bão lũ", "Hỗ trợ sửa nhà cho các hộ có nhà hư hỏng nặng sau đợt lũ.", "Sóc Sơn, Ba Vì", "Hộ nghèo, hộ có nhà hư hỏng", 200_000_000L, CampaignStatus.UPCOMING, -5, 40, 0),
            new CampSeed("Sách vở cho học sinh vùng lũ", "Cấp sách vở, đồ dùng học tập cho học sinh bị mất đồ vì lũ.", "Thạch Thất, Ba Vì", "Học sinh tiểu học và THCS", 30_000_000L, CampaignStatus.ENDED, 25, -5, 8));

    private static final String[] DONOR_MSGS = {
            "Chút tấm lòng gửi bà con vùng lũ.", "Cầu mong mọi người bình an.", "Ủng hộ đội cứu hộ.", "Cố lên Hà Nội!", ""};
    private static final long[] AMOUNTS = {20_000, 50_000, 100_000, 200_000, 500_000, 1_000_000, 2_000_000};

    // ---------- chạy ----------
    @Override
    public void run(String... args) {
        if (users.existsByEmailIgnoreCase(MARKER_EMAIL)) {
            log.info("[seed] Dữ liệu mẫu đã có, bỏ qua.");
            return;
        }
        tx.executeWithoutResult(st -> seedAll());
        log.warn("[seed] Đã sinh dữ liệu mẫu. Mọi tài khoản seed.*@floodconnect.demo dùng mật khẩu: {}", PASSWORD);
    }

    private void seedAll() {
        String hash = encoder.encode(PASSWORD);
        List<User> newCitizens = seedCitizens(hash);
        List<RescueOrganization> newOrgs = seedOrgs(hash);

        // Gộp cả tài khoản bạn đã có vào danh sách dùng để gán dữ liệu
        List<User> citizens = users.findByRole(Role.CITIZEN);
        List<RescueOrganization> activeOrgs = new ArrayList<>(orgs.findByStatus(OrgStatus.ACTIVE));
        activeOrgs.sort((a, b) -> Long.compare(a.getId(), b.getId())); // tổ chức có sẵn của bạn (id nhỏ) đứng đầu
        User admin = users.findByRole(Role.ADMIN).stream().findFirst().orElse(null);

        seedFollows(citizens, activeOrgs);
        List<Post> newPosts = seedPosts(citizens, activeOrgs);
        seedInteractions(newPosts, citizens, activeOrgs);
        seedIncidents(newCitizens.isEmpty() ? citizens : newCitizens, activeOrgs, admin);
        seedCampaigns(activeOrgs, citizens);
        log.info("[seed] {} người dân, {} tổ chức, {} bài viết, {} ca SOS, {} chiến dịch (mới thêm).",
                newCitizens.size(), newOrgs.size(), newPosts.size(), SOS.size(), CAMPS.size());
    }

    private List<User> seedCitizens(String hash) {
        List<User> list = new ArrayList<>();
        for (int i = 0; i < CITIZEN_NAMES.length; i++) {
            Place p = PLACES.get(i % PLACES.size());
            User u = newUser(CITIZEN_NAMES[i], String.format("seed.citizen%02d@floodconnect.demo", i + 1),
                    String.format("0911%06d", i + 1), hash, Role.CITIZEN);
            u.setLatitude(p.lat()); u.setLongitude(p.lng());
            list.add(users.save(u));
        }
        return list;
    }

    private List<RescueOrganization> seedOrgs(String hash) {
        List<RescueOrganization> list = new ArrayList<>();
        for (int i = 0; i < ORG_SEEDS.size(); i++) {
            OrgSeed s = ORG_SEEDS.get(i);
            Place p = PLACES.get(s.place());
            User u = users.save(newUser("Đại diện " + s.name(), String.format("seed.org%02d@floodconnect.demo", i + 1),
                    String.format("0922%06d", i + 1), hash, Role.RESCUE_ORG));
            RescueOrganization o = new RescueOrganization();
            o.setUser(u); o.setName(s.name()); o.setOrgType(s.type());
            o.setProvince(SEED_PROVINCE); o.setWard(p.ward()); o.setStreetAddress("Số " + (10 + i * 7) + " đường Giải Phóng");
            o.setDescription(s.desc()); o.setLatitude(p.lat()); o.setLongitude(p.lng()); o.setServiceAreas(SEED_PROVINCE);
            o.setStatus(s.status()); o.setOperatingStatus(s.op());
            o.setMaxConcurrentCases(s.cases()); o.setServiceRadiusKm(s.radiusKm());
            o = orgs.save(o);
            // Hồ sơ xác minh mẫu (đường dẫn giả, chỉ để màn Admin hiển thị danh sách tệp)
            int d = 1;
            for (OrgDocSlot slot : OrgDocumentService.requiredSlots(s.type())) {
                OrganizationDocument doc = new OrganizationDocument();
                doc.setOrg(o); doc.setSlot(slot); doc.setDocLabel(OrgDocumentService.label(s.type(), slot));
                doc.setStorageKey("https://placehold.co/600x400?text=Ho+so+" + d++); doc.setFileType("IMAGE"); // ảnh mẫu (không phải giấy tờ thật)
                orgDocs.save(doc);
            }
            list.add(o);
        }
        return list;
    }

    private void seedFollows(List<User> citizens, List<RescueOrganization> activeOrgs) {
        for (User c : citizens) {
            // mỗi người dân theo dõi 2-4 tổ chức và vài người dân khác
            for (RescueOrganization o : pick(activeOrgs, 2 + rnd.nextInt(3)))
                saveFollow(c.getId(), o.getUser().getId());
            for (User other : pick(citizens, 2 + rnd.nextInt(3)))
                if (!other.getId().equals(c.getId())) saveFollow(c.getId(), other.getId());
        }
    }

    private void saveFollow(Long follower, Long following) {
        if (!follows.existsByFollowerIdAndFollowingId(follower, following)) follows.save(new Follow(follower, following));
    }

    private List<Post> seedPosts(List<User> citizens, List<RescueOrganization> activeOrgs) {
        List<Post> list = new ArrayList<>();
        for (int i = 0; i < POSTS.length; i++) {
            boolean byOrg = i % 4 == 2 && !activeOrgs.isEmpty();
            User author = byOrg ? activeOrgs.get(rnd.nextInt(activeOrgs.size())).getUser() : citizens.get(rnd.nextInt(citizens.size()));
            Place p = PLACES.get(rnd.nextInt(PLACES.size()));
            Post post = new Post();
            post.setAuthor(author); post.setContent(POSTS[i]);
            post.setLatitude(jitter(p.lat())); post.setLongitude(jitter(p.lng()));
            post = posts.save(post);
            backdate("posts", post.getId(), Instant.now().minus(Duration.ofMinutes(20L + i * 170L + rnd.nextInt(60))));
            list.add(post);
        }
        return list;
    }

    private void seedInteractions(List<Post> postList, List<User> citizens, List<RescueOrganization> activeOrgs) {
        List<User> everyone = new ArrayList<>(citizens);
        activeOrgs.forEach(o -> everyone.add(o.getUser()));
        for (Post p : postList) {
            for (User u : pick(everyone, 2 + rnd.nextInt(Math.min(8, everyone.size() - 1))))
                if (!u.getId().equals(p.getAuthor().getId()) && !likes.existsByPostIdAndUserId(p.getId(), u.getId()))
                    likes.save(new PostLike(p.getId(), u.getId()));
            int nc = rnd.nextInt(4);
            for (int k = 0; k < nc; k++) {
                User who = everyone.get(rnd.nextInt(everyone.size()));
                Comment c = new Comment();
                c.setPost(p); c.setAuthor(who); c.setContent(COMMENTS[rnd.nextInt(COMMENTS.length)]);
                c = comments.save(c);
                Instant t = Instant.now().minus(Duration.ofMinutes(5 + rnd.nextInt(600)));
                backdate("comments", c.getId(), t);
                if (rnd.nextInt(3) == 0) { // một số bình luận có phản hồi
                    User replier = p.getAuthor();
                    Comment r = new Comment();
                    r.setPost(p); r.setAuthor(replier); r.setParentComment(c); r.setReplyToUser(who);
                    r.setContent(REPLIES[rnd.nextInt(REPLIES.length)]);
                    r = comments.save(r);
                    backdate("comments", r.getId(), t.plus(Duration.ofMinutes(3 + rnd.nextInt(30))));
                }
            }
        }
    }

    private void seedIncidents(List<User> citizens, List<RescueOrganization> activeOrgs, User admin) {
        int orgIdx = 0;
        for (int i = 0; i < SOS.size(); i++) {
            SosSeed s = SOS.get(i);
            User citizen = citizens.get(i % citizens.size());
            if (SosService.ACTIVE.contains(s.status())) { // mỗi người dân chỉ có một ca đang hoạt động (chỉ mục duy nhất, Flyway V1)
                citizen = citizens.stream().filter(c -> !incidents.existsByCitizenIdAndStatusIn(c.getId(), SosService.ACTIVE)).findFirst().orElse(null);
                if (citizen == null) continue; // mọi người dân đều đang có ca: bỏ qua ca mẫu này
            }
            Place p = PLACES.get(s.place());
            Instant created = Instant.now().minus(Duration.ofMinutes(s.minutesAgo()));
            Incident inc = new Incident();
            inc.setCode("TMP-SEED-" + System.nanoTime() + "-" + i);
            inc.setCitizen(citizen);
            inc.setDescription(s.desc()); inc.setAddress(p.name());
            inc.setLatitude(jitter(p.lat())); inc.setLongitude(jitter(p.lng()));
            inc.setStatus(s.status());
            inc.setAiDangerLevel(s.ai()); inc.setNeedsVerification(s.verify());
            inc.setDangerLevel(s.ai());
            RescueOrganization org = null;
            if (s.status() == IncidentStatus.ASSIGNED || s.status() == IncidentStatus.ACCEPTED || s.status() == IncidentStatus.COMPLETED) {
                if (!activeOrgs.isEmpty()) org = activeOrgs.get(orgIdx++ % activeOrgs.size());
                inc.setAssignedOrg(org);
                inc.setAssignedAt(created.plus(Duration.ofMinutes(10)));
                if (s.status() != IncidentStatus.ASSIGNED) inc.setAcceptedAt(created.plus(Duration.ofMinutes(16)));
                if (s.status() == IncidentStatus.COMPLETED) inc.setCompletedAt(created.plus(Duration.ofMinutes(140)));
            }
            inc = incidents.save(inc);
            inc.setCode("SOS-" + (1000 + inc.getId()));
            inc = incidents.saveAndFlush(inc); // flush trước khi lùi thời gian, tránh bị ghi đè updated_at
            backdate("incidents", inc.getId(), created);

            IncidentAiAssessment a = new IncidentAiAssessment();
            a.setIncident(inc); a.setPredictedLevel(s.ai()); a.setConfidence(s.conf()); a.setNeedsVerification(s.verify());
            a.setNearbySosCount(rnd.nextInt(4));
            a.setWeatherData("{\"rain_mm_24h\":" + (40 + rnd.nextInt(120)) + ",\"forecast\":\"Mưa to đến rất to\",\"alert\":\"Cảnh báo ngập lụt\"}");
            a.setReasons(s.reason());
            a = assessments.save(a);
            backdate("incident_ai_assessments", a.getId(), created, false);

            if (org != null) {
                IncidentAssignment as = new IncidentAssignment();
                as.setIncident(inc); as.setOrg(org);
                boolean claim = i % 2 == 0;
                as.setType(claim ? AssignmentType.ORG_CLAIM : AssignmentType.ADMIN_ASSIGN);
                if (!claim) as.setAssignedBy(admin);
                as.setStatus(s.status() == IncidentStatus.ASSIGNED ? AssignmentStatus.WAITING_CONFIRM : AssignmentStatus.ACCEPTED);
                if (s.status() != IncidentStatus.ASSIGNED) as.setRespondedAt(created.plus(Duration.ofMinutes(16)));
                as = assignments.save(as);
                backdate("incident_assignments", as.getId(), created.plus(Duration.ofMinutes(10)), false);
            }
        }
    }

    private static final String[] SEED_BANKS = {"Vietcombank", "BIDV", "Agribank", "MB Bank", "VietinBank", "Techcombank"};

    private void seedCampaigns(List<RescueOrganization> activeOrgs, List<User> citizens) {
        if (activeOrgs.isEmpty()) return;
        int txn = 0;
        for (int i = 0; i < CAMPS.size(); i++) {
            CampSeed s = CAMPS.get(i);
            RescueOrganization org = activeOrgs.get(i % activeOrgs.size());
            FundraisingCampaign c = new FundraisingCampaign();
            c.setOrg(org); c.setName(s.name()); c.setPurpose(s.purpose()); c.setSupportArea(s.area());
            c.setTargetAudience(s.audience()); c.setOrgInfo(org.getName());
            c.setTargetAmount(s.target());
            c.setBankName(SEED_BANKS[i % SEED_BANKS.length]);
            c.setAccountHolder(org.getName().toUpperCase().replace("Đ", "D")); c.setAccountNumber("0" + (900000000L + i * 1111111L));
            c.setStartDate(Instant.now().minus(Duration.ofDays(s.startDaysAgo())));
            c.setEndDate(Instant.now().plus(Duration.ofDays(s.endDaysLeft())));
            c.setStatus(s.status());
            c = campaigns.save(c);

            long raised = 0;
            for (int d = 0; d < s.donations(); d++) {
                long amount = AMOUNTS[rnd.nextInt(AMOUNTS.length)];
                if (s.status() == CampaignStatus.ENDED) amount = Math.max(amount, s.target() / s.donations() + 1);
                Donation don = new Donation();
                don.setCampaign(c); don.setDonor(citizens.get(rnd.nextInt(citizens.size())));
                don.setAmount(amount); don.setMessage(DONOR_MSGS[rnd.nextInt(DONOR_MSGS.length)]);
                don.setPaymentMethod(PaymentMethod.VNPAY); don.setAnonymous(rnd.nextInt(5) == 0);
                don.setTransactionCode("SEED-" + System.currentTimeMillis() + "-" + (txn++));
                don.setGatewayRef("MOCK-" + txn);
                don.setStatus(DonationStatus.SUCCESS);
                don.setCompletedAt(Instant.now().minus(Duration.ofHours(1 + rnd.nextInt(100))));
                don = donations.save(don);
                backdate("donations", don.getId(), don.getCompletedAt(), false);
                raised += amount;
            }
            c.setRaisedAmount(raised);
            if (s.status() == CampaignStatus.ENDED) {
                c.setEndReason(raised >= s.target() ? EndReason.TARGET_REACHED : EndReason.EXPIRED);
                c.setEndedAt(Instant.now().minus(Duration.ofDays(5)));
            }
            campaigns.save(c);
        }
    }

    // ---------- tiện ích ----------
    private User newUser(String name, String email, String phone, String hash, Role role) {
        User u = new User();
        u.setFullName(name); u.setEmail(email); u.setPhone(phone); u.setPasswordHash(hash);
        u.setRole(role); u.setEmailVerified(true);
        return u;
    }

    private double jitter(double v) { return Math.round((v + (rnd.nextDouble() - 0.5) * 0.02) * 10000d) / 10000d; }

    private <T> List<T> pick(List<T> src, int n) {
        List<T> copy = new ArrayList<>(src);
        java.util.Collections.shuffle(copy, rnd);
        return copy.subList(0, Math.min(n, copy.size()));
    }

    /** @CreationTimestamp luôn ghi giờ hiện tại nên dùng SQL để lùi thời gian cho dữ liệu trông tự nhiên. */
    private void backdate(String table, Long id, Instant at) { backdate(table, id, at, true); }

    private void backdate(String table, Long id, Instant at, boolean alsoUpdatedAt) {
        Timestamp ts = Timestamp.from(at);
        if (alsoUpdatedAt) jdbc.update("update " + table + " set created_at = ?, updated_at = ? where id = ?", ts, ts, id);
        else jdbc.update("update " + table + " set created_at = ? where id = ?", ts, id);
    }
}