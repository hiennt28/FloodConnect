package vn.floodconnect.common.exception;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import vn.floodconnect.auth.dto.AuthDtos.LoginRequest;
import vn.floodconnect.incident.entity.IncidentStatus;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

/** Lỗi từ thư viện không được lộ thông điệp kỹ thuật (tên lớp Java, "JSON parse error"...) ra ngoài. */
class GlobalExceptionHandlerTest {
    private final GlobalExceptionHandler h = new GlobalExceptionHandler();
    private final ObjectMapper json = new ObjectMapper();

    /** Tạo đúng loại lỗi Spring sinh ra khi Jackson không đọc được body. */
    private HttpMessageNotReadableException unreadable(String body, Class<?> type) {
        try {
            json.readValue(body, type);
            fail("body phải lỗi");
            return null;
        } catch (Exception jackson) {
            return new HttpMessageNotReadableException("JSON parse error: " + jackson.getMessage(), jackson,
                    new MockHttpInputMessage(body.getBytes(StandardCharsets.UTF_8)));
        }
    }

    private static void noInternals(String msg) {
        for (String leak : List.of("java.", "JSON parse error", "deserialize", "vn.floodconnect", "Exception", "token"))
            assertFalse(msg.contains(leak), "thông điệp lộ chi tiết kỹ thuật (" + leak + "): " + msg);
    }

    @Test void jsonHongThiBaoCauChung() {
        ResponseEntity<GlobalExceptionHandler.ErrorBody> r = h.unreadable(unreadable("{\"identifier\":\"a\",\"password\":", LoginRequest.class));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("Dữ liệu gửi lên không đúng định dạng.", r.getBody().message());
        noInternals(r.getBody().message());
    }

    @Test void saiKieuDuLieuThiNeuTenTruong() {
        var r = h.unreadable(unreadable("{\"identifier\":[\"a\"],\"password\":\"x\"}", LoginRequest.class));
        assertEquals("Trường 'identifier' có kiểu dữ liệu không hợp lệ.", r.getBody().message());
        assertEquals(r.getBody().message(), r.getBody().errors().get("identifier"));
        noInternals(r.getBody().message());
    }

    record StatusBody(IncidentStatus status) {}

    @Test void giaTriKhongThuocDanhSachThiNeuTenTruong() {
        var r = h.unreadable(unreadable("{\"status\":\"ABC\"}", StatusBody.class));
        assertEquals("Giá trị của trường 'status' không hợp lệ.", r.getBody().message());
        noInternals(r.getBody().message());
    }

    @Test void illegalArgumentKhongLoTenLop() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> IncidentStatus.valueOf("ABC"));
        assertTrue(e.getMessage().contains("vn.floodconnect"), "tiền đề: thông điệp gốc có tên lớp");
        var r = h.illegalArgument(e);
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        noInternals(r.getBody().message());
    }

    @Test void thieuPhanMultipartLa400KhongPhai500() {
        var r = h.missingPart(new MissingServletRequestPartException("data"));
        assertEquals(HttpStatus.BAD_REQUEST, r.getStatusCode());
        assertEquals("Thiếu phần 'data' trong yêu cầu.", r.getBody().message());
    }

    @Test void saiKieuNoiDungLa415KhongPhai500() {
        var r = h.mediaType(new HttpMediaTypeNotSupportedException(MediaType.APPLICATION_JSON, List.of(MediaType.MULTIPART_FORM_DATA)));
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, r.getStatusCode());
        noInternals(r.getBody().message());
    }

    @Test void saiPhuongThucBaoTiengViet() {
        var r = h.method(new HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals("Phương thức DELETE không được hỗ trợ cho đường dẫn này.", r.getBody().message());
    }
}
