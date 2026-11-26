package vn.floodconnect.ai.service;

import org.junit.jupiter.api.Test;
import vn.floodconnect.incident.entity.DangerLevel;
import static org.junit.jupiter.api.Assertions.*;

class AiClientRuleTest {
    private final AiClient ai = new AiClient();

    @Test void severeCase() {
        var c = ai.ruleClassify("Nước ngập 1.5m, nhà có người già và trẻ em mắc kẹt trên mái");
        assertEquals(DangerLevel.KHAN_CAP, c.level());
    }

    @Test void unrelatedIsKhong() {
        assertEquals(DangerLevel.KHONG, ai.ruleClassify("Chào bạn hôm nay trời đẹp").level());
    }

    @Test void unaccentedTextIsUnderstood() {
        assertEquals(DangerLevel.TRUNG_BINH, ai.ruleClassify("nuoc ngap sau 6m").level()); // ngập +1, ≥ 1 m +2
        assertEquals(DangerLevel.KHAN_CAP, ai.ruleClassify("Nuoc ngap 1.5m, nha co nguoi gia va tre em mac ket tren mai").level());
    }

    @Test void foldingDoesNotMakeUnrelatedTextLookLikeAFlood() {
        assertEquals(DangerLevel.KHONG, ai.ruleClassify("Tôi muốn mua điện thoại").level()); // mua ≠ mưa
        assertEquals(DangerLevel.KHONG, ai.ruleClassify("toi sap di lam luc 8 gio").level()); // sắp ≠ sập, lúc ≠ lũ
    }

    @Test void bumpNeverLowersAndKhongStays() {
        assertEquals(DangerLevel.CAO, DangerLevel.TRUNG_BINH.bump(1));
        assertEquals(DangerLevel.KHAN_CAP, DangerLevel.KHAN_CAP.bump(1));
        assertEquals(DangerLevel.KHONG, DangerLevel.KHONG.bump(1));
    }
}
