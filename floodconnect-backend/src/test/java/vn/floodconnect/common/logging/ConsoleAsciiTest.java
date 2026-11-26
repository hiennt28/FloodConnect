package vn.floodconnect.common.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;

class ConsoleAsciiTest {

    @Test
    void boDauTiengVietGiuChuHoaThuong() {
        assertEquals("Dich vu AI (MATCH) loi, dung quy tac du phong trong 30 giay toi",
                ConsoleAscii.of("Dịch vụ AI (MATCH) lỗi, dùng quy tắc dự phòng trong 30 giây tới"));
        assertEquals("Da gui OTP toi Dang Van Duc", ConsoleAscii.of("Đã gửi OTP tới Đặng Văn Đức"));
    }

    @Test
    void doiDauCauDacBietSangAscii() {
        assertEquals("\"Ca SOS\" - xong... -> 112", ConsoleAscii.of("“Ca SOS” – xong… → 112"));
    }

    @Test
    void kyTuKhongCoCachDoiThanhDauHoi() {
        assertEquals("Lu ?", ConsoleAscii.of("Lũ 🌊"));
    }

    @Test
    void chuoiAsciiGiuNguyen() {
        String s = "GET /api/sos 200";
        assertSame(s, ConsoleAscii.of(s));
        assertNull(ConsoleAscii.of(null));
    }
}
