package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VnTextTest {
    @Test void foldRemovesAccentsAndD() {
        assertEquals("duong bi ngap sau", VnText.fold("Đường bị NGẬP sâu"));
    }

    @Test void keywordMatchesWithOrWithoutAccents() {
        assertTrue(VnText.contains("Nước ngập tới ngực", "ngập"));
        assertTrue(VnText.contains("nuoc ngap toi nguc", "ngập"));
        assertTrue(VnText.contains("nuoc ngập sâu", "nước"));     // gõ lẫn có dấu và không dấu
        assertTrue(VnText.contains("can ca-no gap", "ca-nô"));
    }

    @Test void unaccentedMatchIsWholeWordOnly() {
        assertFalse(VnText.contains("toi di lam luc 8 gio", "lũ")); // "luc" không phải "lu"
        assertTrue(VnText.contains("lu ve rat nhanh", "lũ"));
    }

    @Test void ambiguousKeywordsNeedAccents() {
        assertFalse(VnText.contains("toi muon mua dien thoai", "mưa"));
        assertTrue(VnText.contains("Trời mưa to cả đêm", "mưa"));
        assertFalse(VnText.contains("nha cu bi dot", "cụ "));
    }
}
