package vn.floodconnect.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import vn.floodconnect.ai.dto.AiDtos.*;
import vn.floodconnect.common.util.VnText;
import vn.floodconnect.incident.entity.DangerLevel;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Cầu nối tới dịch vụ AI chung floodconnect-ai (Python/FastAPI), xem floodconnect-ai/docs/TICH_HOP_BACKEND.md:
 *   POST {url}/v1/assess          đánh giá mức đầy đủ: PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm ca lân cận + rule engine
 *   POST {url}/v1/match           xếp hạng tổ chức: năng lực C + ngữ nghĩa S (BGE-M3) + khoảng cách L + tải W, luật cứng
 *   POST {url}/predict-severity   {"text": "..."}              -> {"severity":"CAO","confidence":0.91}   (chỉ nhãn PhoBERT)
 *   POST {url}/embed              {"texts": ["a","b",...]}     -> {"embeddings": [[...],[...]]}
 * app.ai.service-url để trống, dịch vụ lỗi / quá hạn chờ / chưa nạp model (503): dùng bộ quy tắc dự phòng trong Java.
 * Sau một lần lỗi, phần đó (đánh giá hoặc matching) tạm không gọi AI trong app.ai.retry-after-seconds giây, để danh sách nhiều ca
 * không phải chờ hết hạn chờ ở từng ca.
 */
@Component
@Slf4j
public class AiClient {
    @Value("${app.ai.service-url}") private String serviceUrl;
    @Value("${app.ai.timeout-ms}") private int timeoutMs;
    @Value("${app.ai.token:}") private String token;
    @Value("${app.ai.retry-after-seconds:30}") private int retryAfterSeconds;
    private RestClient client;

    /** Ngắt mạch riêng cho từng nhóm: PhoBERT chưa nạp không làm matching (BGE-M3) bị bỏ qua và ngược lại. */
    private enum Part { ASSESS, MATCH }
    private final Map<Part, Instant> downUntil = new EnumMap<>(Part.class);

    public record Classification(DangerLevel level, double confidence) {}

    /** Constructor cho Spring. */
    public AiClient() {}

    /** Cho kiểm thử: trỏ thẳng tới một máy chủ AI giả. */
    AiClient(String serviceUrl, int timeoutMs, String token, int retryAfterSeconds) {
        this.serviceUrl = serviceUrl; this.timeoutMs = timeoutMs; this.token = token; this.retryAfterSeconds = retryAfterSeconds;
    }

    private RestClient client() {
        if (client == null) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(timeoutMs);
            f.setReadTimeout(timeoutMs * 3);
            RestClient.Builder b = RestClient.builder().requestFactory(f);
            if (token != null && !token.isBlank()) b.defaultHeader("X-API-Token", token);
            client = b.build();
        }
        return client;
    }
    private boolean remote() { return serviceUrl != null && !serviceUrl.isBlank(); }
    private String url(String path) { return serviceUrl.replaceAll("/+$", "") + path; }

    /** Đã cấu hình dịch vụ AI (không xét đang lỗi hay không). */
    public boolean remoteEnabled() { return remote(); }

    private synchronized boolean up(Part p) {
        Instant t = downUntil.get(p);
        return remote() && (t == null || Instant.now().isAfter(t));
    }
    private synchronized void down(Part p, Exception e) {
        downUntil.put(p, Instant.now().plus(Duration.ofSeconds(Math.max(1, retryAfterSeconds))));
        log.warn("Dịch vụ AI ({}) lỗi, dùng quy tắc dự phòng trong {} giây tới: {}", p, retryAfterSeconds, e.getMessage());
    }

    // ---------------- 0) Đánh giá đầy đủ qua dịch vụ AI ----------------

    /**
     * Gửi ca và các ca lân cận (backend đã lọc theo bán kính, thời gian) sang AI. Thời tiết là số liệu backend đã lấy;
     * null = không có dữ liệu, AI cũng không tự lấy (fetch_weather=false) để hai nơi không dùng hai nguồn khác nhau.
     * @return empty khi chưa cấu hình, AI lỗi, chưa nạp PhoBERT (503) hoặc trả dữ liệu không hợp lệ
     */
    public Optional<RemoteAssessment> assessRemote(String text, Double lat, Double lng, Long incidentId, Long citizenId,
                                                   List<NeighborCase> neighbors, WeatherInfo w) {
        if (!up(Part.ASSESS)) return Optional.empty();
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text == null ? "" : text);
            body.put("latitude", lat); body.put("longitude", lng);
            body.put("incident_id", incidentId); body.put("citizen_id", citizenId);
            List<Map<String, Object>> ns = new ArrayList<>();
            for (NeighborCase n : neighbors == null ? List.<NeighborCase>of() : neighbors) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", n.id()); m.put("description", n.description() == null ? "" : n.description());
                m.put("citizen_id", n.citizenId());
                m.put("level", n.level() == null ? null : n.level().name());
                m.put("content_level", n.contentLevel() == null ? null : n.contentLevel().name());
                m.put("admin_confirmed", n.adminConfirmed());
                ns.add(m);
            }
            body.put("neighbors", ns);
            body.put("weather", w == null ? null : Map.of("rainMm", w.rainMm(), "forecast", Objects.toString(w.forecast(), ""),
                    "alert", Objects.toString(w.alert(), ""), "severe", w.severe(), "source", Objects.toString(w.source(), "Backend")));
            body.put("fetch_weather", false);
            JsonNode n = client().post().uri(url("/v1/assess")).body(body).retrieve().body(JsonNode.class);
            return Optional.of(parseAssessment(n));
        } catch (Exception e) {
            down(Part.ASSESS, e);
            return Optional.empty();
        }
    }

    static RemoteAssessment parseAssessment(JsonNode n) {
        DangerLevel level = DangerLevel.valueOf(n.path("level").asText());
        DangerLevel content = n.hasNonNull("content_level") ? DangerLevel.valueOf(n.path("content_level").asText()) : level;
        DangerLevel phobert = n.hasNonNull("phobert_level") ? DangerLevel.valueOf(n.path("phobert_level").asText()) : level;
        return new RemoteAssessment(level, content, phobert, n.path("confidence").asDouble(0),
                n.path("needs_verification").asBoolean(false), strings(n.path("reasons")), strings(n.path("applied_rules")),
                n.path("similar_count").asInt(0));
    }

    // ---------------- 0b) Xếp hạng tổ chức qua dịch vụ AI ----------------

    /**
     * @param orgs tổ chức ứng viên (đã có tọa độ). Điểm S của AI là TƯƠNG ĐỐI giữa các tổ chức trong cùng lần gọi
     * @return empty khi chưa cấu hình hoặc AI lỗi
     */
    public Optional<RemoteMatch> matchRemote(String text, double lat, double lng, Long incidentId, List<MatchOrg> orgs) {
        if (!up(Part.MATCH) || orgs.isEmpty()) return Optional.empty();
        try {
            List<Map<String, Object>> os = new ArrayList<>();
            for (MatchOrg o : orgs) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.id()); m.put("name", o.name()); m.put("lat", o.lat()); m.put("lng", o.lng());
                m.put("description", o.description() == null ? "" : o.description());
                m.put("status", o.status()); m.put("service_radius_km", o.serviceRadiusKm()); m.put("capacity", o.capacity());
                m.put("active_cases", o.activeCases().stream().map(c -> Map.of("lat", c.lat(), "lng", c.lng())).toList());
                m.put("so_lan_tu_choi", o.recentRefusals());
                m.put("dia_chi", o.address()); m.put("xa_phuong", o.ward()); m.put("tinh", o.province());
                os.add(m);
            }
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("text", text == null || text.isBlank() ? "(không có mô tả)" : text);
            body.put("lat", lat); body.put("lng", lng); body.put("incident_id", incidentId);
            body.put("top_k", 30); body.put("include_all", true); body.put("orgs", os);
            JsonNode n = client().post().uri(url("/v1/match")).body(body).retrieve().body(JsonNode.class);
            return Optional.of(parseMatch(n));
        } catch (Exception e) {
            down(Part.MATCH, e);
            return Optional.empty();
        }
    }

    static RemoteMatch parseMatch(JsonNode n) {
        Map<Long, RemoteMatchItem> items = new LinkedHashMap<>();
        for (JsonNode it : n.path("items")) {
            JsonNode c = it.path("components"), wt = it.path("weights");
            Map<String, Integer> pct = new LinkedHashMap<>();
            for (String k : List.of("L", "S", "C", "W")) pct.put(k, (int) Math.round(wt.path(k).asDouble(0) * 100));
            items.put(it.path("org_id").asLong(), new RemoteMatchItem(it.path("score").asDouble(0),
                    c.hasNonNull("C") ? c.path("C").asDouble() : null, c.path("S").asDouble(0), c.path("L").asDouble(0),
                    c.path("W").asDouble(0), it.path("distance_km").asDouble(0), it.path("zone").asText(""),
                    it.path("workload").path("trang_thai").asText(""), strings(it.path("reasons")), pct));
        }
        Map<Long, RemoteExclusion> excluded = new LinkedHashMap<>();
        for (JsonNode e : n.path("excluded"))
            excluded.put(e.path("org_id").asLong(), new RemoteExclusion(e.path("loai").asText(""), e.path("ly_do").asText("")));
        return new RemoteMatch(items, excluded, n.path("khuyen_nghi").path("noi_dung").asText(""),
                n.path("embedding").path("mode").asText(""), strings(n.path("notes")));
    }

    private static List<String> strings(JsonNode arr) {
        List<String> out = new ArrayList<>();
        if (arr != null && arr.isArray()) for (JsonNode x : arr) if (x.isTextual() && !x.asText().isBlank()) out.add(x.asText());
        return out;
    }

    // ---------------- 1) Phân loại mức nguy hiểm (chỉ nhãn) ----------------
    public Classification classify(String text) {
        if (up(Part.ASSESS)) {
            try {
                JsonNode n = client().post().uri(url("/predict-severity")).body(Map.of("text", text))
                        .retrieve().body(JsonNode.class);
                DangerLevel lv = DangerLevel.valueOf(n.path("severity").asText("THAP"));
                return new Classification(lv, n.path("confidence").asDouble(0.5));
            } catch (Exception e) {
                down(Part.ASSESS, e);
            }
        }
        return ruleClassify(text);
    }

    private static final List<String> FLOOD = List.of("ngập", "lũ", "nước", "mưa", "cứu", "mắc kẹt", "cô lập", "sạt", "sập",
            "thuyền", "ca-nô", "khó thở", "bị thương", "chảy máu", "sơ tán", "lương thực", "tiếp tế");
    private static final Pattern METERS = Pattern.compile("(\\d+(?:[.,]\\d+)?)\\s?m\\b");

    /** Từ khóa so khớp qua VnText: nhận cả câu gõ không dấu. */
    Classification ruleClassify(String text) {
        int score = 0;
        for (String w : List.of("mắc kẹt", "cô lập", "chảy xiết", "cuốn trôi", "sập", "mái nhà", "gác lửng")) if (VnText.contains(text, w)) score += 3;
        for (String w : List.of("người già", "cụ ", "trẻ em", "em bé", "bầu", "khó thở", "bất tỉnh", "chảy máu", "bị thương")) if (VnText.contains(text, w)) score += 2;
        for (String w : List.of("ngập", "nước dâng", "lũ")) if (VnText.contains(text, w)) score += 1;
        Matcher m = METERS.matcher(text.toLowerCase());
        if (m.find() && Double.parseDouble(m.group(1).replace(',', '.')) >= 1) score += 2;
        boolean related = VnText.containsAny(text, FLOOD);
        if (!related) return new Classification(DangerLevel.KHONG, 0.82);
        DangerLevel lv = score >= 6 ? DangerLevel.KHAN_CAP : score >= 4 ? DangerLevel.CAO : score >= 2 ? DangerLevel.TRUNG_BINH : DangerLevel.THAP;
        double conf = Math.min(0.95, 0.56 + score * 0.07
                + ((lv == DangerLevel.THAP || lv == DangerLevel.TRUNG_BINH) && text.length() >= 30 ? 0.12 : 0));
        return new Classification(lv, conf);
    }

    // ---------------- 2) Độ tương đồng ngữ nghĩa (0..100) ----------------
    public int semanticScore(String sosText, String orgText) {
        if (up(Part.MATCH)) {
            try {
                JsonNode n = client().post().uri(url("/embed")).body(Map.of("texts", List.of(sosText, orgText)))
                        .retrieve().body(JsonNode.class);
                double[] a = vec(n.path("embeddings").path(0)), b = vec(n.path("embeddings").path(1));
                return (int) Math.max(0, Math.min(100, Math.round(cosine(a, b) * 100)));
            } catch (Exception e) {
                down(Part.MATCH, e);
            }
        }
        return conceptScore(sosText, orgText);
    }

    private static double[] vec(JsonNode arr) {
        double[] v = new double[arr.size()];
        for (int i = 0; i < v.length; i++) v[i] = arr.get(i).asDouble();
        return v;
    }
    static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i]; }
        return na == 0 || nb == 0 ? 0 : dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static final List<List<String>> CONCEPTS = List.of(
            List.of("cứu hộ", "cứu nạn", "ca-nô", "cano", "xuồng", "thuyền", "áo phao", "mắc kẹt", "cô lập", "sơ tán", "sạt", "sập", "ngập"),
            List.of("y tế", "cấp cứu", "sơ cứu", "bác sĩ", "thuốc", "chạy thận", "khó thở", "bị thương", "chảy máu", "thai phụ", "bệnh"),
            List.of("lương thực", "nước uống", "suất ăn", "cơm", "tiếp tế", "nhu yếu phẩm", "thức ăn", "bánh mì", "chỗ ở"),
            List.of("vận chuyển", "phương tiện", "xe tải", "kho", "vật tư", "viễn thông", "hậu cần"));

    private static double[] conceptVec(String text) {
        double[] v = new double[CONCEPTS.size()];
        for (int i = 0; i < v.length; i++) v[i] = Math.min(3, VnText.count(text, CONCEPTS.get(i)));
        return v;
    }

    int conceptScore(String sosText, String orgText) {
        double[] a = conceptVec(sosText), b = conceptVec(orgText);
        if (Arrays.stream(a).allMatch(x -> x == 0)) return 40;   // ca thiếu nội dung: trung tính
        if (Arrays.stream(b).allMatch(x -> x == 0)) return 25;   // tổ chức thiếu mô tả
        return (int) Math.round(20 + 80 * cosine(a, b));
    }
}
