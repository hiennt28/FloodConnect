package vn.floodconnect.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import vn.floodconnect.ai.dto.AiDtos.NeighborCase;
import vn.floodconnect.ai.dto.AiDtos.RemoteAssessment;
import vn.floodconnect.ai.dto.AiDtos.WeatherInfo;
import vn.floodconnect.common.util.VnText;
import vn.floodconnect.incident.entity.DangerLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * AI phân loại mức độ nguy hiểm của ca SOS + bộ quy tắc hiệu chỉnh.
 * Nguyên tắc: AI chỉ hỗ trợ, con người quyết định; chỉ NÂNG mức, KHÔNG BAO GIỜ tự hạ mức.
 *
 * Có dịch vụ AI (app.ai.service-url): gọi POST /v1/assess của floodconnect-ai (PhoBERT + tín hiệu ngữ nghĩa + thời tiết + cụm
 * ca lân cận + rule engine). Chưa cấu hình, AI lỗi, chưa nạp model: dùng bộ quy tắc dự phòng bên dưới (như trước).
 */
@Service
@RequiredArgsConstructor
public class SosAssessor {
    private final AiClient ai;

    /** Nguồn kết quả đánh giá: dịch vụ AI hay bộ quy tắc dự phòng trong Java. */
    public enum Source { AI_SERVICE, RULES }

    /**
     * @param contentLevel mức chỉ theo nội dung, trước khi xét thời tiết / ca lân cận; lưu lại để các ca gửi sau tính cụm
     *                     (cụm chỉ dùng mức theo nội dung của ca lân cận, tránh các ca nâng mức qua lại cho nhau)
     */
    public record Assessment(DangerLevel aiLevel, DangerLevel finalLevel, double confidence,
                             boolean needsVerification, boolean adminReview, WeatherInfo weather,
                             int nearCount, List<String> reasons, DangerLevel contentLevel, Source source) {
        /** Dạng cũ (8 trường): mức theo nội dung = mức cuối, nguồn = quy tắc dự phòng. */
        public Assessment(DangerLevel aiLevel, DangerLevel finalLevel, double confidence, boolean needsVerification,
                          boolean adminReview, WeatherInfo weather, int nearCount, List<String> reasons) {
            this(aiLevel, finalLevel, confidence, needsVerification, adminReview, weather, nearCount, reasons, finalLevel, Source.RULES);
        }
    }

    /** Thông tin thêm cho dịch vụ AI. neighbors chỉ được tính khi thực sự gọi AI (tránh truy vấn CSDL thừa khi không có AI). */
    public record Context(Long incidentId, Long citizenId, Double latitude, Double longitude, Supplier<List<NeighborCase>> neighbors) {
        public static Context none() { return new Context(null, null, null, null, List::of); }
    }

    /** So khớp qua VnText: nhận cả câu gõ không dấu ("co tre em mac ket"). */
    private static final List<String> RISK = List.of("người già", "người cao tuổi", "cụ ", "trẻ em", "trẻ nhỏ", "em bé", "người bệnh",
            "thai phụ", "bà bầu", "mang bầu", "bị thương", "mắc kẹt", "chảy máu", "khó thở");

    /**
     * @param nearCount số ca SOS đang hoạt động khác trong bán kính 500 m (hiển thị cho Admin; quy tắc dự phòng dùng để nâng mức)
     * @param w thời tiết tại vị trí, nơi gọi lấy sẵn bằng WeatherService TRƯỚC khi mở transaction (gọi mạng có thể chậm);
     *          null = không có dữ liệu: không dùng thời tiết để nâng mức
     */
    public Assessment assess(String description, int nearCount, WeatherInfo w, Context ctx) {
        String text = description == null ? "" : description.trim();
        if (ai.remoteEnabled() && !text.isEmpty()) {
            Context c = ctx == null ? Context.none() : ctx;
            Optional<RemoteAssessment> r = ai.assessRemote(text, c.latitude(), c.longitude(), c.incidentId(), c.citizenId(),
                    c.neighbors().get(), w);
            if (r.isPresent()) return fromRemote(r.get(), nearCount, w);
            Assessment a = assess(text, nearCount, w);
            a.reasons().add(0, "Dịch vụ AI không phản hồi hoặc chưa nạp model: đánh giá bằng bộ quy tắc dự phòng.");
            return a;
        }
        return assess(text, nearCount, w);
    }

    static Assessment fromRemote(RemoteAssessment r, int nearCount, WeatherInfo w) {
        List<String> reasons = new ArrayList<>(r.reasons());
        boolean khong = r.level() == DangerLevel.KHONG;
        // KHONG: chưa gửi tổ chức, Admin xem xét và xác minh (giống quy tắc dự phòng)
        return new Assessment(r.phobertLevel(), r.level(), r.confidence(), r.needsVerification() || khong, khong, w, nearCount,
                reasons, r.contentLevel(), Source.AI_SERVICE);
    }

    /** Bộ quy tắc dự phòng trong Java (không cần dịch vụ AI). */
    public Assessment assess(String description, int nearCount, WeatherInfo w) {
        String text = description == null ? "" : description.trim();
        List<String> reasons = new ArrayList<>();
        AiClient.Classification c = ai.classify(text);
        boolean risk = VnText.containsAny(text, RISK);

        // Ngoài phạm vi lũ lụt: gửi Admin xem xét, xác minh; chưa gửi tổ chức
        if (c.level() == DangerLevel.KHONG && !risk) {
            reasons.add("AI đánh giá nội dung không thuộc phạm vi cứu hộ lũ lụt: chuyển Admin xem xét, xác minh, chưa gửi tổ chức.");
            return new Assessment(DangerLevel.KHONG, DangerLevel.KHONG, c.confidence(), true, true, w, nearCount, reasons,
                    DangerLevel.KHONG, Source.RULES);
        }

        DangerLevel sev = c.level() == DangerLevel.KHONG ? DangerLevel.TRUNG_BINH : c.level();
        // Thiếu thông tin/mơ hồ/độ tin cậy thấp: cảnh báo xác minh, KHÔNG hạ mức, KHÔNG bỏ qua ca
        boolean uncertain = c.confidence() < 0.7 || text.length() < 20;
        if (uncertain) reasons.add("Nội dung ngắn/mơ hồ hoặc độ tin cậy thấp (" + Math.round(c.confidence() * 100)
                + "%): nên xác minh bằng cách liên hệ người gửi.");
        // Nhóm nguy cơ cao: tối thiểu mức Cao
        if (risk) {
            sev = DangerLevel.max(sev, DangerLevel.CAO);
            reasons.add("Có trẻ em / người cao tuổi / người bệnh / thai phụ / người bị thương / mắc kẹt: tối thiểu mức Cao.");
        }
        DangerLevel content = DangerLevel.max(sev, c.level()); // mức theo nội dung, trước bối cảnh
        // Bối cảnh: chỉ để nâng tối đa 1 bậc; thời tiết bình thường/ít SOS không dùng để hạ mức
        List<String> ctx = new ArrayList<>();
        if (w == null) reasons.add("Không lấy được dữ liệu thời tiết: đánh giá không dùng yếu tố thời tiết.");
        else if (w.severe()) ctx.add("thời tiết bất lợi (" + w.rainMm() + " mm" + (w.alert() != null ? ", " + w.alert().toLowerCase() : "") + ")");
        if (nearCount >= 2) ctx.add(nearCount + " ca SOS khác trong bán kính 500 m");
        if (!ctx.isEmpty()) {
            sev = sev.bump(1);
            reasons.add("Nâng 1 bậc do " + String.join(", ", ctx) + ".");
        }
        if (sev.weight < c.level().weight) sev = c.level(); // không bao giờ thấp hơn kết quả AI
        return new Assessment(c.level(), sev, c.confidence(), uncertain, false, w, nearCount, reasons, content, Source.RULES);
    }
}
