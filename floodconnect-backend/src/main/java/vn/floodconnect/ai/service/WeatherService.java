package vn.floodconnect.ai.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lấy lượng mưa khu vực từ Open-Meteo (miễn phí, không cần khóa).
 * Có thể thay bằng NCHMF/OpenWeatherMap: chỉ cần giữ nguyên kiểu trả về WeatherInfo.
 * Lỗi mạng: trả null (không có dữ liệu), không ước lượng. Trước đây có hàm "Dự phòng" sinh lượng mưa giả theo tọa độ,
 * đã bỏ vì làm ca bị nâng mức và hiện cảnh báo lũ không có thật (IncidentMapper ẩn số liệu "Dự phòng" cũ đã lưu).
 */
@Service
@Slf4j
public class WeatherService {
    @Value("${app.weather.enabled}") private boolean enabled;
    @Value("${app.ai.timeout-ms}") private int timeoutMs;

    private static final long TTL_MIN = 15, FAILURE_TTL_MIN = 2;
    /** info null: lần gọi gần nhất không lấy được dữ liệu. */
    private record Cached(WeatherInfo info, Instant at) {}
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();
    private RestClient client;

    private RestClient client() {
        if (client == null) {
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(timeoutMs);
            f.setReadTimeout(timeoutMs);
            client = RestClient.builder().requestFactory(f).build();
        }
        return client;
    }

    /**
     * Lượng mưa trong ngày tại tọa độ, hoặc null nếu không có dữ liệu (đã tắt, lỗi mạng, Open-Meteo chậm quá hạn chờ).
     * Không bao giờ tự tạo số liệu: thời tiết được dùng để NÂNG mức nguy hiểm của ca, số liệu bịa sẽ nâng mức sai.
     * Cache theo ô ~11 km: có dữ liệu giữ 15 phút; lỗi giữ 2 phút để khi Open-Meteo sập, các ca SOS tiếp theo không phải
     * chờ hết hạn chờ mỗi lần.
     */
    public WeatherInfo at(double lat, double lng) {
        if (!enabled) return null;
        String key = Math.round(lat * 10) + ":" + Math.round(lng * 10);
        Cached c = cache.get(key);
        if (c != null && Duration.between(c.at, Instant.now()).toMinutes() < (c.info == null ? FAILURE_TTL_MIN : TTL_MIN)) return c.info;
        WeatherInfo w = fetch(lat, lng);
        cache.put(key, new Cached(w, Instant.now()));
        return w;
    }

    private WeatherInfo fetch(double lat, double lng) {
        try {
            String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lng
                    + "&daily=precipitation_sum&timezone=auto&forecast_days=1";
            JsonNode n = client().get().uri(java.net.URI.create(url)).retrieve().body(JsonNode.class);
            double mm = n.path("daily").path("precipitation_sum").path(0).asDouble(0);
            return build((int) Math.round(mm), "Open-Meteo");
        } catch (Exception e) {
            log.warn("Không lấy được thời tiết Open-Meteo: {}", e.getMessage());
            return null;
        }
    }

    private WeatherInfo build(int rainMm, String source) {
        String alert = rainMm >= 100 ? "Cảnh báo lũ, sạt lở đất" : rainMm >= 70 ? "Cảnh báo mưa rất to" : null;
        String forecast = rainMm >= 70 ? "Mưa lớn kéo dài trong 6 giờ tới"
                : rainMm >= 40 ? "Mưa vừa đến mưa to trong vài giờ tới"
                : rainMm >= 1 ? "Mưa nhẹ, rải rác" : "Không mưa hoặc mưa không đáng kể"; // < 0,5 mm được làm tròn thành 0
        return new WeatherInfo(rainMm, forecast, alert, rainMm >= 70 || alert != null, source);
    }
}
