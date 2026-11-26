package vn.floodconnect.common.util;

import java.util.regex.Pattern;
import vn.floodconnect.common.exception.ApiException;

/** Các quy tắc kiểm tra dữ liệu dùng chung (khớp tài liệu đặc tả). */
public final class Validators {
    private Validators() {}

    public static final int PASSWORD_MAX = 64;
    public static final String PASSWORD_MSG = "Mật khẩu 8–64 ký tự, gồm cả chữ và số; chỉ dùng chữ không dấu, số và ký hiệu (không dấu cách).";
    public static final String PASSWORD_CHARS_MSG = "Mật khẩu chỉ gồm chữ không dấu, số và ký hiệu (không có dấu cách). Hãy tắt bộ gõ tiếng Việt rồi nhập lại.";
    /**
     * Chỉ ký tự ASCII in được, không dấu cách (mã 0x21–0x7E). Chặn chữ tiếng Việt có dấu vì cùng một chữ có thể
     * được mã hóa khác nhau tùy thiết bị/bộ gõ (NFC/NFD) khiến bcrypt so khớp sai, và người dùng dễ quên tắt Unikey khi ô đang ẩn.
     */
    private static final Pattern PASSWORD_CHARS = Pattern.compile("^[\\x21-\\x7E]+$");
    public static final String PHONE_MSG = "Số điện thoại chỉ gồm 9–11 chữ số.";
    private static final Pattern PHONE = Pattern.compile("^\\d{9,11}$");
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    public static final String URL_MSG = "Đường dẫn phải bắt đầu bằng http:// hoặc https:// và tối đa 500 ký tự.";
    /** Chỉ http/https: đường dẫn được hiển thị thành liên kết công khai, chặn javascript: và các giao thức khác. */
    private static final Pattern URL = Pattern.compile("^https?://\\S+$", Pattern.CASE_INSENSITIVE);

    /**
     * Độ dài tối đa của cột chuỗi mặc định (varchar 255): họ tên, email, tên tổ chức, địa chỉ, chủ tài khoản, địa chỉ ca SOS.
     * Kiểm tra trước khi lưu để người dùng nhận lỗi rõ ràng theo từng ô, thay vì CSDL từ chối và báo nhầm "Dữ liệu bị trùng" (409).
     */
    public static final int TEXT_MAX = 255;

    /** Chuỗi (đã trim) dài hơn max ký tự. null/rỗng không tính là quá dài (ô bắt buộc kiểm tra riêng). */
    public static boolean tooLong(String v, int max) { return v != null && v.trim().length() > max; }

    public static String tooLongMsg(String label, int max) { return label + " tối đa " + max + " ký tự."; }

    /** Mô tả tổ chức: bắt buộc (đăng ký, gửi lại hồ sơ, sửa thông tin) vì AI Matching suy năng lực của tổ chức từ đây (điểm C). */
    public static final int ORG_DESCRIPTION_MAX = 2000;
    public static final String ORG_DESCRIPTION_REQUIRED =
            "Vui lòng mô tả tổ chức: năng lực, phương tiện, khu vực hoạt động (AI dùng mô tả này để gợi ý ca phù hợp).";

    /** Lỗi của ô mô tả tổ chức (null nếu hợp lệ): trống hoặc dài quá ORG_DESCRIPTION_MAX ký tự sau khi trim. */
    public static String orgDescriptionError(String d) {
        if (d == null || d.isBlank()) return ORG_DESCRIPTION_REQUIRED;
        return tooLong(d, ORG_DESCRIPTION_MAX) ? tooLongMsg("Mô tả", ORG_DESCRIPTION_MAX) : null;
    }

    /** Mật khẩu chỉ gồm ký tự được phép (dùng cả khi đăng nhập, không xét độ dài/độ mạnh). */
    public static boolean passwordCharsOk(String p) { return p != null && PASSWORD_CHARS.matcher(p).matches(); }

    /** Quy tắc khi đặt mật khẩu mới (đăng ký, đặt lại, đổi mật khẩu). */
    public static boolean passwordOk(String p) {
        return passwordCharsOk(p) && p.length() >= 8 && p.length() <= PASSWORD_MAX
                && p.matches(".*[A-Za-z].*") && p.matches(".*\\d.*");
    }
    public static boolean phoneOk(String p) { return p != null && PHONE.matcher(p).matches(); }
    public static boolean emailOk(String e) { return e != null && e.length() <= TEXT_MAX && EMAIL.matcher(e).matches(); }
    public static boolean urlOk(String u) { return u != null && u.length() <= 500 && URL.matcher(u).matches(); }

    /** Quy tắc kiểm tra tọa độ: đủ cặp, số hợp lệ, chuẩn GPS và nằm trong lãnh thổ Việt Nam. */
    public static void coords(Double lat, Double lng) {
        if (lat == null || lng == null) throw ApiException.bad("INVALID_LOCATION", "Vị trí phải có đủ vĩ độ và kinh độ.");
        if (lat.isNaN() || lng.isNaN()) throw ApiException.bad("INVALID_LOCATION", "Vĩ độ và kinh độ phải là số thập phân.");
        if (lat < -90 || lat > 90) throw ApiException.bad("INVALID_LOCATION", "Vĩ độ phải nằm trong khoảng -90 đến 90.");
        if (lng < -180 || lng > 180) throw ApiException.bad("INVALID_LOCATION", "Kinh độ phải nằm trong khoảng -180 đến 180.");
        if (lat < 8.5 || lat > 23.4 || lng < 102.1 || lng > 109.5)
            throw ApiException.bad("INVALID_LOCATION", "Vị trí không được hỗ trợ (ngoài lãnh thổ Việt Nam).");
    }

    /** Vị trí tùy chọn: hoặc có cả hai hoặc không có gì. */
    public static void optionalCoords(Double lat, Double lng) {
        if (lat == null && lng == null) return;
        coords(lat, lng);
    }
}
