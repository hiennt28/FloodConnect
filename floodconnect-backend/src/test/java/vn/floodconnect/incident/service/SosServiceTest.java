package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import vn.floodconnect.ai.service.SosAssessor;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.ai.service.WeatherService;
import org.mockito.InOrder;
import org.springframework.transaction.PlatformTransactionManager;
import vn.floodconnect.incident.dto.IncidentDtos.CreateSosResponse;
import vn.floodconnect.incident.dto.IncidentDtos.SosCreateRequest;
import vn.floodconnect.incident.dto.IncidentDtos.SosUpdateRequest;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.incident.repository.IncidentDangerAdjustmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Quy tắc gửi và hủy ca SOS của người dân. */
class SosServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final SosAssessor assessor = mock(SosAssessor.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final WeatherService weather = mock(WeatherService.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final RescueOrganizationRepository orgs = mock(RescueOrganizationRepository.class);
    private final SosService sos = new SosService(incidents, mock(IncidentAiAssessmentRepository.class),
            mock(IncidentDangerAdjustmentRepository.class), orgs, assessor, notifications,
            current, new ObjectMapper(), mock(IncidentMapper.class), assignments, weather, txManager);

    private final User citizen = new User();
    private final SosCreateRequest request = new SosCreateRequest("Nước ngập tới ngực, có trẻ nhỏ", 16.05, 108.2, "Kiệt 126 Nguyễn Trãi", false);

    @BeforeEach void setUp() {
        citizen.setId(1L); citizen.setRole(Role.CITIZEN);
        when(current.get()).thenReturn(citizen);
        when(assessor.assess(anyString(), anyInt(), any(), any()))
                .thenReturn(new SosAssessor.Assessment(DangerLevel.CAO, DangerLevel.CAO, 0.9, false, false, null, 0, List.of()));
        when(incidents.saveAndFlush(any(Incident.class))).thenAnswer(inv -> { Incident i = inv.getArgument(0); i.setId(7L); return i; });
    }

    /** Ca khác cách vị trí gửi khoảng 15 m. */
    private static Incident nearby(DangerLevel level) {
        Incident i = new Incident();
        i.setId(3L); i.setLatitude(16.0501); i.setLongitude(108.2001); i.setDangerLevel(level); i.setStatus(IncidentStatus.PENDING);
        i.setCreatedAt(Instant.now());
        return i;
    }

    /** Chỉ người dân gửi SOS: tổ chức cứu hộ và Admin bị từ chối trước cả khi gọi thời tiết hay ghi CSDL. */
    @Test void onlyCitizensCanSendSos() {
        for (Role role : List.of(Role.RESCUE_ORG, Role.ADMIN)) {
            citizen.setRole(role);
            ApiException e = assertThrows(ApiException.class, () -> sos.create(request));
            assertEquals("SOS_CITIZEN_ONLY", e.getCode());
        }
        verifyNoInteractions(weather);
        verify(incidents, never()).saveAndFlush(any());
    }

    @Test void addressLongerThanColumnIsRejectedWithClearMessage() {
        SosCreateRequest longAddress = new SosCreateRequest("Nước ngập tới ngực", 16.05, 108.2, "a".repeat(256), false);
        ApiException e = assertThrows(ApiException.class, () -> sos.create(longAddress));
        assertEquals("INVALID_ADDRESS", e.getCode());
        verify(incidents, never()).saveAndFlush(any());
    }

    @Test void citizenWithActiveCaseCannotSendAnother() {
        when(incidents.existsByCitizenIdAndStatusIn(eq(1L), any())).thenReturn(true);
        ApiException e = assertThrows(ApiException.class, () -> sos.create(request));
        assertEquals("SOS_ALREADY_ACTIVE", e.getCode());
        verify(incidents, never()).saveAndFlush(any());
    }

    @Test void similarCaseNearbyOnlyWarns() {
        when(incidents.findWithinRadius(any(), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(nearby(DangerLevel.CAO)));
        CreateSosResponse r = sos.create(request);
        assertTrue(r.duplicateWarning());
        verify(incidents, never()).saveAndFlush(any());
    }

    @Test void lockedCitizenCanStillSendAndHiddenKhongCaseIsNotADuplicate() {
        citizen.setStatus(UserStatus.LOCKED); citizen.setLockedUntil(Instant.now().plusSeconds(86_400));
        when(incidents.findWithinRadius(any(), any(), anyDouble(), anyDouble(), anyDouble())).thenReturn(List.of(nearby(DangerLevel.KHONG)));
        CreateSosResponse r = sos.create(request);
        assertFalse(r.duplicateWarning());
        verify(incidents).saveAndFlush(any());
    }

    @Test void secondOfTwoSimultaneousRequestsGetsAlreadyActive() {
        doThrow(new DataIntegrityViolationException("insert",
                new RuntimeException("duplicate key value violates unique constraint \"uq_incidents_one_active_per_citizen\"")))
                .when(incidents).saveAndFlush(any(Incident.class));
        ApiException e = assertThrows(ApiException.class, () -> sos.create(request));
        assertEquals("SOS_ALREADY_ACTIVE", e.getCode());
    }

    /** Ca đang Chờ xử lý của người dân, mức cho trước; sửa SOS đọc tọa độ trước (findById) để lấy thời tiết ngoài transaction. */
    private Incident editable(DangerLevel level, String description) {
        Incident i = nearby(level);
        i.setCitizen(citizen); i.setDescription(description);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
        when(incidents.findById(3L)).thenReturn(Optional.of(i));
        return i;
    }

    private void aiRates(DangerLevel level, boolean verify, boolean adminReview) {
        when(assessor.assess(anyString(), anyInt(), any(), any()))
                .thenReturn(new SosAssessor.Assessment(level, level, 0.8, verify, adminReview, null, 0, List.of()));
    }

    /** Sửa mô tả được chấm lại y như gửi ca mới: AI chấm KHONG thì ca thành "Cần xác minh", chỉ Admin được báo. */
    @Test void editRatedOutOfScopeBecomesKhongLikeANewSos() {
        Incident i = editable(DangerLevel.TRUNG_BINH, "Nước ngập tới đầu gối");
        aiRates(DangerLevel.KHONG, true, true);

        sos.update(3L, new SosUpdateRequest("Chào bạn hôm nay trời đẹp"));

        assertEquals(DangerLevel.KHONG, i.getDangerLevel());
        assertTrue(i.isNeedsVerification());
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_NEW), eq("Ca SOS cần xác minh"), anyString(), eq("INCIDENT"), eq(3L));
        verify(orgs, never()).findAvailableCovering(anyDouble(), anyDouble()); // không báo tổ chức
    }

    /** AI chấm thấp hơn mức cũ: nhãn theo đúng AI, cờ "nên xác minh" theo AI; đổi giữa Thấp..Khẩn cấp thì không báo lại ai. */
    @Test void editRatedLowerFollowsAiWithoutRenotifying() {
        Incident i = editable(DangerLevel.CAO, "Nước ngập tới ngực, có người già");
        aiRates(DangerLevel.THAP, false, false);

        sos.update(3L, new SosUpdateRequest("Nước ngập sân, mọi người đã lên tầng 2"));

        assertEquals(DangerLevel.THAP, i.getDangerLevel());
        assertFalse(i.isNeedsVerification());
        verify(notifications, never()).notifyAdmins(any(), anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(orgs);
    }

    @Test void editRatedLowerAndUncertainIsFlaggedAndClearsOldVerification() {
        Incident i = editable(DangerLevel.CAO, "Nước ngập tới ngực, có người già");
        i.setVerifiedBy(new User()); i.setVerifiedAt(Instant.now()); // lần xác minh trước nội dung mới
        aiRates(DangerLevel.TRUNG_BINH, true, false);

        sos.update(3L, new SosUpdateRequest("ngập"));

        assertEquals(DangerLevel.TRUNG_BINH, i.getDangerLevel());
        assertTrue(i.isNeedsVerification());
        assertNull(i.getVerifiedBy()); // nội dung đổi, AI lại gắn cờ: lần xác minh cũ không còn giá trị
    }

    @Test void editRatedHigherFollowsAiWithoutRenotifying() {
        Incident i = editable(DangerLevel.TRUNG_BINH, "Nước ngập tới đầu gối");

        sos.update(3L, new SosUpdateRequest("Nước ngập tới ngực, có trẻ nhỏ")); // AI chấm Cao (setUp)

        assertEquals(DangerLevel.CAO, i.getDangerLevel());
        verify(notifications, never()).notifyAdmins(any(), anyString(), anyString(), anyString(), anyLong());
        verifyNoInteractions(orgs);
    }

    /** Ca KHONG sửa lại thành yêu cầu cứu hộ thật: báo Admin và tổ chức trong khu vực như ca mới. */
    @Test void editFromKhongToRealLevelIsAnnouncedLikeANewSos() {
        Incident i = editable(DangerLevel.KHONG, "alo");

        sos.update(3L, new SosUpdateRequest("Nước ngập tới ngực, có trẻ nhỏ")); // AI chấm Cao (setUp)

        assertEquals(DangerLevel.CAO, i.getDangerLevel());
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_NEW), eq("Ca SOS mới"), anyString(), eq("INCIDENT"), eq(3L));
        verify(orgs).findAvailableCovering(anyDouble(), anyDouble());
    }

    @Test void weatherIsFetchedBeforeTheTransactionOpens() {
        sos.create(request);

        InOrder order = inOrder(weather, txManager, assessor);
        order.verify(weather).at(16.05, 108.2);     // gọi mạng khi chưa giữ kết nối DB
        order.verify(txManager).getTransaction(any());
        order.verify(assessor).assess(anyString(), anyInt(), isNull(), any()); // weather mock trả null: không có dữ liệu thời tiết
        order.verify(txManager).commit(any());
    }

    private static RescueOrganization orgAt(long id, double lat) {
        RescueOrganization o = new RescueOrganization();
        User u = new User(); u.setId(100 + id);
        o.setId(id); o.setUser(u); o.setLatitude(lat); o.setLongitude(108.2);
        return o;
    }

    @Test void caseReturnedToPendingSkipsTheReturningOrgAndOrgsThatAlreadyRefused() {
        Incident i = nearby(DangerLevel.CAO);
        RescueOrganization returning = orgAt(5L, 16.06), refusedBefore = orgAt(6L, 16.07), fresh = orgAt(7L, 16.08);
        when(orgs.findAvailableCovering(anyDouble(), anyDouble())).thenReturn(List.of(returning, refusedBefore, fresh));
        when(assignments.findOrgIdsByIncidentIdAndStatusIn(eq(3L), any())).thenReturn(List.of(6L));

        sos.notifyNearbyOrgs(i, "Ca SOS cần tổ chức tiếp nhận", "Ca SOS-1003 mức Cao vừa trở về Chờ xử lý", 5L);

        verify(notifications).notify(eq(fresh.getUser()), any(), eq("Ca SOS cần tổ chức tiếp nhận"), contains("cách bạn"), eq("INCIDENT"), eq(3L));
        verify(notifications, never()).notify(eq(returning.getUser()), any(), anyString(), anyString(), anyString(), anyLong());
        verify(notifications, never()).notify(eq(refusedBefore.getUser()), any(), anyString(), anyString(), anyString(), anyLong());
    }

    @Test void cancellingAssignedCaseIsNotCountedAsOrgRejection() {
        RescueOrganization org = new RescueOrganization();
        org.setUser(new User());
        Incident i = nearby(DangerLevel.CAO);
        i.setCitizen(citizen); i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedOrg(org);
        IncidentAssignment a = new IncidentAssignment();
        a.setStatus(AssignmentStatus.WAITING_CONFIRM);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
        when(incidents.findById(3L)).thenReturn(Optional.of(i)); // sửa SOS đọc tọa độ trước để lấy thời tiết ngoài transaction
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));

        sos.cancel(3L);

        assertEquals(IncidentStatus.CANCELLED, i.getStatus());
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus()); // AI Matching chỉ trừ điểm REJECTED/TIMEOUT
    }

    // ---------- Người dân báo "Tôi đã an toàn" ----------
    private Incident acceptedCase(User orgUser) {
        RescueOrganization org = new RescueOrganization();
        org.setUser(orgUser);
        Incident i = nearby(DangerLevel.CAO);
        i.setCode("SOS-1003"); i.setCitizen(citizen); i.setStatus(IncidentStatus.ACCEPTED); i.setAssignedOrg(org);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
        return i;
    }

    @Test void citizenMarkingSafeCompletesTheCaseAndTellsTheOrgToStop() {
        User orgUser = new User();
        Incident i = acceptedCase(orgUser);

        sos.markSafe(3L, "  Hàng xóm đã chở cả nhà đi  ");

        assertEquals(IncidentStatus.COMPLETED, i.getStatus());
        assertNotNull(i.getCompletedAt());
        assertEquals(CompletedBy.CITIZEN, i.getCompletedByRole());
        assertSame(citizen, i.getCompletedBy());
        assertEquals("Hàng xóm đã chở cả nhà đi", i.getCompletionNote());
        verify(notifications).notify(eq(orgUser), eq(NotificationType.SOS_STATUS), anyString(), contains("dừng cứu hộ"), eq("INCIDENT"), eq(3L));
        verify(notifications).notifyAdmins(eq(NotificationType.SOS_STATUS), anyString(), contains("đã an toàn"), eq("INCIDENT"), eq(3L));
    }

    @Test void noteIsOptionalWhenMarkingSafe() {
        Incident i = acceptedCase(new User());
        sos.markSafe(3L, "   ");
        assertEquals(IncidentStatus.COMPLETED, i.getStatus());
        assertNull(i.getCompletionNote());
    }

    @Test void onlyTheCitizenWhoSentTheCaseCanMarkItSafe() {
        Incident i = acceptedCase(new User());
        User other = new User(); other.setId(2L); other.setRole(Role.CITIZEN);
        when(current.get()).thenReturn(other);

        assertThrows(ApiException.class, () -> sos.markSafe(3L, null));
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test void caseNotYetAcceptedMustBeCancelledInstead() {
        Incident i = acceptedCase(new User());
        i.setStatus(IncidentStatus.ASSIGNED);
        ApiException e = assertThrows(ApiException.class, () -> sos.markSafe(3L, null));
        assertEquals("SOS_USE_CANCEL", e.getCode());
        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
    }

    @Test void finishedCaseCannotBeMarkedSafe() {
        Incident i = acceptedCase(new User());
        i.setStatus(IncidentStatus.COMPLETED); // tổ chức vừa bấm Hoàn thành
        i.setCompletedByRole(CompletedBy.ORG);
        ApiException e = assertThrows(ApiException.class, () -> sos.markSafe(3L, null));
        assertEquals("SOS_FINISHED", e.getCode());
        assertEquals(CompletedBy.ORG, i.getCompletedByRole());
        verifyNoInteractions(notifications);
    }

    // ---------- Sửa ca SOS: chỉ có mô tả (không ảnh) ----------
    @Test void editWithUnchangedDescriptionChangesNothingAndNotifiesNoOne() {
        Incident i = nearby(DangerLevel.CAO);
        i.setCitizen(citizen); i.setDescription("Nước ngập tới ngực"); i.setStatus(IncidentStatus.PENDING);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
        when(incidents.findById(3L)).thenReturn(Optional.of(i));

        sos.update(3L, new SosUpdateRequest("  Nước ngập tới ngực  "));

        verify(assessor, never()).assess(anyString(), anyInt(), any());
        verifyNoInteractions(notifications);
    }

    /** Đã phân công: nội dung đóng băng (tổ chức quyết định dựa trên mô tả đã dùng để tính matching), người dân chỉ hủy được */
    @Test void assignedCaseCannotBeEditedButCanStillBeCancelled() {
        RescueOrganization org = new RescueOrganization();
        org.setUser(new User());
        Incident i = nearby(DangerLevel.THAP);
        i.setCitizen(citizen); i.setDescription("Nhà ngập, cần thực phẩm"); i.setStatus(IncidentStatus.ASSIGNED); i.setAssignedOrg(org);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
        when(incidents.findById(3L)).thenReturn(Optional.of(i));

        ApiException e = assertThrows(ApiException.class, () -> sos.update(3L, new SosUpdateRequest("Có người bị thương nặng")));
        assertEquals("SOS_NOT_EDITABLE", e.getCode());
        assertTrue(e.getMessage().contains("112"));
        assertEquals("Nhà ngập, cần thực phẩm", i.getDescription());
        assertEquals(DangerLevel.THAP, i.getDangerLevel());
        verify(assessor, never()).assess(anyString(), anyInt(), any());
        verifyNoInteractions(notifications);

        sos.cancel(3L);
        assertEquals(IncidentStatus.CANCELLED, i.getStatus());
    }

    @Test void acceptedCaseCannotBeEdited() {
        Incident i = acceptedCase(new User());
        i.setDescription("Nhà ngập");
        when(incidents.findById(3L)).thenReturn(Optional.of(i));
        assertEquals("SOS_NOT_EDITABLE", assertThrows(ApiException.class, () -> sos.update(3L, new SosUpdateRequest("Ngập sâu hơn"))).getCode());
        assertEquals("Nhà ngập", i.getDescription());
    }

    @Test void editNeedsADescription() {
        ApiException e = assertThrows(ApiException.class, () -> sos.update(3L, new SosUpdateRequest("   ")));
        assertEquals("INVALID_DESCRIPTION", e.getCode());
        verify(incidents, never()).findByIdForUpdate(any());
    }
}
