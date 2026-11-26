package vn.floodconnect.ai.service;

import org.junit.jupiter.api.Test;
import vn.floodconnect.ai.service.CapabilityRules.Capability;
import java.util.EnumSet;
import static org.junit.jupiter.api.Assertions.*;
import static vn.floodconnect.ai.service.CapabilityRules.Capability.*;

/** Điểm năng lực C: nhu cầu của ca, năng lực của tổ chức (có phủ định) và cách tính điểm. */
class CapabilityRulesTest {

    @Test void needsAreReadFromSosTextIncludingUnaccentedTyping() {
        assertEquals(EnumSet.of(CUU_HO, Y_TE), CapabilityRules.needs("Nhà ngập 1 m, có cụ già khó thở, cần xuồng"));
        assertEquals(EnumSet.of(CUU_HO, Y_TE), CapabilityRules.needs("nha ngap, co nguoi bi thuong can so cuu"));
        assertEquals(EnumSet.of(TIEP_TE), CapabilityRules.needs("Cả xóm hết đồ ăn từ hôm qua"));
        assertEquals(EnumSet.noneOf(Capability.class), CapabilityRules.needs("hi mn"));
    }

    /** Ở mô tả ca, "không có thuốc" chính là nhu cầu thuốc: không xét phủ định. */
    @Test void negationInSosTextIsStillANeed() {
        assertTrue(CapabilityRules.needs("Mẹ tôi chạy thận, không có thuốc").contains(Y_TE));
    }

    @Test void accentedWordsDoNotMatchOtherAccentedWords() {
        assertFalse(CapabilityRules.needs("Nhà thuộc phường Hòa Khánh").contains(Y_TE), "“thuộc” không phải “thuốc”");
        assertTrue(CapabilityRules.offers("Chùa Phước Lâm cứu trợ lương thực").contains(TIEP_TE), "“chùa” không phải “chưa”");
    }

    @Test void orgCapabilitiesComeFromDescriptionAndRespectNegation() {
        assertEquals(EnumSet.of(CUU_HO, Y_TE), CapabilityRules.offers("Đội có 3 ca-nô, áo phao và 2 bác sĩ đi cùng"));
        assertEquals(EnumSet.of(TIEP_TE), CapabilityRules.offers("Nấu suất ăn cho bà con. Hiện chưa có xuồng"));
        assertEquals(EnumSet.of(CUU_HO), CapabilityRules.offers("Cứu hộ bằng thuyền, không hỗ trợ y tế"));
        assertEquals(EnumSet.of(TIEP_TE, Y_TE), CapabilityRules.offers("Không chỉ cứu trợ lương thực mà còn sơ cứu"));
        // phủ định chỉ có tác dụng trong cùng câu
        assertEquals(EnumSet.of(Y_TE), CapabilityRules.offers("Không có xuồng. Có bác sĩ trực"));
    }

    @Test void scoreIsShareOfNeedsTheOrgSupports() {
        String sos = "Nhà ngập, mắc kẹt trên mái, có người bị thương";              // cứu hộ + y tế
        assertEquals(100, CapabilityRules.score(sos, "Ca-nô cứu hộ, có y tá sơ cứu").score());
        CapabilityRules.Result half = CapabilityRules.score(sos, "Đội cứu hộ bằng xuồng");
        assertEquals(50, half.score());
        assertEquals("đáp ứng 1/2 nhu cầu (cứu hộ – sơ tán), chưa thấy năng lực y tế", half.reason());
        assertEquals(0, CapabilityRules.score(sos, "Quyên góp quần áo").score());
        assertEquals(0, CapabilityRules.score(sos, null).score(), "tổ chức không có mô tả: không đáp ứng nhu cầu nào");
    }

    @Test void unknownNeedsGiveEveryOrgTheSameNeutralScore() {
        CapabilityRules.Result r = CapabilityRules.score("hi mn", "Ca-nô cứu hộ");
        assertEquals(CapabilityRules.NEUTRAL, r.score());
        assertEquals("chưa rõ nhu cầu cụ thể của ca", r.reason());
        assertEquals(CapabilityRules.NEUTRAL, CapabilityRules.score("hi mn", null).score());
    }

    /** Ghi chú xác minh của Admin bổ sung nhu cầu khi mô tả gốc quá ít (MatchingService ghép hai đoạn). */
    @Test void verificationNoteAddsNeeds() {
        assertTrue(CapabilityRules.needs("hi mn").isEmpty());
        assertEquals(EnumSet.of(CUU_HO, Y_TE), CapabilityRules.needs("hi mn. Đã gọi: nước ngập 1 m, có thai phụ, cần xuồng"));
    }
}
