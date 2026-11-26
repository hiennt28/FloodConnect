package vn.floodconnect.organization.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgUpdateRequest;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.service.RescueService;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** #15: tổ chức đã duyệt tự sửa mô tả, trang web, địa chỉ và ghim trụ sở (không có tên, loại tổ chức, giấy tờ). */
class OrgUpdateTest {
    private final CurrentUser current = mock(CurrentUser.class);
    private final OrgContext orgContext = mock(OrgContext.class);
    private final GeoService geo = mock(GeoService.class);
    private final OrgMapper orgMapper = mock(OrgMapper.class);
    private final OrgService service = new OrgService(mock(RescueOrganizationRepository.class), mock(IncidentRepository.class), current,
            orgContext, mock(OrgDocumentService.class), mock(NotificationPublisher.class), orgMapper, mock(RescueService.class), geo);

    private final User u = new User();
    private final RescueOrganization o = new RescueOrganization();
    private final WardCenter haiChau = new WardCenter("20194", "Phường Hải Châu", "48", "Thành phố Đà Nẵng", 16.06, 108.22);

    @BeforeEach void setUp() {
        o.setName("Đội A"); o.setStatus(OrgStatus.ACTIVE); o.setStreetAddress("Cũ"); o.setLatitude(16.0); o.setLongitude(108.0);
        when(current.get()).thenReturn(u);
        when(orgContext.active(u)).thenReturn(o);
        when(geo.center("20194")).thenReturn(haiChau);
    }

    private static OrgUpdateRequest req(String street, String website, double lat, double lng) {
        return new OrgUpdateRequest("Cứu hộ bằng ca-nô", website, "20194", lat, lng, street, 3, 20.0);
    }

    @Test void validUpdateMovesHqAndKeepsNameAndType() {
        service.update(req("12 Bạch Đằng", "https://doi-a.vn", 16.0612, 108.2234));
        assertEquals("Phường Hải Châu", o.getWard());
        assertEquals("Thành phố Đà Nẵng", o.getProvince());
        assertEquals("12 Bạch Đằng", o.getStreetAddress());
        assertEquals(16.0612, o.getLatitude());
        assertEquals("https://doi-a.vn", o.getWebsiteUrl());
        assertEquals("Cứu hộ bằng ca-nô", o.getDescription());
        assertEquals("Đội A", o.getName(), "tên tổ chức không đổi");
        verify(orgMapper).org(o, true);
    }

    @Test void pinOutsideChosenWardIsRejectedAndNothingChanges() {
        doThrow(ApiException.bad("LOCATION_OUTSIDE_WARD", "Ghim nằm ngoài phường/xã đã chọn.")).when(geo).requireInWard(anyDouble(), anyDouble(), eq(haiChau));
        ApiException e = assertThrows(ApiException.class, () -> service.update(req("12 Bạch Đằng", null, 16.5, 108.5)));
        assertEquals("VALIDATION_ERROR", e.getCode());
        assertTrue(e.getErrors().containsKey("location"));
        assertEquals("Cũ", o.getStreetAddress());
        assertEquals(16.0, o.getLatitude());
    }

    @Test void invalidWebsiteAndTooLongStreetAreReportedPerField() {
        ApiException e = assertThrows(ApiException.class, () -> service.update(req("a".repeat(256), "javascript:alert(1)", 16.06, 108.22)));
        assertTrue(e.getErrors().containsKey("websiteUrl"));
        assertTrue(e.getErrors().containsKey("address"));
    }

    /** Mô tả bắt buộc, không xóa trắng được: AI Matching suy năng lực của tổ chức từ mô tả (điểm C). */
    @Test void descriptionIsRequiredAndCannotBeCleared() {
        o.setDescription("Cứu hộ bằng ca-nô");
        for (String blank : new String[] { null, "", "   " }) {
            ApiException e = assertThrows(ApiException.class,
                    () -> service.update(new OrgUpdateRequest(blank, null, "20194", 16.06, 108.22, "12 Bạch Đằng", 3, 20.0)));
            assertTrue(e.getErrors().containsKey("description"));
        }
        assertEquals("Cứu hộ bằng ca-nô", o.getDescription());
        assertEquals("Cũ", o.getStreetAddress(), "lỗi thì không lưu gì");
    }

    @Test void blankWebsiteRemovesIt() {
        o.setWebsiteUrl("https://cu.vn");
        service.update(req("12 Bạch Đằng", "  ", 16.06, 108.22));
        assertNull(o.getWebsiteUrl());
    }

    @Test void lockedOrgCannotEdit() {
        when(orgContext.active(u)).thenThrow(ApiException.forbidden("ACCOUNT_LOCKED", "Tổ chức đang bị khóa"));
        ApiException e = assertThrows(ApiException.class, () -> service.update(req("12 Bạch Đằng", null, 16.06, 108.22)));
        assertEquals("ACCOUNT_LOCKED", e.getCode());
        verifyNoInteractions(geo);
    }
}
