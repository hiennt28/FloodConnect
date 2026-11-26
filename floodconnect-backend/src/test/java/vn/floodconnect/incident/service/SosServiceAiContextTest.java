package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import vn.floodconnect.ai.dto.AiDtos.NeighborCase;
import vn.floodconnect.ai.service.SosAssessor;
import vn.floodconnect.ai.service.WeatherService;
import vn.floodconnect.incident.dto.IncidentDtos.SosCreateRequest;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.incident.repository.IncidentDangerAdjustmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Ca lân cận gửi cho dịch vụ AI để xét cụm, và mức theo nội dung được lưu lại cho các ca gửi sau. */
class SosServiceAiContextTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAiAssessmentRepository assessments = mock(IncidentAiAssessmentRepository.class);
    private final IncidentDangerAdjustmentRepository adjustments = mock(IncidentDangerAdjustmentRepository.class);
    private final SosAssessor assessor = mock(SosAssessor.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final SosService sos = new SosService(incidents, assessments, adjustments, mock(RescueOrganizationRepository.class),
            assessor, mock(NotificationPublisher.class), current, new ObjectMapper(), mock(IncidentMapper.class),
            mock(IncidentAssignmentRepository.class), mock(WeatherService.class), mock(PlatformTransactionManager.class));
    private final User citizen = new User();

    private static Incident neighbor(long id, long citizenId, DangerLevel level) {
        User u = new User(); u.setId(citizenId);
        Incident i = new Incident();
        i.setId(id); i.setCitizen(u); i.setDescription("Nước ngập, hết lương thực"); i.setLatitude(16.0505); i.setLongitude(108.2005);
        i.setDangerLevel(level); i.setAiDangerLevel(level); i.setStatus(IncidentStatus.PENDING); i.setCreatedAt(Instant.now());
        return i;
    }

    private static IncidentAiAssessment assessment(Incident i, DangerLevel content) {
        IncidentAiAssessment a = new IncidentAiAssessment();
        a.setIncident(i); a.setPredictedLevel(i.getDangerLevel()); a.setContentLevel(content);
        return a;
    }

    @BeforeEach void setUp() {
        citizen.setId(1L); citizen.setRole(Role.CITIZEN);
        when(current.get()).thenReturn(citizen);
        when(incidents.saveAndFlush(any(Incident.class))).thenAnswer(inv -> { Incident i = inv.getArgument(0); i.setId(7L); return i; });
        when(assessor.assess(anyString(), anyInt(), any(), any())).thenReturn(new SosAssessor.Assessment(DangerLevel.CAO,
                DangerLevel.KHAN_CAP, 0.9, false, false, null, 0, List.of("x"), DangerLevel.CAO, SosAssessor.Source.AI_SERVICE));
    }

    @Test void neighborsCarryContentLevelAndAdminFlagAndContentLevelIsSaved() {
        Incident a = neighbor(3L, 20L, DangerLevel.CAO), b = neighbor(4L, 21L, DangerLevel.TRUNG_BINH);
        // 500 m (đếm ca lân cận + cảnh báo trùng) trả rỗng; 1000 m (cụm cho AI) trả hai ca
        when(incidents.findWithinRadius(any(), any(), anyDouble(), anyDouble(), eq(1000.0))).thenReturn(List.of(a, b));
        when(assessments.findByIncidentIdsLatestFirst(anyCollection()))
                .thenReturn(List.of(assessment(a, DangerLevel.TRUNG_BINH), assessment(a, DangerLevel.KHAN_CAP), assessment(b, null)));
        when(adjustments.findAdjustedIncidentIds(anyCollection())).thenReturn(List.of(4L));

        sos.create(new SosCreateRequest("Nước ngập tới ngực, có trẻ nhỏ", 16.05, 108.2, null, true));

        ArgumentCaptor<SosAssessor.Context> ctx = ArgumentCaptor.forClass(SosAssessor.Context.class);
        verify(assessor).assess(anyString(), anyInt(), any(), ctx.capture());
        assertEquals(7L, ctx.getValue().incidentId());
        assertEquals(1L, ctx.getValue().citizenId());
        List<NeighborCase> ns = ctx.getValue().neighbors().get();
        assertEquals(2, ns.size());
        assertEquals(new NeighborCase(3L, "Nước ngập, hết lương thực", 20L, DangerLevel.CAO, DangerLevel.TRUNG_BINH, false), ns.get(0));
        assertNull(ns.get(1).contentLevel());          // bản mới nhất chưa có mức theo nội dung (trước V9)
        assertTrue(ns.get(1).adminConfirmed());

        ArgumentCaptor<IncidentAiAssessment> saved = ArgumentCaptor.forClass(IncidentAiAssessment.class);
        verify(assessments).save(saved.capture());
        assertEquals(DangerLevel.KHAN_CAP, saved.getValue().getPredictedLevel());
        assertEquals(DangerLevel.CAO, saved.getValue().getContentLevel());
        assertEquals("AI_SERVICE", saved.getValue().getSource());
    }
}
