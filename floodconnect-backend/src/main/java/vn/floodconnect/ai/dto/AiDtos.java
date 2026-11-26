package vn.floodconnect.ai.dto;

import vn.floodconnect.incident.entity.DangerLevel;
import java.util.List;
import java.util.Map;

public final class AiDtos {
    private AiDtos() {}

    public record WeatherInfo(int rainMm, String forecast, String alert, boolean severe, String source) {}

    // ---------------- Dịch vụ AI chung (floodconnect-ai): POST /v1/assess ----------------

    /**
     * Một ca lân cận gửi kèm cho AI để xét cụm. level: mức đang hiệu lực; contentLevel: mức theo nội dung ở lần đánh giá gần nhất
     * (null với ca cũ / ca chấm bằng quy tắc dự phòng trước đây); adminConfirmed: Admin đã điều chỉnh mức ca này (mức Admin là chuẩn).
     */
    public record NeighborCase(Long id, String description, Long citizenId, DangerLevel level, DangerLevel contentLevel,
                               boolean adminConfirmed) {}

    /** Kết quả đánh giá của dịch vụ AI (PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm + rule engine). */
    public record RemoteAssessment(DangerLevel level, DangerLevel contentLevel, DangerLevel phobertLevel, double confidence,
                                   boolean needsVerification, List<String> reasons, List<String> appliedRules, int similarCount) {}

    // ---------------- POST /v1/match ----------------

    /** Một ca tổ chức đang nhận: vị trí để AI tính tải và "ca gần". */
    public record ActiveCase(double lat, double lng) {}

    /** Tổ chức ứng viên gửi sang AI. status: hoat_dong | tam_dung | ngung_hoat_dong. */
    public record MatchOrg(Long id, String name, double lat, double lng, String description, String status,
                           double serviceRadiusKm, Integer capacity, List<ActiveCase> activeCases, int recentRefusals,
                           String address, String ward, String province) {}

    /** Điểm AI cho một tổ chức (thang 0..100). c = null: ca chưa rõ nhu cầu (trọng số năng lực = 0). */
    public record RemoteMatchItem(double score, Double c, double s, double l, double w, double distanceKm, String zone,
                                  String workloadState, List<String> reasons, Map<String, Integer> weightsPct) {}

    /** Tổ chức AI loại khỏi gợi ý (luật cứng, quá xa, tuyên bố không hỗ trợ nhu cầu chính, quá tải). */
    public record RemoteExclusion(String kind, String reason) {}

    public record RemoteMatch(Map<Long, RemoteMatchItem> items, Map<Long, RemoteExclusion> excluded, String advice,
                              String embeddingMode, List<String> notes) {}
}
