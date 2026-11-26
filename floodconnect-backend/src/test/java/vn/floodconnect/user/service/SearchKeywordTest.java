package vn.floodconnect.user.service;

import org.junit.jupiter.api.Test;
import java.text.Normalizer;
import static org.junit.jupiter.api.Assertions.*;

/** Chuẩn hóa từ khóa tìm kiếm trước khi CSDL so sánh không phân biệt dấu (UserService.searchKeyword). */
class SearchKeywordTest {
    @Test void composedAndDecomposedAccentsBecomeTheSame() {
        String decomposed = Normalizer.normalize("Yên Hòa", Normalizer.Form.NFD); // kiểu "Unicode tổ hợp" của một số bộ gõ
        assertNotEquals("Yên Hòa", decomposed);
        assertEquals("Yên Hòa", UserService.searchKeyword(decomposed));
    }

    @Test void extraSpacesAreCollapsedAndNullIsEmpty() {
        assertEquals("yen hoa", UserService.searchKeyword("   yen    hoa  "));
        assertEquals("", UserService.searchKeyword(null));
    }

    @Test void likeWildcardsAreSearchedLiterally() {
        assertEquals("100\\%", UserService.searchKeyword("100%"));
        assertEquals("a\\_b", UserService.searchKeyword("a_b"));
        assertEquals("a\\\\b", UserService.searchKeyword("a\\b"));
    }

    @Test void keywordIsCappedAt100Characters() {
        assertEquals(100, UserService.searchKeyword("a".repeat(300)).length());
    }
}
