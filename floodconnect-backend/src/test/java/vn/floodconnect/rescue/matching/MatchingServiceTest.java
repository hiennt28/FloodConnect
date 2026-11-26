package vn.floodconnect.rescue.matching;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.floodconnect.ai.dto.AiDtos.MatchOrg;
import vn.floodconnect.ai.dto.AiDtos.RemoteExclusion;
import vn.floodconnect.ai.dto.AiDtos.RemoteMatch;
import vn.floodconnect.ai.dto.AiDtos.RemoteMatchItem;
import vn.floodconnect.ai.service.AiClient;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.dto.RescueDtos.MatchingDto;
import vn.floodconnect.rescue.entity.IncidentMatchingScore;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.rescue.repository.IncidentMatchingScoreRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Công thức AI Matching = 0,25·L + 0,30·S + 0,30·C + 0,15·W, ghi chú xác minh vào S và C, W chỉ trừ từ chối trong 3 giờ gần đây. */
class MatchingServiceTest {
    private final AiClient ai = mock(AiClient.class);
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final IncidentMatchingScoreRepository scores = mock(IncidentMatchingScoreRepository.class);
    private final Instant now = Instant.parse("2026-11-13T15:00:00Z");
    private final RescueOrganizationRepository orgRepo = mock(RescueOrganizationRepository.class);
    private final MatchingService service = new MatchingService(ai, incidents, assignments, scores,
            orgRepo, 3, 35, 3, Clock.fixed(now, ZoneOffset.UTC));

    private final Incident sos = new Incident();
    private final RescueOrganization org = new RescueOrganization();

    @BeforeEach void setUp() {
        sos.setId(1L); sos.setLatitude(16.05); sos.setLongitude(108.2);
        sos.setDescription("Nhà ngập, mắc kẹt, có người bị thương");           // nhu cầu: cứu hộ + y tế
        org.setId(7L); org.setName("Đội A"); org.setDescription("Cứu hộ bằng xuồng"); // chỉ cứu hộ
        org.setLatitude(16.05); org.setLongitude(108.2);                        // cùng vị trí: L = 100
        org.setStatus(OrgStatus.ACTIVE); org.setOperatingStatus(OperatingStatus.AVAILABLE);
        when(ai.semanticScore(anyString(), anyString())).thenReturn(60);
        when(incidents.findByAssignedOrgIdInAndStatusIn(anyCollection(), anyCollection())).thenReturn(List.of());
        when(assignments.countByOrgIdsAndStatusInSince(anyCollection(), anyCollection(), any())).thenReturn(List.of());
        when(scores.findByIncidentIdAndOrgId(1L, 7L)).thenReturn(Optional.empty());
    }

    @Test void weightsAreL25S30C30W15() {
        MatchingDto m = service.compute(sos, org, false);
        assertEquals(100, m.locationScore());
        assertEquals(60, m.semanticScore());
        assertEquals(50, m.capabilityScore());   // đáp ứng 1/2 nhu cầu
        assertEquals(100, m.workloadScore());    // rảnh, không từ chối gần đây
        assertEquals(Math.round(0.25 * 100 + 0.30 * 60 + 0.30 * 50 + 0.15 * 100), m.score()); // = 73
        assertTrue(m.reason().contains("đáp ứng 1/2 nhu cầu"), m.reason());
    }

    @Test void capabilityScoreIsSaved() {
        service.compute(sos, org, true);
        ArgumentCaptor<IncidentMatchingScore> saved = ArgumentCaptor.forClass(IncidentMatchingScore.class);
        verify(scores).save(saved.capture());
        assertEquals(50.0, saved.getValue().getCapabilityScore());
        assertEquals(73.0, saved.getValue().getMatchingScore());
    }

    /** Ca "hi mn" được Admin xác minh: ghi chú được ghép vào văn bản cho S (embedding) và C (nhu cầu). */
    @Test void verificationNoteIsUsedForSemanticAndCapability() {
        sos.setDescription("hi mn");
        assertEquals(50, service.compute(sos, org, false).capabilityScore()); // chưa rõ nhu cầu: trung tính
        sos.setVerificationNote("Đã gọi: nước ngập 1 m, cần xuồng");
        MatchingDto m = service.compute(sos, org, false);
        assertEquals(100, m.capabilityScore());                                // nhu cầu cứu hộ, tổ chức có xuồng
        verify(ai).semanticScore(eq("hi mn. Đã gọi: nước ngập 1 m, cần xuồng"), anyString());
    }

    @Test void orgTypeHintIsNoLongerAddedToOrgText() {
        org.setServiceAreas("Thành phố Đà Nẵng");
        service.compute(sos, org, false);
        verify(ai).semanticScore(anyString(), eq("Đội A. Cứu hộ bằng xuồng. Thành phố Đà Nẵng"));
    }

    @Test void onlyRefusalsInTheLastThreeHoursLowerW() {
        when(assignments.countByOrgIdsAndStatusInSince(anyCollection(), anyCollection(), eq(now.minusSeconds(3 * 3600))))
                .thenReturn(List.<Object[]>of(new Object[] { 7L, 2L }));
        MatchingDto m = service.compute(sos, org, false);
        assertEquals(80, m.workloadScore());   // 100 − 10 × 2 lần
        assertEquals(2, m.refusedCount());
        assertTrue(m.reason().contains("từ chối/không phản hồi 2 ca trong 3 giờ qua"), m.reason());
    }

    // ---------------- Qua dịch vụ AI chung (/v1/match) ----------------

    private RemoteMatch aiResult() {
        return new RemoteMatch(
                Map.of(7L, new RemoteMatchItem(84.6, 100.0, 92.0, 100.0, 50.0, 0.3, "trong_vung", "ranh",
                        List.of("Cách 0.3 km (bán kính phục vụ 35 km), ETA ~1 phút", "Đáp ứng: Cứu nạn nước / kẹt do ngập"),
                        Map.of("L", 30, "S", 20, "C", 35, "W", 15))),
                Map.of(8L, new RemoteExclusion("luat_cung", "Không hoạt động (tam_dung)")),
                "Giao đội #1.", "bge", List.of());
    }

    @Test void aiScoresAreUsedForAllCandidatesWhenAvailable() {
        when(ai.remoteEnabled()).thenReturn(true);
        when(ai.matchRemote(anyString(), anyDouble(), anyDouble(), any(), anyList())).thenReturn(Optional.of(aiResult()));
        RescueOrganization busy = new RescueOrganization();
        busy.setId(8L); busy.setName("Đội B"); busy.setDescription("Y tế"); busy.setLatitude(16.06); busy.setLongitude(108.21);
        busy.setStatus(OrgStatus.ACTIVE); busy.setOperatingStatus(OperatingStatus.BUSY);
        Map<Long, MatchingDto> all = service.computeAll(sos, List.of(org, busy), false);

        MatchingDto a = all.get(7L);
        assertEquals(MatchingService.SOURCE_AI, a.source());
        assertEquals(85, a.score());
        assertEquals(100, a.capabilityScore());
        assertEquals(92, a.semanticScore());
        assertEquals(35, a.weights().get("C"));
        assertEquals(300, a.distanceM(), 1e-6);
        assertTrue(a.reason().contains("Đáp ứng: Cứu nạn nước"), a.reason());

        MatchingDto b = all.get(8L);
        assertEquals(0, b.score());
        assertEquals("AI không gợi ý: tổ chức đang bận / tạm dừng nhận ca", b.reason());
        assertNull(b.weights());                                    // bị loại: không có công thức điểm
        verify(ai, never()).semanticScore(anyString(), anyString()); // không trộn công thức dự phòng
    }

    @Test void aiReceivesStatusWorkloadAndOrgText() {
        when(ai.remoteEnabled()).thenReturn(true);
        Incident other = new Incident(); other.setId(99L); other.setLatitude(16.051); other.setLongitude(108.201);
        RescueOrganization me = org; me.setServiceAreas("Hòa Vang");
        other.setAssignedOrg(me);
        when(incidents.findByAssignedOrgIdInAndStatusIn(anyCollection(), anyCollection())).thenReturn(List.of(other));
        when(ai.matchRemote(anyString(), anyDouble(), anyDouble(), any(), anyList())).thenReturn(Optional.of(aiResult()));
        service.computeAll(sos, List.of(me), false);
        @SuppressWarnings("unchecked") ArgumentCaptor<List<MatchOrg>> sent = ArgumentCaptor.forClass(List.class);
        verify(ai).matchRemote(eq("Nhà ngập, mắc kẹt, có người bị thương"), eq(16.05), eq(108.2), eq(1L), sent.capture());
        MatchOrg o = sent.getValue().get(0);
        assertEquals("hoat_dong", o.status());
        assertEquals("Đội A. Cứu hộ bằng xuồng. Khu vực hoạt động: Hòa Vang", o.description());
        assertEquals(1, o.activeCases().size());
        assertEquals(3, o.capacity());
        assertEquals(35.0, o.serviceRadiusKm());
    }

    @Test void singleOrgIsScoredAgainstNearbyReferenceOrgs() {
        when(ai.remoteEnabled()).thenReturn(true);
        RescueOrganization ref = new RescueOrganization();
        ref.setId(9L); ref.setName("Đội C"); ref.setDescription("Cứu hộ"); ref.setLatitude(16.07); ref.setLongitude(108.22);
        ref.setStatus(OrgStatus.ACTIVE); ref.setOperatingStatus(OperatingStatus.AVAILABLE);
        when(orgRepo.findAvailableWithin(16.05, 108.2, MatchingService.REFERENCE_RADIUS_M)).thenReturn(List.of(org, ref));
        when(ai.matchRemote(anyString(), anyDouble(), anyDouble(), any(), anyList())).thenReturn(Optional.of(aiResult()));
        MatchingDto m = service.compute(sos, org, true);
        assertEquals(85, m.score());
        @SuppressWarnings("unchecked") ArgumentCaptor<List<MatchOrg>> sent = ArgumentCaptor.forClass(List.class);
        verify(ai).matchRemote(anyString(), anyDouble(), anyDouble(), any(), sent.capture());
        assertEquals(List.of(7L, 9L), sent.getValue().stream().map(MatchOrg::id).toList()); // không gửi trùng chính tổ chức
        ArgumentCaptor<IncidentMatchingScore> saved = ArgumentCaptor.forClass(IncidentMatchingScore.class);
        verify(scores).save(saved.capture());
        assertEquals(85.0, saved.getValue().getMatchingScore());
    }

    @Test void aiDownFallsBackToJavaFormula() {
        when(ai.remoteEnabled()).thenReturn(true);
        when(ai.matchRemote(anyString(), anyDouble(), anyDouble(), any(), anyList())).thenReturn(Optional.empty());
        MatchingDto m = service.computeAll(sos, List.of(org), false).get(7L);
        assertEquals(MatchingService.SOURCE_RULES, m.source());
        assertEquals(73, m.score());
        assertEquals(25, m.weights().get("L"));
    }
}
