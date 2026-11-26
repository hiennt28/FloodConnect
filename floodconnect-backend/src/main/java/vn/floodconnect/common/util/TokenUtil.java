package vn.floodconnect.common.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

public final class TokenUtil {
    private TokenUtil() {}
    private static final SecureRandom RND = new SecureRandom();

    public static String random() {
        byte[] b = new byte[32];
        RND.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    /** Mã OTP ngẫu nhiên gồm {@code digits} chữ số (0 đến digits-1 có thể là số 0 ở đầu). */
    public static String randomOtp(int digits) {
        int max = (int) Math.pow(10, digits);
        return String.format("%0" + digits + "d", RND.nextInt(max));
    }

    public static String sha256(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
