package vn.floodconnect.geo.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.dto.GeoDtos.WardDetail;
import vn.floodconnect.geo.dto.GeoDtos.WardRef;
import vn.floodconnect.geo.repository.AdminWardRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Tra cứu phường/xã: kiểm tra đầu vào, điểm không thuộc xã nào, bảng địa giới chưa nạp. */
class GeoServiceTest {
    private final AdminWardRepository repo = mock(AdminWardRepository.class);
    private final GeoService geo = new GeoService(repo);
    private static final WardRef BA_DINH = new WardRef("00004", "Phường Ba Đình", "01", "Thành phố Hà Nội");

    @Test void locateReturnsWard() {
        when(repo.findAt(21.0368, 105.8346)).thenReturn(Optional.of(new LocateResult(BA_DINH, false)));
        LocateResult r = geo.locate(21.0368, 105.8346);
        assertEquals("00004", r.ward().code());
        assertFalse(r.approximate());
    }

    @Test void locateOutsideAnyWardReturnsNullWard() {
        when(repo.findAt(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        LocateResult r = geo.locate(16.5, 109.4); // ngoài biển nhưng vẫn trong khung tọa độ Việt Nam
        assertNull(r.ward());
        assertFalse(r.approximate());
    }

    @Test void locateRejectsBadCoordinatesWithoutQuerying() {
        assertEquals("INVALID_LOCATION", assertThrows(ApiException.class, () -> geo.locate(null, 105.8)).getCode());
        assertEquals("INVALID_LOCATION", assertThrows(ApiException.class, () -> geo.locate(40.7, -74.0)).getCode());
        verifyNoInteractions(repo);
    }

    @Test void wardRejectsMalformedCode() {
        for (String bad : new String[]{null, "", "123", "0000a", "000040", "1; drop table x"})
            assertEquals("INVALID_WARD_CODE", assertThrows(ApiException.class, () -> geo.ward(bad)).getCode(), "mã: " + bad);
        verifyNoInteractions(repo);
    }

    @Test void wardNotFound() {
        when(repo.findDetail("99999")).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> geo.ward("99999")).getStatus());
    }

    @Test void centerReturnsWardNamesAndPoint() {
        when(repo.findCenter("00004")).thenReturn(Optional.of(new WardCenter("00004", "Phường Ba Đình", "01", "Thành phố Hà Nội", 21.04, 105.84)));
        WardCenter c = geo.center("00004");
        assertEquals("Thành phố Hà Nội", c.provinceName());
        assertEquals(21.04, c.lat());
    }

    /** Mã xã đến từ ô chọn ở giao diện: mã sai/không có là lỗi đầu vào 400, không phải 404 */
    @Test void centerRejectsUnknownOrMalformedCode() {
        when(repo.findCenter("99999")).thenReturn(Optional.empty());
        ApiException e = assertThrows(ApiException.class, () -> geo.center("99999"));
        assertEquals(HttpStatus.BAD_REQUEST, e.getStatus());
        assertEquals("INVALID_WARD", e.getCode());
        assertEquals("INVALID_WARD_CODE", assertThrows(ApiException.class, () -> geo.center("abc")).getCode());
    }

    private static final WardCenter BA_DINH_CENTER = new WardCenter("00004", "Phường Ba Đình", "01", "Thành phố Hà Nội", 21.04, 105.84);

    @Test void pinInsideChosenWardPasses() {
        when(repo.findAt(21.0368, 105.8346)).thenReturn(Optional.of(new LocateResult(BA_DINH, false)));
        assertDoesNotThrow(() -> geo.requireInWard(21.0368, 105.8346, BA_DINH_CENTER));
    }

    /** Đổi xã mà quên dời ghim: báo rõ ghim đang ở xã nào */
    @Test void pinInAnotherWardIsRejected() {
        when(repo.findAt(21.03, 105.85)).thenReturn(Optional.of(new LocateResult(new WardRef("00070", "Phường Hoàn Kiếm", "01", "Thành phố Hà Nội"), false)));
        ApiException e = assertThrows(ApiException.class, () -> geo.requireInWard(21.03, 105.85, BA_DINH_CENTER));
        assertEquals("LOCATION_OUTSIDE_WARD", e.getCode());
        assertTrue(e.getMessage().contains("Phường Hoàn Kiếm"));
    }

    @Test void pinOutsideAnyWardOrInvalidIsRejected() {
        when(repo.findAt(anyDouble(), anyDouble())).thenReturn(Optional.empty());
        assertEquals("LOCATION_OUTSIDE_WARD", assertThrows(ApiException.class, () -> geo.requireInWard(16.5, 109.4, BA_DINH_CENTER)).getCode());
        assertEquals("INVALID_LOCATION", assertThrows(ApiException.class, () -> geo.requireInWard(null, 105.8, BA_DINH_CENTER)).getCode());
    }

    @Test void missingGeoTableGivesServiceUnavailable() {
        when(repo.findAt(anyDouble(), anyDouble())).thenThrow(new DataAccessResourceFailureException("relation \"admin_wards\" does not exist"));
        when(repo.findDetail(anyString())).thenThrow(new DataAccessResourceFailureException("relation \"admin_wards\" does not exist"));
        when(repo.findCenter(anyString())).thenThrow(new DataAccessResourceFailureException("relation \"admin_wards\" does not exist"));
        ApiException a = assertThrows(ApiException.class, () -> geo.locate(21.0, 105.8));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, a.getStatus());
        assertEquals("GEO_UNAVAILABLE", a.getCode());
        assertEquals("GEO_UNAVAILABLE", assertThrows(ApiException.class, () -> geo.ward("00004")).getCode());
        assertEquals("GEO_UNAVAILABLE", assertThrows(ApiException.class, () -> geo.center("00004")).getCode());
    }

    /** Đường viền trả nguyên chuỗi GeoJSON, không bị bọc thành chuỗi trong chuỗi */
    @Test void outlineSerializesAsRawJson() throws Exception {
        WardDetail d = new WardDetail("00004", "Phường Ba Đình", "01", "Thành phố Hà Nội", 2.97, 21.04, 105.84,
                "{\"type\":\"MultiPolygon\",\"coordinates\":[]}");
        JsonNode json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsString(d));
        assertTrue(json.get("outline").isObject());
        assertEquals("MultiPolygon", json.get("outline").get("type").asText());
        assertEquals(21.04, json.get("centerLat").asDouble());
    }
}
