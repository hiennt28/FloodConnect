package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import vn.floodconnect.common.exception.ApiException;
import static org.junit.jupiter.api.Assertions.*;

class ValidatorsTest {
    @Test void phoneAndEmail() {
        assertTrue(Validators.phoneOk("090123456"));
        assertTrue(Validators.phoneOk("09012345678"));
        assertFalse(Validators.phoneOk("0901234"));
        assertFalse(Validators.phoneOk("09012a456"));
        assertFalse(Validators.phoneOk(null));
        assertTrue(Validators.emailOk("an@floodconnect.vn"));
        assertFalse(Validators.emailOk("an@floodconnect"));
        assertFalse(Validators.emailOk(null));
    }

    @Test void orgDescriptionIsRequiredAndLimited() {
        assertEquals(Validators.ORG_DESCRIPTION_REQUIRED, Validators.orgDescriptionError(null));
        assertEquals(Validators.ORG_DESCRIPTION_REQUIRED, Validators.orgDescriptionError("  " + System.lineSeparator() + " "));
        assertNull(Validators.orgDescriptionError("Đội cứu hộ có 3 ca-nô"));
        assertNull(Validators.orgDescriptionError("a".repeat(2000)));
        assertEquals("Mô tả tối đa 2000 ký tự.", Validators.orgDescriptionError("a".repeat(2001)));
    }

    @Test void passwordNeedsLettersAndDigits() {
        assertTrue(Validators.passwordOk("matkhau1"));
        assertFalse(Validators.passwordOk("12345678"));
        assertFalse(Validators.passwordOk(null));
        assertTrue(Validators.passwordOk("Demo@123!"));
        assertTrue(Validators.passwordOk("a1" + "x".repeat(62)));   // 64 ký tự
        assertFalse(Validators.passwordOk("a1" + "x".repeat(63)));  // 65 ký tự
    }

    @Test void passwordRejectsVietnameseAccentsAndSpaces() {
        assertFalse(Validators.passwordOk("mậtkhẩu123"));
        assertFalse(Validators.passwordOk("mat khau123"));
        assertFalse(Validators.passwordCharsOk("Demo12345ạ"));
        assertFalse(Validators.passwordCharsOk("Demo 12345"));
        assertFalse(Validators.passwordCharsOk(null));
        assertTrue(Validators.passwordCharsOk("Demo12345"));
        assertTrue(Validators.passwordCharsOk("x"));   // đăng nhập chỉ xét ký tự, không xét độ dài
    }

    @Test void optionalCoordsAcceptsNoneOrBoth() {
        assertDoesNotThrow(() -> Validators.optionalCoords(null, null));
        assertDoesNotThrow(() -> Validators.optionalCoords(10.77, 106.7));
        ApiException e = assertThrows(ApiException.class, () -> Validators.optionalCoords(10.77, null));
        assertEquals("INVALID_LOCATION", e.getCode());
        assertThrows(ApiException.class, () -> Validators.coords(Double.NaN, 106.0));
        assertThrows(ApiException.class, () -> Validators.coords(21.0, 181.0));
    }

    @Test void websiteMustBeHttpOrHttps() {
        assertTrue(Validators.urlOk("https://facebook.com/nhomaoxanh"));
        assertTrue(Validators.urlOk("HTTP://example.vn"));
        assertFalse(Validators.urlOk("javascript:alert(1)")); // hiển thị thành liên kết công khai: chặn giao thức khác
        assertFalse(Validators.urlOk("facebook.com/nhomaoxanh"));
        assertFalse(Validators.urlOk("https://co khoang trang.vn"));
        assertFalse(Validators.urlOk("https://" + "a".repeat(500)));
    }
}
