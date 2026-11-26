package vn.floodconnect.rescue.matching;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.ai.dto.AiDtos.ActiveCase;
import vn.floodconnect.ai.dto.AiDtos.MatchOrg;
import vn.floodconnect.ai.dto.AiDtos.RemoteExclusion;
import vn.floodconnect.ai.dto.AiDtos.RemoteMatch;
import vn.floodconnect.ai.dto.AiDtos.RemoteMatchItem;
import vn.floodconnect.ai.service.AiClient;
import vn.floodconnect.ai.service.CapabilityRules;
import vn.floodconnect.common.util.Geo;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentMatchingScore;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.rescue.repository.IncidentMatchingScoreRepository;
import vn.floodconnect.common.util.Counts;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AI Matching. Có dịch vụ AI (app.ai.service-url): gọi POST /v1/match của floodconnect-ai: luật cứng (không hoạt động, quá xa,
 * tuyên bố không hỗ trợ nhu cầu chính, quá tải), điểm = wC·C + wS·S + wL·L + wW·W với trọng số đổi theo mức đầy đủ của tin SOS,
 * C phân biệt "mô tả không nhắc tới" với "tuyên bố KHÔNG hỗ trợ", S bằng embedding BGE-M3 (chấm tương đối giữa các tổ chức).
 * Chưa cấu hình / AI lỗi: công thức dự phòng trong Java bên dưới.
 *
 * Công thức dự phòng: MatchingScore = 0,25·L + 0,30·S + 0,30·C + 0,15·W (mỗi thành phần 0..100).
 *  L – vị trí: khoảng cách từ trụ sở tổ chức tới ca SOS.
 *  S – tương đồng ngữ nghĩa (embedding): mô tả ca + ghi chú xác minh của Admin so với tên, mô tả, khu vực hoạt động của tổ chức.
 *  C – năng lực: nhu cầu của ca (từ mô tả + ghi chú xác minh) được tổ chức hỗ trợ bao nhiêu phần (năng lực suy ra từ mô tả tổ chức),
 *      xem {@link CapabilityRules}.
 *  W – tình trạng tiếp nhận: số ca đang nhận, ca gần vị trí, số lần từ chối / không phản hồi trong vài giờ gần đây.
 * AI chỉ gợi ý/xếp hạng, không tự giao ca.
 */
@Service
public class MatchingService {
    public static final double W_LOCATION = 0.25, W_SEMANTIC = 0.30, W_CAPABILITY = 0.30, W_WORKLOAD = 0.15;
    static final Map<String, Integer> RULE_WEIGHTS = Map.of("L", 25, "S", 30, "C", 30, "W", 15);
    public static final String SOURCE_AI = "AI_SERVICE", SOURCE_RULES = "RULES";
    /** Chấm một tổ chức qua AI: gửi kèm các tổ chức Sẵn sàng quanh ca làm nhóm so sánh (điểm S của AI là tương đối). */
    static final double REFERENCE_RADIUS_M = 50_000;
    /** Ca đang xử lý trong bán kính này quanh ca mới được coi là "ca gần" (khớp NEARBY_CASE_KM = 5 của dịch vụ AI). */
    public static final double NEARBY_CASE_M = 5_000;
    /** Tải từ mức này trở lên là "bận" (khớp BUSY_LOAD_RATIO = 0.7 của dịch vụ AI). */
    static final double BUSY_RATIO = 0.7;
    /** Các mốc (km, điểm 0..100) của điểm vị trí L; giữa hai mốc nội suy tuyến tính (khớp DISTANCE_BANDS_KM của dịch vụ AI). */
    private static final double[][] DISTANCE_BANDS = {{0, 100}, {2, 100}, {5, 90}, {10, 75}, {20, 50}, {30, 30}, {50, 15}};
    static final int REFERENCE_MAX = 29;

    private final AiClient ai;
    private final IncidentRepository incidents;
    private final IncidentAssignmentRepository assignments;
    private final IncidentMatchingScoreRepository scores;
    private final RescueOrganizationRepository orgs;
    /** Bán kính dự phòng khi một tổ chức thiếu bán kính tự khai (cột bắt buộc từ V10): ngoài 1,5 lần bán kính thì AI loại "quá xa". */
    private final double serviceRadiusKm;
    /** Sức chứa giả định (số ca cùng lúc) gửi cho AI để tính tải; 0 = không gửi (AI cho điểm tải trung tính). */
    private final int defaultCapacity;
    /** Chỉ lần từ chối / không phản hồi trong ngần này giờ mới trừ điểm W: tổ chức từ chối vì đang bận, vài giờ sau có thể đã rảnh. */
    private final Duration refusalWindow;
    private final Clock clock;

    @Autowired // hai constructor: Spring dùng cái này; cái còn lại cho test (đồng hồ cố định)
    public MatchingService(AiClient ai, IncidentRepository incidents, IncidentAssignmentRepository assignments,
                           IncidentMatchingScoreRepository scores, RescueOrganizationRepository orgs,
                           @Value("${app.matching.refusal-window-hours:3}") long refusalWindowHours,
                           @Value("${app.ai.org-service-radius-km:35}") double serviceRadiusKm,
                           @Value("${app.ai.org-default-capacity:3}") int defaultCapacity) {
        this(ai, incidents, assignments, scores, orgs, refusalWindowHours, serviceRadiusKm, defaultCapacity, Clock.systemUTC());
    }

    MatchingService(AiClient ai, IncidentRepository incidents, IncidentAssignmentRepository assignments,
                    IncidentMatchingScoreRepository scores, RescueOrganizationRepository orgs, long refusalWindowHours,
                    double serviceRadiusKm, int defaultCapacity, Clock clock) {
        this.ai = ai; this.incidents = incidents; this.assignments = assignments; this.scores = scores; this.orgs = orgs;
        this.refusalWindow = Duration.ofHours(Math.max(1, refusalWindowHours));
        this.serviceRadiusKm = serviceRadiusKm > 0 ? serviceRadiusKm : 35;
        this.defaultCapacity = Math.max(0, defaultCapacity);
        this.clock = clock;
    }

    /** Số liệu chỉ phụ thuộc tổ chức: các ca đang nhận và số lần từ chối / không phản hồi gần đây. Chấm nhiều ca cho cùng một tổ
     *  chức (danh sách ca đề xuất) thì tính một lần rồi dùng lại, thay vì hai truy vấn cho mỗi ca. */
    public record OrgLoad(List<Incident> active, long refused) {}

    private static final EnumSet<IncidentStatus> TAKEN = EnumSet.of(IncidentStatus.ASSIGNED, IncidentStatus.ACCEPTED);
    private static final EnumSet<AssignmentStatus> REFUSED = EnumSet.of(AssignmentStatus.REJECTED, AssignmentStatus.TIMEOUT);

    @Transactional(readOnly = true)
    public OrgLoad load(RescueOrganization o) { return loads(List.of(o)).get(o.getId()); }

    /** Như load nhưng cho nhiều tổ chức: hai câu SQL cho cả danh sách (orgId → số liệu), thay vì hai câu mỗi tổ chức. */
    @Transactional(readOnly = true)
    public Map<Long, OrgLoad> loads(Collection<RescueOrganization> list) {
        if (list.isEmpty()) return Map.of();
        List<Long> ids = list.stream().map(RescueOrganization::getId).toList();
        Map<Long, List<Incident>> active = new HashMap<>();
        for (Incident i : incidents.findByAssignedOrgIdInAndStatusIn(ids, TAKEN))
            active.computeIfAbsent(i.getAssignedOrg().getId(), k -> new ArrayList<>()).add(i);
        Instant since = clock.instant().minus(refusalWindow);
        Map<Long, Long> refused = Counts.byId(assignments.countByOrgIdsAndStatusInSince(ids, REFUSED, since));
        Map<Long, OrgLoad> out = new HashMap<>();
        for (Long id : ids) out.put(id, new OrgLoad(active.getOrDefault(id, List.of()), refused.getOrDefault(id, 0L)));
        return out;
    }

    @Transactional
    public MatchingDto compute(Incident s, RescueOrganization o, boolean persist) { return compute(s, o, persist, load(o)); }

    @Transactional
    public MatchingDto compute(Incident s, RescueOrganization o, boolean persist, OrgLoad load) {
        MatchingDto m = null;
        if (ai.remoteEnabled() && hasLocation(o)) {
            // Nhóm so sánh: tổ chức Sẵn sàng quanh ca (tải của họ không ảnh hưởng điểm của o nên không cần đọc)
            List<RescueOrganization> group = new ArrayList<>(List.of(o));
            orgs.findAvailableWithin(s.getLatitude(), s.getLongitude(), REFERENCE_RADIUS_M).stream()
                    .filter(x -> !x.getId().equals(o.getId())).limit(REFERENCE_MAX).forEach(group::add);
            Map<Long, OrgLoad> loads = new HashMap<>();
            for (RescueOrganization x : group) loads.put(x.getId(), x.getId().equals(o.getId()) ? load : new OrgLoad(List.of(), 0));
            m = remote(s, group, loads).map(r -> r.get(o.getId())).orElse(null);
        }
        if (m == null) m = score(s, o, load);
        if (persist) save(s, o, m, scores.findByIncidentIdAndOrgId(s.getId(), o.getId()).orElseGet(IncidentMatchingScore::new));
        return m;
    }

    /**
     * Chấm một ca với nhiều tổ chức (danh sách đề xuất của Admin), kết quả orgId → điểm theo thứ tự list. Số liệu tải của các tổ
     * chức và điểm đã lưu của ca được đọc theo lô; persist = true thì vẫn ghi mỗi tổ chức một dòng điểm (dữ liệu thật phải lưu).
     */
    @Transactional
    public Map<Long, MatchingDto> computeAll(Incident s, List<RescueOrganization> list, boolean persist) {
        return computeAll(s, list, persist, loads(list));
    }

    /** Như trên nhưng dùng số liệu tải đã đọc sẵn (nơi gọi cần thêm số liệu này, ví dụ ca đang xử lý gần đó). */
    public Map<Long, MatchingDto> computeAll(Incident s, List<RescueOrganization> list, boolean persist, Map<Long, OrgLoad> loads) {
        Map<Long, IncidentMatchingScore> saved = new HashMap<>();
        if (persist) for (IncidentMatchingScore m : scores.findByIncidentId(s.getId())) saved.put(m.getOrg().getId(), m);
        Map<Long, MatchingDto> viaAi = remote(s, list, loads).orElse(Map.of());
        Map<Long, MatchingDto> out = new LinkedHashMap<>();
        for (RescueOrganization o : list) {
            MatchingDto m = viaAi.get(o.getId());
            if (m == null) m = score(s, o, loads.get(o.getId()));
            if (persist) save(s, o, m, saved.getOrDefault(o.getId(), new IncidentMatchingScore()));
            out.put(o.getId(), m);
        }
        return out;
    }

    // ---------------- Dịch vụ AI ----------------

    private static boolean hasLocation(RescueOrganization o) { return o.getLatitude() != null && o.getLongitude() != null; }

    /** hoat_dong | tam_dung | ngung_hoat_dong theo cách gọi của dịch vụ AI. */
    static String aiStatus(RescueOrganization o) {
        if (o.getStatus() != OrgStatus.ACTIVE || (o.getUser() != null && o.getUser().isLocked())) return "ngung_hoat_dong";
        return switch (o.getOperatingStatus()) {
            case AVAILABLE -> "hoat_dong";
            case BUSY -> "tam_dung";
            default -> "ngung_hoat_dong";
        };
    }

    /** Bán kính hoạt động tổ chức tự khai báo (km); dữ liệu cũ thiếu thì dùng giá trị chung trong cấu hình. */
    double radiusKm(RescueOrganization o) {
        return o.getServiceRadiusKm() != null && o.getServiceRadiusKm() > 0 ? o.getServiceRadiusKm() : serviceRadiusKm;
    }

    /** Số ca xử lý cùng lúc tổ chức tự khai báo; dữ liệu cũ thiếu thì dùng giá trị chung trong cấu hình (0 = chưa rõ). */
    Integer capacity(RescueOrganization o) {
        if (o.getMaxConcurrentCases() != null && o.getMaxConcurrentCases() > 0) return o.getMaxConcurrentCases();
        return defaultCapacity > 0 ? defaultCapacity : null;
    }

    /** Ca tổ chức đang xử lý trong bán kính NEARBY_CASE_M quanh ca s (không tính chính ca s), gần nhất trước. */
    public static List<Incident> nearbyActive(Incident s, OrgLoad load) {
        if (load == null) return List.of();
        return load.active().stream().filter(x -> !x.getId().equals(s.getId()))
                .filter(x -> Geo.haversine(x.getLatitude(), x.getLongitude(), s.getLatitude(), s.getLongitude()) <= NEARBY_CASE_M)
                .sorted(java.util.Comparator.comparingDouble(x -> Geo.haversine(x.getLatitude(), x.getLongitude(), s.getLatitude(), s.getLongitude())))
                .toList();
    }

    /**
     * Nhận thêm ca này thì tổ chức có rơi vào trạng thái bận không: (số ca đang xử lý + 1) / số ca xử lý cùng lúc >= 70%.
     * Khi đó các ca đang xử lý gần đó mới bị trừ điểm tải (lực lượng đang dồn tại khu vực); tổ chức còn nhiều sức thì không.
     */
    public boolean busyIfAssigned(RescueOrganization o, OrgLoad load) {
        Integer cap = capacity(o);
        int active = load == null ? 0 : load.active().size();
        return cap == null || (active + 1) / (double) cap >= BUSY_RATIO;
    }

    MatchOrg toAiOrg(RescueOrganization o, OrgLoad load) {
        List<ActiveCase> active = load == null ? List.of() : load.active().stream()
                .map(x -> new ActiveCase(x.getLatitude(), x.getLongitude())).toList();
        String address = Stream.of(o.getStreetAddress(), o.getWard(), o.getDistrict(), o.getProvince())
                .filter(x -> x != null && !x.isBlank()).collect(Collectors.joining(", "));
        String areas = o.getServiceAreas() == null || o.getServiceAreas().isBlank() ? null : "Khu vực hoạt động: " + o.getServiceAreas().trim();
        return new MatchOrg(o.getId(), o.getName(), o.getLatitude(), o.getLongitude(), join(o.getName(), o.getDescription(), areas),
                aiStatus(o), radiusKm(o), capacity(o), active,
                load == null ? 0 : (int) load.refused(), address, o.getWard(), o.getProvince());
    }

    /**
     * Chấm cả nhóm tổ chức bằng một lần gọi AI. Tổ chức chưa có tọa độ không gửi được: trả điểm 0 kèm lý do (không trộn với
     * điểm của công thức dự phòng vì hai thang khác nhau). empty = chưa cấu hình AI hoặc AI lỗi (nơi gọi dùng công thức dự phòng).
     */
    Optional<Map<Long, MatchingDto>> remote(Incident s, List<RescueOrganization> list, Map<Long, OrgLoad> loads) {
        if (!ai.remoteEnabled() || list.isEmpty()) return Optional.empty();
        List<RescueOrganization> located = list.stream().filter(MatchingService::hasLocation).toList();
        if (located.isEmpty()) return Optional.empty();
        Optional<RemoteMatch> res = ai.matchRemote(sosText(s), s.getLatitude(), s.getLongitude(), s.getId(),
                located.stream().map(o -> toAiOrg(o, loads.get(o.getId()))).toList());
        if (res.isEmpty()) return Optional.empty();
        RemoteMatch r = res.get();
        Map<Long, MatchingDto> out = new HashMap<>();
        for (RescueOrganization o : list) {
            OrgLoad load = loads.getOrDefault(o.getId(), new OrgLoad(List.of(), 0));
            int mine = (int) load.active().stream().filter(x -> !x.getId().equals(s.getId())).count();
            if (!hasLocation(o)) {
                out.put(o.getId(), new MatchingDto(0, 0, 0, 0, 0, 1e9, mine, (int) load.refused(),
                        "Chưa có tọa độ trụ sở: AI không xếp hạng được, cần cập nhật vị trí tổ chức", null, SOURCE_AI));
                continue;
            }
            double dist = Geo.haversine(o.getLatitude(), o.getLongitude(), s.getLatitude(), s.getLongitude());
            RemoteExclusion ex = r.excluded().get(o.getId());
            RemoteMatchItem it = r.items().get(o.getId());
            if (it != null && (ex == null || "qua_tai".equals(ex.kind()))) out.put(o.getId(), fromAi(it, mine, load, r));
            else out.put(o.getId(), new MatchingDto(0, 0, 0, 0, 0, dist, mine, (int) load.refused(),
                    "AI không gợi ý: " + readable(ex == null ? "không đủ điều kiện" : ex.reason()), null, SOURCE_AI));
        }
        return Optional.of(out);
    }

    private static MatchingDto fromAi(RemoteMatchItem it, int mine, OrgLoad load, RemoteMatch r) {
        List<String> parts = new ArrayList<>(it.reasons().stream().map(MatchingService::readable).toList());
        if (it.c() == null) parts.add("chưa rõ nhu cầu cụ thể của ca: điểm dựa vào vị trí và tải");
        if ("qua_tai".equals(it.workloadState())) parts.add("nguy cơ quá tải");
        if ("tfidf".equals(r.embeddingMode())) parts.add("điểm mô tả tạm tính bằng TF-IDF (chưa nạp model embedding)");
        int c = it.c() == null ? CapabilityRules.NEUTRAL : (int) Math.round(it.c());
        return new MatchingDto((int) Math.round(it.score()), (int) Math.round(it.l()), (int) Math.round(it.s()), c,
                (int) Math.round(it.w()), it.distanceKm() * 1000, mine, (int) load.refused(), String.join("; ", parts),
                it.weightsPct(), SOURCE_AI);
    }

    /** Mã trạng thái của AI sang chữ hiển thị. */
    private static String readable(String s) {
        return s == null ? "" : s.replace("Không hoạt động (tam_dung)", "tổ chức đang bận / tạm dừng nhận ca")
                .replace("Không hoạt động (ngung_hoat_dong)", "tổ chức không hoạt động hoặc đang bị khóa");
    }

    private void save(Incident s, RescueOrganization o, MatchingDto d, IncidentMatchingScore m) {
        m.setIncident(s); m.setOrg(o);
        m.setLocationScore(d.locationScore()); m.setSemanticScore(d.semanticScore());
        m.setCapabilityScore((double) d.capabilityScore()); m.setWorkloadScore(d.workloadScore());
        m.setMatchingScore(d.score()); m.setDistanceM(d.distanceM());
        scores.save(m);
    }

    /** Văn bản của ca dùng cho S và C: mô tả gốc của người dân + ghi chú Admin ghi khi xác minh ca AI xếp KHONG (nếu có). */
    static String sosText(Incident s) { return join(s.getDescription(), s.getVerificationNote()); }

    /** Văn bản của tổ chức cho S: tên, mô tả, khu vực hoạt động. */
    static String orgText(RescueOrganization o) { return join(o.getName(), o.getDescription(), o.getServiceAreas()); }

    /** Năng lực (C) chỉ lấy từ tên và mô tả tổ chức: khu vực hoạt động là địa danh, không nói tổ chức làm được gì. */
    static String orgCapabilityText(RescueOrganization o) { return join(o.getName(), o.getDescription()); }

    private static String join(String... parts) {
        return Stream.of(parts).filter(x -> x != null && !x.isBlank()).map(String::trim).collect(Collectors.joining(". "));
    }

    /** Điểm vị trí 0..100 theo khoảng cách (km): nội suy tuyến tính giữa các mốc, sau mốc cuối giảm dần tới 5. */
    static double distanceScore(double km) {
        for (int k = 1; k < DISTANCE_BANDS.length; k++) {
            double[] near = DISTANCE_BANDS[k - 1], far = DISTANCE_BANDS[k];
            if (km <= far[0]) return near[1] + (far[1] - near[1]) * Math.max(0, (km - near[0]) / (far[0] - near[0]));
        }
        double[] last = DISTANCE_BANDS[DISTANCE_BANDS.length - 1];
        return Math.max(5, last[1] - (km - last[0]) * 0.5);
    }

    /** Công thức chấm điểm (không đọc/ghi CSDL). */
    private MatchingDto score(Incident s, RescueOrganization o, OrgLoad load) {
        double dist = (o.getLatitude() == null || o.getLongitude() == null) ? 1e9
                : Geo.haversine(o.getLatitude(), o.getLongitude(), s.getLatitude(), s.getLongitude());
        int L = dist / 1000 <= radiusKm(o) ? (int) Math.round(distanceScore(dist / 1000)) : 0; // ngoài bán kính hoạt động: 0

        String text = sosText(s);
        int S = ai.semanticScore(text, orgText(o));
        CapabilityRules.Result cap = CapabilityRules.score(text, orgCapabilityText(o));
        int C = cap.score();

        List<Incident> mine = load.active().stream().filter(x -> !x.getId().equals(s.getId())).toList();
        long nearby = nearbyActive(s, load).size();
        boolean canTake = o.getStatus() == OrgStatus.ACTIVE && o.getOperatingStatus() == OperatingStatus.AVAILABLE;
        long refused = load.refused();
        Integer maxCases = capacity(o);

        // W giống dịch vụ AI: 100 x (1 - số ca / số ca xử lý cùng lúc); ca gần chỉ trừ khi nhận thêm ca này thì tổ chức bận
        double W0 = !canTake ? 0 : maxCases == null ? 50 : Math.max(0, 100.0 * (1 - mine.size() / (double) maxCases));
        if (canTake && nearby > 0 && busyIfAssigned(o, load)) W0 -= 8 * nearby;
        int W = (int) Math.round(Math.max(0, W0 - Math.min(30, 10 * refused)));

        int score = (int) Math.round(W_LOCATION * L + W_SEMANTIC * S + W_CAPABILITY * C + W_WORKLOAD * W);
        List<String> parts = new ArrayList<>();
        parts.add(dist < 5000 ? String.format("Gần (%.1f km)", dist / 1000) : String.format("Cách %.1f km", dist / 1000));
        parts.add(S >= 70 ? "mô tả tương đồng cao" : S >= 45 ? "mô tả tương đồng vừa" : "mô tả ít tương đồng");
        parts.add(cap.reason());
        parts.add(!canTake ? "chưa sẵn sàng nhận ca" : mine.isEmpty() ? "chưa nhận ca nào"
                : "đang nhận " + mine.size() + (maxCases == null ? "" : "/" + maxCases) + " ca" + (maxCases != null && mine.size() >= maxCases ? " (nguy cơ quá tải)" : ""));
        if (refused > 0) parts.add("từ chối/không phản hồi " + refused + " ca trong " + refusalWindow.toHours() + " giờ qua");
        return new MatchingDto(score, L, S, C, W, dist, mine.size(), (int) refused, String.join(", ", parts), RULE_WEIGHTS, SOURCE_RULES);
    }
}
