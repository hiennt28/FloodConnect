package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import vn.floodconnect.common.exception.ApiException;

class GeoTest {
    @Test void samePointIsZero() { assertEquals(0, Geo.haversine(21.0, 105.8, 21.0, 105.8), 1e-6); }

    @Test void hanoiToHcmc() {
        double d = Geo.haversine(21.0285, 105.8542, 10.8231, 106.6297);
        assertTrue(d > 1_100_000 && d < 1_200_000, "d=" + d);
    }

    @Test void coarseRoundsToThreeDecimals() {
        assertEquals(20.694, Geo.coarse(20.69412));
        assertEquals(105.755, Geo.coarse(105.75468));
        assertEquals(-0.001, Geo.coarse(-0.0012));
        assertTrue(Geo.haversine(20.69412, 105.75468, Geo.coarse(20.69412), Geo.coarse(105.75468)) < 80); // lệch tối đa ~0,0005° mỗi trục
    }

    @Test void coordsRules() {
        assertDoesNotThrow(() -> Validators.coords(16.05, 108.2));
        assertThrows(ApiException.class, () -> Validators.coords(95.0, 108.2));   // ngoài chuẩn GPS
        assertThrows(ApiException.class, () -> Validators.coords(40.7, -74.0));   // ngoài Việt Nam
        assertThrows(ApiException.class, () -> Validators.coords(16.05, null));   // thiếu kinh độ
    }

    @Test void passwordRule() {
        assertTrue(Validators.passwordOk("abc12345"));
        assertFalse(Validators.passwordOk("abcdefgh"));
        assertFalse(Validators.passwordOk("a1"));
    }
}
