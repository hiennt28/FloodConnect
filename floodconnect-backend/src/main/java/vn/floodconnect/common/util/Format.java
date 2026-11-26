package vn.floodconnect.common.util;

import java.text.NumberFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Định dạng số tiền và thời gian theo kiểu Việt Nam cho nội dung thông báo/email (không dùng cho API, chỉ cho văn bản hiển thị). */
public final class Format {
    private Format() {}
    private static final Locale VN = Locale.forLanguageTag("vi-VN");
    private static final NumberFormat MONEY = NumberFormat.getIntegerInstance(VN);
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy", VN).withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    /** 10000000 -> "10.000.000 đ" */
    public static String money(long amount) { return MONEY.format(amount) + " đ"; }

    /** -> "14:05 01/12/2026" (giờ Việt Nam) */
    public static String dateTime(Instant t) { return t == null ? "" : DATE_TIME.format(t); }
}
