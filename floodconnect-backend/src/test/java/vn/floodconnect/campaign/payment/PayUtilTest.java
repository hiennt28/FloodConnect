package vn.floodconnect.campaign.payment;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Chữ ký HMAC của cổng thanh toán (VNPay dùng SHA512; kiểm thêm SHA256 vì hàm dùng chung). Vector theo RFC 4231 (test case 2). */
class PayUtilTest {
    private static final String KEY = "Jefe";
    private static final String DATA = "what do ya want for nothing?";

    @Test void hmacSha256MatchesRfc4231() {
        assertEquals("5bdcc146bf60754e6a042426089575c75a003f089d2739839dec58b964ec3843",
                PayUtil.hmacHex("HmacSHA256", KEY, DATA));
    }

    @Test void hmacSha512MatchesRfc4231() {
        assertEquals("164b7a7bfcf819e2e395fbe73b56e0a387bd64222e831fd610270cd7ea2505549758bf75c05a994a6d034f65f8f0e6fdcaeab1a34d4a6b4b636e070a38bce737",
                PayUtil.hmacHex("HmacSHA512", KEY, DATA));
    }

    @Test void unknownAlgorithmFails() {
        assertThrows(IllegalStateException.class, () -> PayUtil.hmacHex("HmacNOPE", KEY, DATA));
    }

    @Test void signatureComparisonIgnoresCaseAndRejectsNull() {
        assertTrue(PayUtil.sameSignature("ABCdef", "abcDEF"));
        assertFalse(PayUtil.sameSignature("abc", "abd"));
        assertFalse(PayUtil.sameSignature(null, "abc"));
        assertFalse(PayUtil.sameSignature("abc", null));
    }
}
