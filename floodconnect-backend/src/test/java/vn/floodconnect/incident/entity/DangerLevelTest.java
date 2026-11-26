package vn.floodconnect.incident.entity;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DangerLevelTest {
    @Test void ofWeight() {
        assertEquals(DangerLevel.CAO, DangerLevel.of(3));
        assertEquals(DangerLevel.KHONG, DangerLevel.of(99));
    }

    @Test void bumpCapsAtKhanCapAndNeverRaisesKhong() {
        assertEquals(DangerLevel.TRUNG_BINH, DangerLevel.THAP.bump(1));
        assertEquals(DangerLevel.KHAN_CAP, DangerLevel.CAO.bump(5));
        assertEquals(DangerLevel.KHONG, DangerLevel.KHONG.bump(2));
    }

    @Test void maxPicksHigher() {
        assertEquals(DangerLevel.CAO, DangerLevel.max(DangerLevel.THAP, DangerLevel.CAO));
        assertEquals(DangerLevel.CAO, DangerLevel.max(DangerLevel.CAO, DangerLevel.CAO));
    }
}
