package vn.floodconnect.rescue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.IncidentMapper;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.service.OrgContext;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.AssignmentType;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.matching.MatchingService;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

/** Tổ chức chủ động tiếp nhận ca SOS; trả ca Đã phân công khi tổ chức bị khóa / chuyển Không hoạt động. */
class RescueServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final OrgContext orgContext = mock(OrgContext.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final SosService sosService = mock(SosService.class);
    private final RescueService rescue = new RescueService(incidents, assignments, mock(MatchingService.class), notifications,
            current, orgContext, mock(IncidentMapper.class), sosService);
    private final RescueOrganization org = new RescueOrganization();
    private final Incident sos = new Incident();

    @BeforeEach void setUp() {
        User u = new User();
        u.setId(10L); u.setRole(Role.RESCUE_ORG);
        org.setId(5L); org.setUser(u); org.setName("Đội A"); org.setStatus(OrgStatus.ACTIVE); org.setOperatingStatus(OperatingStatus.AVAILABLE);
        when(current.get()).thenReturn(u);
        when(orgContext.active(u)).thenReturn(org);
        sos.setId(3L); sos.setCitizen(new User()); sos.setDangerLevel(DangerLevel.CAO);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(sos));
    }

    @Test void claimingPendingCaseAcceptsItForTheOrg() {
        sos.setStatus(IncidentStatus.PENDING);
        rescue.claim(3L);
        assertEquals(IncidentStatus.ACCEPTED, sos.getStatus());
        assertSame(org, sos.getAssignedOrg());
        ArgumentCaptor<IncidentAssignment> a = ArgumentCaptor.forClass(IncidentAssignment.class);
        verify(assignments).save(a.capture());
        assertEquals(AssignmentType.ORG_CLAIM, a.getValue().getType());
        assertEquals(AssignmentStatus.ACCEPTED, a.getValue().getStatus());
    }

    @Test void caseAlreadyTakenByAnotherOrgCannotBeClaimed() {
        sos.setStatus(IncidentStatus.ACCEPTED);
        ApiException e = assertThrows(ApiException.class, () -> rescue.claim(3L));
        assertEquals("SOS_UNAVAILABLE", e.getCode());
        verify(assignments, never()).save(any());
    }

    @Test void releasingReturnsTheOrgsUnansweredCasesToPending() {
        sos.setStatus(IncidentStatus.ASSIGNED); sos.setAssignedOrg(org); sos.setAssignedAt(Instant.now()); sos.setCode("SOS-1003");
        IncidentAssignment a = new IncidentAssignment();
        a.setStatus(AssignmentStatus.WAITING_CONFIRM);
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));
        when(incidents.findIdsByAssignedOrgIdAndStatusIn(eq(5L), any())).thenReturn(List.of(3L));

        rescue.releaseAssigned(org, "Tổ chức bị khóa");

        assertEquals(IncidentStatus.PENDING, sos.getStatus());
        assertNull(sos.getAssignedOrg());
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus());
        verify(notifications).notifyAdmins(any(), anyString(), anyString(), eq("INCIDENT"), eq(3L));
        verify(sosService).notifyNearbyOrgs(eq(sos), anyString(), anyString(), eq(5L)); // tổ chức gần đó, trừ tổ chức vừa trả ca
    }

    @Test void rejectingTellsCitizenAndNearbyOrgsButNotTheReasonToCitizen() {
        User citizen = new User();
        sos.setCitizen(citizen); sos.setCode("SOS-1003");
        sos.setStatus(IncidentStatus.ASSIGNED); sos.setAssignedOrg(org);
        IncidentAssignment a = new IncidentAssignment();
        a.setStatus(AssignmentStatus.WAITING_CONFIRM);
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));

        rescue.rejectAssigned(3L, "Xuồng đang hỏng");

        assertEquals(IncidentStatus.PENDING, sos.getStatus());
        assertEquals(AssignmentStatus.REJECTED, a.getStatus());
        verify(notifications).notifyAdmins(any(), anyString(), contains("Xuồng đang hỏng"), eq("INCIDENT"), eq(3L)); // Admin biết lý do
        verify(notifications).notify(eq(citizen), any(), anyString(), argThat(b -> b.contains("tổ chức khác") && !b.contains("Xuồng")), eq("INCIDENT"), eq(3L));
        verify(sosService).notifyNearbyOrgs(eq(sos), eq("Ca SOS cần tổ chức tiếp nhận"), contains("mức Cao"), eq(5L));
    }

    /** Hàm dùng chung (tổ chức từ chối, bị khóa, Admin hủy phân công, tự trả ca): bỏ tổ chức, báo người dân và tổ chức gần đó. */
    @Test void returnToPendingClearsTheOrgAndNotifies() {
        User citizen = new User();
        sos.setCitizen(citizen); sos.setCode("SOS-1003");
        sos.setStatus(IncidentStatus.ASSIGNED); sos.setAssignedOrg(org); sos.setAssignedAt(Instant.now());
        sos.setPendingTimeoutNotified(true); sos.setAssignTimeoutNotified(true);

        rescue.returnToPending(sos, org);

        assertEquals(IncidentStatus.PENDING, sos.getStatus());
        assertNull(sos.getAssignedOrg());
        assertNull(sos.getAssignedAt());
        // ca đã được nhắc "chờ quá 15 phút" thì không nhắc lại: lúc trả ca Admin đã được báo kèm lý do (tránh báo trùng)
        assertTrue(sos.isPendingTimeoutNotified());
        assertFalse(sos.isAssignTimeoutNotified());
        verify(notifications).notify(eq(citizen), any(), anyString(), contains("tổ chức khác"), eq("INCIDENT"), eq(3L));
        verify(sosService).notifyNearbyOrgs(eq(sos), anyString(), anyString(), eq(5L));
    }

    /** Ca mới gửi (chưa tới mốc 15 phút, chưa nhắc) bị trả về: vẫn được nhắc khi đủ 15 phút như thường. */
    @Test void returnedYoungCaseKeepsItsPendingReminder() {
        sos.setCode("SOS-1003"); sos.setStatus(IncidentStatus.ASSIGNED); sos.setAssignedOrg(org);
        sos.setPendingTimeoutNotified(false);

        rescue.returnToPending(sos, org);

        assertFalse(sos.isPendingTimeoutNotified());
    }

    @Test void caseReassignedToAnotherOrgMeanwhileIsNotReleased() {
        RescueOrganization other = new RescueOrganization();
        other.setId(6L);
        sos.setStatus(IncidentStatus.ASSIGNED); sos.setAssignedOrg(other); // Admin vừa điều phối lại cho tổ chức khác trước khi khóa được ca
        when(incidents.findIdsByAssignedOrgIdAndStatusIn(eq(5L), any())).thenReturn(List.of(3L));

        rescue.releaseAssigned(org, "Tổ chức bị khóa");

        assertEquals(IncidentStatus.ASSIGNED, sos.getStatus());
        assertSame(other, sos.getAssignedOrg()); // phân công mới không bị xóa
        verifyNoInteractions(notifications);
        verify(assignments, never()).findFirstByIncidentIdOrderByCreatedAtDesc(any());
    }
}
