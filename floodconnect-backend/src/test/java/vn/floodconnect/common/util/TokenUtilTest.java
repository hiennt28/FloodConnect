package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class TokenUtilTest {
    @Test void sha256KnownVector() {
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", TokenUtil.sha256("abc"));
    }

    @Test void randomIsUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 200; i++) {
            String t = TokenUtil.random();
            assertEquals(43, t.length());                 // 32 byte, Base64 URL không padding
            assertTrue(t.matches("[A-Za-z0-9_-]+"), t);
            assertTrue(seen.add(t), "trùng token");
        }
    }

    @Test void randomOtpIsAlwaysSixDigitsEvenWithLeadingZero() {
        for (int i = 0; i < 500; i++) {
            String code = TokenUtil.randomOtp(6);
            assertEquals(6, code.length(), code);
            assertTrue(code.matches("[0-9]{6}"), code);
        }
    }
}
