package vn.floodconnect.common.util;

import java.text.Normalizer;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

/**
 * So khớp từ khóa tiếng Việt chấp nhận cả câu gõ không dấu ("nuoc ngap sau 6m"): người dân hay gõ vội, không dấu khi khẩn cấp.
 * Từ khóa viết có dấu. Một từ khóa khớp khi văn bản chứa nguyên dạng có dấu (như trước đây), hoặc khi bỏ dấu cả hai bên
 * thì văn bản chứa nguyên từ đó: so nguyên từ để "lũ" (lu) không khớp nhầm "lúc" (luc).
 */
public final class VnText {
    private VnText() {}

    /**
     * Từ khóa mà khi bỏ dấu trùng với từ thông dụng khác (mưa/mua, sập/sắp, sạt/sát, bầu/bàu, cụ/cũ, mái nhà/mai nhá,
     * kho/khó, xuồng/xuống): chỉ khớp dạng có dấu.
     */
    private static final Set<String> AMBIGUOUS = Set.of("mua", "sap", "sat", "bau", "cu", "mai nha", "kho", "xuong");

    /** Viết thường, bỏ dấu (đ → d). */
    public static String fold(String s) {
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd');
    }

    /** Bỏ dấu nhưng giữ chữ hoa/thường (Đ → D, đ → d). */
    public static String stripAccents(String s) {
        String n = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "");
        return n.replace('đ', 'd').replace('Đ', 'D');
    }

    /** Bỏ dấu, mọi ký tự không phải chữ/số thành một khoảng trắng, thêm khoảng trắng hai đầu để so nguyên từ. */
    private static String words(String s) {
        return " " + fold(s).replaceAll("[^a-z0-9]+", " ").trim() + " ";
    }

    public static boolean contains(String text, String keyword) {
        if (text == null || text.isEmpty()) return false;
        if (text.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT))) return true;
        String k = words(keyword);
        return !AMBIGUOUS.contains(k.trim()) && words(text).contains(k);
    }

    public static boolean containsAny(String text, Collection<String> keywords) {
        return keywords.stream().anyMatch(k -> contains(text, k));
    }

    public static long count(String text, Collection<String> keywords) {
        return keywords.stream().filter(k -> contains(text, k)).count();
    }
}
