package vn.floodconnect.campaign.payment;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Tiện ích ký HMAC cho cổng thanh toán (VNPay dùng HmacSHA512). */
final class PayUtil {
    private PayUtil() {}

    static String hmacHex(String algorithm, String key, String data) {
        try {
            Mac mac = Mac.getInstance(algorithm);
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), algorithm));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không thể tạo chữ ký " + algorithm, e);
        }
    }

    /** So sánh chữ ký không phân biệt hoa/thường, thời gian không phụ thuộc nội dung. */
    static boolean sameSignature(String a, String b) {
        if (a == null || b == null) return false;
        return MessageDigest.isEqual(a.toLowerCase().getBytes(StandardCharsets.UTF_8),
                b.toLowerCase().getBytes(StandardCharsets.UTF_8));
    }
}