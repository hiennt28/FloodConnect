package vn.floodconnect.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.incident.entity.DangerLevel;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/** Thời tiết chỉ được dùng để nâng mức khi có số liệu thật; không có dữ liệu thì bỏ qua, không đoán. */
class SosAssessorTest {
    private final AiClient ai = mock(AiClient.class);
    private final SosAssessor assessor = new SosAssessor(ai);
    private static final String TEXT = "Nước ngập tới đầu gối trong nhà, cả xóm bị ngập";

    @BeforeEach void setUp() {
        when(ai.classify(anyString())).thenReturn(new AiClient.Classification(DangerLevel.TRUNG_BINH, 0.85));
    }

    @Test void heavyRainFromRealDataRaisesOneLevel() {
        var a = assessor.assess(TEXT, 0, new WeatherInfo(120, "Mưa lớn kéo dài", "Cảnh báo lũ, sạt lở đất", true, "Open-Meteo"));
        assertEquals(DangerLevel.CAO, a.finalLevel());
    }

    @Test void missingWeatherDataNeverRaisesTheLevelAndSaysSo() {
        var a = assessor.assess(TEXT, 0, null); // Open-Meteo lỗi hoặc tắt: trước đây hệ thống tự tạo lượng mưa giả
        assertEquals(DangerLevel.TRUNG_BINH, a.finalLevel());
        assertNull(a.weather());
        assertTrue(a.reasons().stream().anyMatch(r -> r.contains("Không lấy được dữ liệu thời tiết")));
    }
}
