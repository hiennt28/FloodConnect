package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class FormatTest {
    @Test void moneyAddsThousandsSeparatorsAndSuffix() {
        assertEquals("10.000.000 đ", Format.money(10_000_000));
        assertEquals("0 đ", Format.money(0));
        assertEquals("999 đ", Format.money(999));
        assertEquals("1.000 đ", Format.money(1000));
    }

    @Test void dateTimeFormatsInVietnamTimezone() {
        // 2026-01-01T00:00:00Z = 07:00 01/01/2026 giờ Việt Nam (UTC+7)
        assertEquals("07:00 01/01/2026", Format.dateTime(Instant.parse("2026-01-01T00:00:00Z")));
        assertEquals("", Format.dateTime(null));
    }
}
