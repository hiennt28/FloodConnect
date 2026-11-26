package vn.floodconnect.ai.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.ai.dto.AiDtos.NeighborCase;
import vn.floodconnect.ai.dto.AiDtos.RemoteAssessment;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.incident.entity.DangerLevel;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Có dịch vụ AI chung: dùng kết quả /v1/assess; AI lỗi hoặc chưa nạp model: quay về bộ quy tắc Java và nói rõ. */
class SosAssessorRemoteTest {
    private final AiClient ai = mock(AiClient.class);
    private final SosAssessor assessor = new SosAssessor(ai);
    private static final String TEXT = "Nước ngập tới mái, có trẻ em mắc kẹt";
    private static final WeatherInfo RAIN = new WeatherInfo(120, "Mưa lớn", "Cảnh báo lũ, sạt lở đất", true, "Open-Meteo");
    private final List<NeighborCase> neighbors = List.of(new NeighborCase(3L, "nước ngập", 9L, DangerLevel.CAO, DangerLevel.TRUNG_BINH, false));

    @BeforeEach void setUp() {
        when(ai.remoteEnabled()).thenReturn(true);
        when(ai.classify(anyString())).thenReturn(new AiClient.Classification(DangerLevel.TRUNG_BINH, 0.85));
    }

    private SosAssessor.Context ctx(AtomicInteger calls) {
        return new SosAssessor.Context(7L, 1L, 16.05, 108.2, () -> { calls.incrementAndGet(); return neighbors; });
    }

    @Test void usesAiServiceResultAndSendsNeighborsAndWeather() {
        when(ai.assessRemote(anyString(), any(), any(), any(), any(), anyList(), any())).thenReturn(Optional.of(new RemoteAssessment(
                DangerLevel.KHAN_CAP, DangerLevel.KHAN_CAP, DangerLevel.CAO, 0.91, false,
                List.of("PhoBERT dự đoán CAO (độ tin cậy 91%).", "Tổ hợp mắc kẹt + trẻ em → KHẨN CẤP."), List.of("TIN_HIEU_MANH"), 1)));
        AtomicInteger calls = new AtomicInteger();
        var a = assessor.assess(TEXT, 2, RAIN, ctx(calls));
        assertEquals(SosAssessor.Source.AI_SERVICE, a.source());
        assertEquals(DangerLevel.CAO, a.aiLevel());          // nhãn thô PhoBERT
        assertEquals(DangerLevel.KHAN_CAP, a.finalLevel());
        assertEquals(DangerLevel.KHAN_CAP, a.contentLevel());
        assertEquals(2, a.nearCount());                      // số ca trong 500 m vẫn do backend đếm (hiển thị cho Admin)
        assertEquals(1, calls.get());
        verify(ai).assessRemote(eq(TEXT), eq(16.05), eq(108.2), eq(7L), eq(1L), eq(neighbors), eq(RAIN));
        verify(ai, never()).classify(anyString());           // không chấm hai lần
    }

    @Test void khongFromAiGoesToAdminForVerification() {
        when(ai.assessRemote(anyString(), any(), any(), any(), any(), anyList(), any())).thenReturn(Optional.of(new RemoteAssessment(
                DangerLevel.KHONG, DangerLevel.KHONG, DangerLevel.KHONG, 0.8, false, List.of("Ngoài phạm vi"), List.of("NHAN_KHONG"), 0)));
        var a = assessor.assess("cho hỏi giá vàng hôm nay", 0, null, ctx(new AtomicInteger()));
        assertEquals(DangerLevel.KHONG, a.finalLevel());
        assertTrue(a.adminReview());
        assertTrue(a.needsVerification());
    }

    @Test void aiDownFallsBackToJavaRulesAndSaysSo() {
        when(ai.assessRemote(anyString(), any(), any(), any(), any(), anyList(), any())).thenReturn(Optional.empty());
        var a = assessor.assess(TEXT, 0, null, ctx(new AtomicInteger()));
        assertEquals(SosAssessor.Source.RULES, a.source());
        assertEquals(DangerLevel.CAO, a.finalLevel());       // quy tắc Java: nhóm nguy cơ cao tối thiểu CAO
        assertTrue(a.reasons().get(0).contains("bộ quy tắc dự phòng"), a.reasons().toString());
    }

    @Test void neighborsAreNotLoadedWithoutAiService() {
        when(ai.remoteEnabled()).thenReturn(false);
        AtomicInteger calls = new AtomicInteger();
        var a = assessor.assess(TEXT, 0, null, ctx(calls));
        assertEquals(0, calls.get());                        // không truy vấn ca lân cận thừa
        assertEquals(SosAssessor.Source.RULES, a.source());
        verify(ai, never()).assessRemote(any(), any(), any(), any(), any(), any(), any());
    }

    @Test void javaRulesRecordContentLevelBeforeContext() {
        when(ai.remoteEnabled()).thenReturn(false);
        var a = assessor.assess("Nước ngập tới đầu gối trong nhà, cả xóm bị ngập", 3, RAIN, SosAssessor.Context.none());
        assertEquals(DangerLevel.CAO, a.finalLevel());       // +1 bậc vì mưa lớn và 3 ca lân cận
        assertEquals(DangerLevel.TRUNG_BINH, a.contentLevel());
    }
}
