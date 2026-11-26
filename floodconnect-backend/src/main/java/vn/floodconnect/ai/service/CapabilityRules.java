package vn.floodconnect.ai.service;

import vn.floodconnect.common.util.VnText;
import java.util.*;

/**
 * Điểm năng lực C của AI Matching: suy NHU CẦU của ca SOS (mô tả + ghi chú xác minh) và NĂNG LỰC của tổ chức (tên + mô tả tổ chức)
 * theo bốn nhóm cố định, rồi tính C = số nhu cầu tổ chức đáp ứng / số nhu cầu của ca × 100.
 *
 * Quy tắc so khớp (khác S: S đo độ gần nghĩa tổng thể của hai đoạn văn, C xét TỪNG nhu cầu có được đáp ứng không):
 *  - Từ khóa viết có dấu; câu gõ không dấu vẫn nhận ("can xuong" ~ "cần xuồng"), nhưng một từ CÓ DẤU trong văn bản chỉ khớp đúng
 *    dạng có dấu của nó, nên "thuộc" không bị hiểu là "thuốc", "chùa" không bị hiểu là "chưa".
 *  - Mô tả tổ chức có phủ định ngay trước từ khóa ("chưa có xuồng", "không hỗ trợ y tế") → KHÔNG tính là hỗ trợ.
 *    Mô tả ca thì ngược lại: "không có thuốc", "hết nước uống" chính là nhu cầu, nên không xét phủ định.
 */
public final class CapabilityRules {
    private CapabilityRules() {}

    public enum Capability {
        CUU_HO("cứu hộ – sơ tán",
                List.of("cứu hộ", "cứu nạn", "cứu người", "mắc kẹt", "bị kẹt", "cô lập", "sơ tán", "di dời", "ca-nô", "ca nô", "cano",
                        "xuồng", "thuyền", "áo phao", "nước dâng", "chảy xiết", "cuốn trôi", "mái nhà", "gác lửng", "sạt lở", "sập", "ngập"),
                List.of("cứu hộ", "cứu nạn", "cứu người", "tìm kiếm", "sơ tán", "di dời", "ca-nô", "ca nô", "cano", "xuồng", "thuyền",
                        "áo phao", "phao cứu sinh", "thợ lặn")),
        Y_TE("y tế",
                List.of("y tế", "cấp cứu", "sơ cứu", "bác sĩ", "thuốc", "chạy thận", "khó thở", "bị thương", "chảy máu", "bất tỉnh",
                        "thai phụ", "sắp sinh", "người bệnh", "bị bệnh", "ốm nặng", "gãy chân", "gãy tay"),
                List.of("y tế", "cấp cứu", "sơ cứu", "bác sĩ", "y bác sĩ", "điều dưỡng", "thuốc", "xe cứu thương", "trạm y tế")),
        TIEP_TE("tiếp tế",
                List.of("lương thực", "nước uống", "nước sạch", "suất ăn", "thức ăn", "đồ ăn", "cơm", "mì tôm", "bánh mì", "tiếp tế",
                        "nhu yếu phẩm", "hết đồ ăn", "hết nước", "chăn màn", "quần áo"),
                List.of("lương thực", "nước uống", "nước sạch", "suất ăn", "thức ăn", "đồ ăn", "cơm", "mì tôm", "bánh mì", "tiếp tế",
                        "cứu trợ", "nhu yếu phẩm", "nấu ăn", "bếp ăn", "chăn màn", "quần áo")),
        VAN_CHUYEN("vận chuyển – hậu cần",
                List.of("vận chuyển", "phương tiện", "xe tải", "đưa đi viện", "chuyển viện", "chuyển đồ", "mất liên lạc", "mất sóng"),
                List.of("vận chuyển", "phương tiện", "xe tải", "xe bán tải", "kho", "vật tư", "hậu cần", "viễn thông", "máy phát điện"));

        public final String label;
        final List<String> needWords, offerWords;

        Capability(String label, List<String> needWords, List<String> offerWords) {
            this.label = label; this.needWords = needWords; this.offerWords = offerWords;
        }

        /** Mọi từ khóa của nhóm (nhu cầu + năng lực), dùng cho độ tương đồng dự phòng của S. */
        public List<String> allWords() {
            LinkedHashSet<String> s = new LinkedHashSet<>(needWords); s.addAll(offerWords);
            return List.copyOf(s);
        }
    }

    /** Nhu cầu của ca: nhóm có ít nhất một từ khóa nhu cầu xuất hiện (không xét phủ định). */
    public static EnumSet<Capability> needs(String sosText) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        List<List<Token>> clauses = clauses(sosText);
        for (Capability c : Capability.values())
            if (c.needWords.stream().anyMatch(k -> occurs(clauses, k, false))) out.add(c);
        return out;
    }

    /** Năng lực tổ chức: nhóm có ít nhất một từ khóa năng lực xuất hiện mà không bị phủ định ngay trước. */
    public static EnumSet<Capability> offers(String orgText) {
        EnumSet<Capability> out = EnumSet.noneOf(Capability.class);
        List<List<Token>> clauses = clauses(orgText);
        for (Capability c : Capability.values())
            if (c.offerWords.stream().anyMatch(k -> occurs(clauses, k, true))) out.add(c);
        return out;
    }

    /** Ca không nhận ra nhu cầu nào: mọi tổ chức cùng điểm này (không thưởng, không phạt, thứ hạng không đổi). */
    public static final int NEUTRAL = 50;

    public record Result(int score, EnumSet<Capability> needs, EnumSet<Capability> met, EnumSet<Capability> missing) {
        /** Câu giải thích ngắn cho Admin / tổ chức. */
        public String reason() {
            if (needs.isEmpty()) return "chưa rõ nhu cầu cụ thể của ca";
            StringBuilder b = new StringBuilder("đáp ứng " + met.size() + "/" + needs.size() + " nhu cầu");
            if (!met.isEmpty()) b.append(" (").append(labels(met)).append(")");
            if (!missing.isEmpty()) b.append(", chưa thấy năng lực ").append(labels(missing));
            return b.toString();
        }
        private static String labels(EnumSet<Capability> s) { return String.join(", ", s.stream().map(c -> c.label).toList()); }
    }

    public static Result score(String sosText, String orgText) {
        EnumSet<Capability> need = needs(sosText), offer = offers(orgText);
        EnumSet<Capability> met = EnumSet.copyOf(need), missing = EnumSet.copyOf(need);
        if (!need.isEmpty()) { met.retainAll(offer); missing.removeAll(offer); }
        int score = need.isEmpty() ? NEUTRAL : (int) Math.round(100.0 * met.size() / need.size());
        return new Result(score, need, met, missing);
    }

    // ---------------- So khớp theo từng từ ----------------

    /** Một từ của văn bản: dạng viết thường giữ dấu và dạng đã bỏ dấu. */
    private record Token(String raw, String folded) {
        boolean accented() { return !raw.equals(folded); }
    }

    private static final Set<String> NEGATIONS = Set.of("không", "chưa", "chẳng", "chả", "ko", "kg");
    private static final Set<String> NEGATIONS_FOLDED = Set.of("khong", "chua", "chang");
    /** "không chỉ…", "không những…" là khẳng định mở rộng, không phải phủ định. */
    private static final Set<String> NOT_NEGATING_NEXT = Set.of("chỉ", "chi", "những", "nhung", "riêng", "rieng");
    private static final int NEGATION_WINDOW = 4;

    /** Tách câu (dấu chấm, chấm phẩy, xuống dòng...) rồi tách từ: phủ định chỉ có tác dụng trong cùng một câu. */
    private static List<List<Token>> clauses(String text) {
        List<List<Token>> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;
        for (String clause : text.toLowerCase(Locale.ROOT).split("[.;!?\\n]+")) {
            List<Token> t = tokens(clause);
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    private static List<Token> tokens(String s) {
        List<Token> out = new ArrayList<>();
        for (String w : s.split("[^\\p{L}\\p{N}]+"))
            if (!w.isEmpty()) out.add(new Token(w, VnText.fold(w)));
        return out;
    }

    private static boolean occurs(List<List<Token>> clauses, String keyword, boolean checkNegation) {
        List<Token> k = tokens(keyword.toLowerCase(Locale.ROOT));
        for (List<Token> t : clauses)
            for (int i = 0; i + k.size() <= t.size(); i++)
                if (matchesAt(t, i, k) && !(checkNegation && negatedBefore(t, i))) return true;
        return false;
    }

    /** Từ có dấu phải trùng đúng dạng có dấu; từ gõ không dấu thì so với từ khóa đã bỏ dấu. */
    private static boolean matchesAt(List<Token> t, int start, List<Token> k) {
        for (int j = 0; j < k.size(); j++) {
            Token x = t.get(start + j), y = k.get(j);
            boolean ok = x.accented() ? x.raw().equals(y.raw()) : x.folded().equals(y.folded());
            if (!ok) return false;
        }
        return true;
    }

    private static boolean negatedBefore(List<Token> t, int start) {
        for (int i = Math.max(0, start - NEGATION_WINDOW); i < start; i++) {
            Token x = t.get(i);
            boolean neg = x.accented() ? NEGATIONS.contains(x.raw()) : NEGATIONS.contains(x.raw()) || NEGATIONS_FOLDED.contains(x.folded());
            if (!neg) continue;
            Token next = i + 1 < t.size() ? t.get(i + 1) : null;
            if (next != null && NOT_NEGATING_NEXT.contains(next.raw())) continue;
            return true;
        }
        return false;
    }
}
