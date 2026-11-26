package vn.floodconnect.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.ai.dto.AiDtos.*;
import vn.floodconnect.incident.entity.DangerLevel;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

/** Hợp đồng JSON với dịch vụ AI chung (máy chủ giả bằng HttpServer của JDK, không cần Python). */
class AiClientRemoteTest {
    private HttpServer server;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, String> lastBody = new ConcurrentHashMap<>();
    private final Map<String, String> lastToken = new ConcurrentHashMap<>();
    private final AtomicInteger hits = new AtomicInteger();
    private volatile int status = 200;
    private volatile String response = "{}";

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            hits.incrementAndGet();
            lastBody.put(ex.getRequestURI().getPath(), new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            String t = ex.getRequestHeaders().getFirst("X-API-Token");
            if (t != null) lastToken.put(ex.getRequestURI().getPath(), t);
            byte[] b = response.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(status, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        });
        server.start();
    }
    @AfterEach void stop() { server.stop(0); }

    private AiClient client() { return new AiClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", 2000, "bi-mat", 30); }

    @Test void assessSendsNeighborsWeatherAndParsesDecision() throws Exception {
        response = """
            {"level":"CAO","content_level":"TRUNG_BINH","phobert_level":"TRUNG_BINH","confidence":0.83,"needs_verification":false,
             "reasons":["PhoBERT dự đoán TRUNG_BINH.","Thời tiết cực đoan: +1 bậc."],"applied_rules":["THOI_TIET"],"similar_count":2}""";
        var r = client().assessRemote("nước ngập", 16.05, 108.2, 7L, 1L,
                List.of(new NeighborCase(3L, "ngập", 9L, DangerLevel.CAO, null, true)),
                new WeatherInfo(120, "Mưa lớn", null, true, "Open-Meteo")).orElseThrow();
        assertEquals(DangerLevel.CAO, r.level());
        assertEquals(DangerLevel.TRUNG_BINH, r.contentLevel());
        assertEquals(List.of("THOI_TIET"), r.appliedRules());
        assertEquals(2, r.reasons().size());
        JsonNode sent = json.readTree(lastBody.get("/v1/assess"));
        assertEquals("bi-mat", lastToken.get("/v1/assess"));
        assertFalse(sent.path("fetch_weather").asBoolean(true));    // AI dùng số liệu thời tiết của backend, không tự lấy
        assertEquals(120, sent.path("weather").path("rainMm").asInt());
        assertTrue(sent.path("weather").path("severe").asBoolean());
        JsonNode n = sent.path("neighbors").get(0);
        assertEquals(3, n.path("id").asInt());
        assertEquals("CAO", n.path("level").asText());
        assertTrue(n.path("content_level").isNull());
        assertTrue(n.path("admin_confirmed").asBoolean());
        assertEquals(9, n.path("citizen_id").asInt());
    }

    @Test void modelNotLoadedOpensCircuitSoNextCasesDoNotWait() {
        status = 503; response = "{\"detail\":\"PhoBERT chưa nạp được\"}";
        AiClient c = client();
        assertTrue(c.assessRemote("a", null, null, null, null, List.of(), null).isEmpty());
        assertTrue(c.assessRemote("b", null, null, null, null, List.of(), null).isEmpty());
        assertEquals(1, hits.get());                                 // lần 2 không gọi mạng
        // classify (cùng phần "đánh giá") cũng dùng quy tắc dự phòng ngay
        assertEquals(DangerLevel.KHONG, c.classify("cho hỏi giá vàng").level());
        assertEquals(1, hits.get());
    }

    @Test void matchSendsWorkloadAndParsesItemsAndExclusions() throws Exception {
        response = """
            {"items":[{"org_id":5,"score":81.4,"components":{"C":100.0,"S":90.0,"L":75.0,"W":40.0},
                       "weights":{"C":0.35,"S":0.2,"L":0.3,"W":0.15},"distance_km":6.2,"zone":"trong_vung",
                       "workload":{"trang_thai":"ban"},"reasons":["Cách 6.2 km","Đáp ứng: Cứu nạn nước"]},
                      {"org_id":6,"score":30,"components":{"C":null,"S":0,"L":90,"W":50},"weights":{"C":0,"S":0.1,"L":0.7,"W":0.2},
                       "distance_km":1,"zone":"trong_vung","workload":{"trang_thai":"chua_ro"},"reasons":[]}],
             "excluded":[{"org_id":8,"loai":"qua_xa","ly_do":"Quá xa (80 km, bán kính phục vụ 35 km)"}],
             "khuyen_nghi":{"noi_dung":"Giao đội #1."},"embedding":{"mode":"bge"},"notes":[]}""";
        var orgs = List.of(new MatchOrg(5L, "Đội A", 16.1, 108.2, "Cứu hộ ca nô", "hoat_dong", 35, 3,
                List.of(new ActiveCase(16.0, 108.1)), 2, "Hòa Vang, Đà Nẵng", "Hòa Vang", "Đà Nẵng"));
        RemoteMatch r = client().matchRemote("cần ca nô", 16.05, 108.2, 1L, orgs).orElseThrow();
        assertEquals(81.4, r.items().get(5L).score());
        assertEquals(35, r.items().get(5L).weightsPct().get("C"));
        assertNull(r.items().get(6L).c());
        assertEquals("qua_xa", r.excluded().get(8L).kind());
        assertEquals("bge", r.embeddingMode());
        JsonNode sent = json.readTree(lastBody.get("/v1/match"));
        assertTrue(sent.path("include_all").asBoolean());
        JsonNode o = sent.path("orgs").get(0);
        assertEquals(1, o.path("active_cases").size());
        assertEquals(2, o.path("so_lan_tu_choi").asInt());
        assertEquals(3, o.path("capacity").asInt());
        assertEquals(35.0, o.path("service_radius_km").asDouble());
    }

    @Test void notConfiguredMeansNoCalls() {
        AiClient c = new AiClient("", 2000, "", 30);
        assertFalse(c.remoteEnabled());
        assertTrue(c.assessRemote("a", null, null, null, null, List.of(), null).isEmpty());
        assertTrue(c.matchRemote("a", 0, 0, null, List.of()).isEmpty());
        assertEquals(0, hits.get());
    }
}
