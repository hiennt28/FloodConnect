package vn.floodconnect.common.logging;

import vn.floodconnect.common.util.VnText;

/**
 * Chuyển một dòng log sang ký tự ASCII trước khi in ra terminal.
 * Terminal Windows mặc định đọc theo bảng mã cũ (437/1252) nên chữ UTF-8 có dấu hiện thành "Dß╗ïch vß╗Ñ".
 * Bỏ dấu tiếng Việt và đổi vài dấu câu thường gặp sang dạng ASCII thì terminal nào cũng đọc đúng.
 * Chỉ áp cho log ra màn hình (logback-spring.xml); dữ liệu trong CSDL và phản hồi API vẫn giữ nguyên dấu.
 */
public final class ConsoleAscii {
    private ConsoleAscii() {}

    public static String of(String s) {
        if (s == null || isAscii(s)) return s;
        String t = VnText.stripAccents(s);
        StringBuilder b = new StringBuilder(t.length());
        // Duyệt theo code point: ký tự ngoài BMP (emoji) chiếm hai char nhưng chỉ thành một dấu "?"
        for (int i = 0; i < t.length(); ) {
            int c = t.codePointAt(i);
            i += Character.charCount(c);
            if (c < 128) {
                b.append((char) c);
                continue;
            }
            switch (c) {
                case '“', '”', '„', '«', '»' -> b.append('"');
                case '‘', '’' -> b.append('\'');
                case '–', '—', '−' -> b.append('-');
                case '…' -> b.append("...");
                case '•', '·' -> b.append('*');
                case '→' -> b.append("->");
                case '←' -> b.append("<-");
                case '≥' -> b.append(">=");
                case '≤' -> b.append("<=");
                case ' ' -> b.append(' ');
                default -> b.append('?');
            }
        }
        return b.toString();
    }

    private static boolean isAscii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 128) return false;
        }
        return true;
    }
}
