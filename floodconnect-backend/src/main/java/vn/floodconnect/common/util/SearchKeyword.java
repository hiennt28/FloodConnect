package vn.floodconnect.common.util;

import java.text.Normalizer;

/**
 * Chuẩn hóa từ khóa tìm kiếm trước khi đưa vào câu LIKE (tìm tên người dân / tổ chức, tìm trong trang quản lý của Admin).
 * - Unicode NFC: cùng một chữ "ễ" có hai cách lưu (dựng sẵn / tổ hợp, tùy bộ gõ: Unikey "Unicode tổ hợp", bàn phím điện thoại,
 *   macOS). Dữ liệu trong CSDL lưu dạng dựng sẵn; đưa từ khóa về cùng dạng thì so khớp mới đúng.
 * - Gộp khoảng trắng thừa, cắt còn {@link #MAX} ký tự.
 * - Thoát ký tự đặc biệt của LIKE (\, %, _) để chúng được tìm như chữ thường (câu SQL dùng ESCAPE '\'), "%" không trả về tất cả.
 * Bỏ dấu và chữ hoa/thường do CSDL làm (unaccent(lower(...)), Flyway V5) ở cả hai phía.
 */
public final class SearchKeyword {
    private SearchKeyword() {}

    public static final int MAX = 100;

    public static String like(String q) {
        if (q == null) return "";
        String s = Normalizer.normalize(q, Normalizer.Form.NFC).trim().replaceAll("\\s+", " ");
        if (s.length() > MAX) s = s.substring(0, MAX);
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
