package vn.floodconnect.rescue.matching;

import org.junit.jupiter.api.Test;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.rescue.matching.MatchingService.OrgLoad;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Số ca xử lý cùng lúc, bán kính hoạt động tự khai báo; điểm L nội suy; quy tắc trừ điểm ca đang xử lý gần đó. */
class OrgCapacityMatchingTest {
    private final MatchingService service = new MatchingService(mock(vn.floodconnect.ai.service.AiClient.class),
            mock(vn.floodconnect.incident.repository.IncidentRepository.class),
            mock(vn.floodconnect.rescue.repository.IncidentAssignmentRepository.class),
            mock(vn.floodconnect.rescue.repository.IncidentMatchingScoreRepository.class),
            mock(vn.floodconnect.organization.repository.RescueOrganizationRepository.class), 3, 35, 3,
            java.time.Clock.systemUTC());

    private static Incident incident(long id, double lat, double lng) {
        Incident i = new Incident(); i.setId(id); i.setLatitude(lat); i.setLongitude(lng); return i;
    }

    private static RescueOrganization org(int cases, double radiusKm) {
        RescueOrganization o = new RescueOrganization(); o.setMaxConcurrentCases(cases); o.setServiceRadiusKm(radiusKm); return o;
    }

    @Test void distanceScoreInterpolatesWithoutJumps() {
        assertEquals(100, MatchingService.distanceScore(1), 1e-9);
        assertEquals(82.5, MatchingService.distanceScore(7.5), 1e-9);           // giữa mốc 5 km (90) và 10 km (75)
        assertTrue(Math.abs(MatchingService.distanceScore(9.9) - MatchingService.distanceScore(10.1)) < 1);
        assertEquals(5, MatchingService.distanceScore(500), 1e-9);
    }

    @Test void usesDeclaredCapacityAndRadius() {
        RescueOrganization o = org(10, 15);
        assertEquals(10, service.capacity(o));
        assertEquals(15, service.radiusKm(o), 1e-9);
    }

    @Test void nearbyCasesOnlyCountWhenOrgWouldBecomeBusy() {
        Incident sos = incident(1, 16.05, 108.20);
        Incident near = incident(2, 16.06, 108.20);                              // cách ~1,1 km
        Incident far = incident(3, 16.50, 108.20);                               // cách ~50 km
        OrgLoad oneNear = new OrgLoad(List.of(near, far), 0);
        assertEquals(List.of(near), MatchingService.nearbyActive(sos, oneNear));
        assertFalse(service.busyIfAssigned(org(10, 20), oneNear));               // nhận thêm -> 3/10: còn sức
        assertTrue(service.busyIfAssigned(org(3, 20), oneNear));                 // nhận thêm -> 3/3: kín việc
    }
}
