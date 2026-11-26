package vn.floodconnect.rescue.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.incident.entity.CompletedBy;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentDangerAdjustmentRepository;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.IncidentMapper;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.matching.MatchingService;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Admin xác minh ca SOS: điều chỉnh mức, đánh dấu không hợp lệ và xem ca lân cận. */
class SosAdminServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);
    private final IncidentDangerAdjustmentRepository adjustments = mock(IncidentDangerAdjustmentRepository.class);
    private final SosService sos = mock(SosService.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final IncidentMapper mapper = mock(IncidentMapper.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final RescueService rescue = mock(RescueService.class);
    private final SosAdminService admin = new SosAdminService(incidents, assignments, adjustments,
            mock(RescueOrganizationRepository.class), mock(MatchingService.class), sos, mapper, notifications, current, rescue);

    private final User adminUser = new User();
    private final User citizen = new User();
    private final Incident i = new Incident();

    @BeforeEach void setUp() {
        adminUser.setId(1L); adminUser.setRole(Role.ADMIN);
        citizen.setId(2L); citizen.setRole(Role.CITIZEN);
        when(current.get()).thenReturn(adminUser);
        i.setId(3L); i.setCode("SOS-1003"); i.setCitizen(citizen);
        i.setStatus(IncidentStatus.PENDING); i.setDangerLevel(DangerLevel.KHONG); i.setNeedsVerification(true);
        when(incidents.findByIdForUpdate(3L)).thenReturn(Optional.of(i));
    }

    @Test void markingInvalidClosesTheCaseAndTellsTheCitizenWhy() {
        admin.markInvalid(3L, "  Đã gọi xác minh, người gửi bấm nhầm  ");

        assertEquals(IncidentStatus.INVALID, i.getStatus()); // không còn "đang hoạt động": người gửi được gửi SOS mới
        assertEquals("Đã gọi xác minh, người gửi bấm nhầm", i.getInvalidReason());
        assertSame(adminUser, i.getInvalidatedBy());
        assertNotNull(i.getInvalidatedAt());
        assertFalse(i.isNeedsVerification());
        verify(notifications).notify(eq(citizen), eq(NotificationType.SOS_INVALID), anyString(), contains("người gửi bấm nhầm"), eq("INCIDENT"), eq(3L));
    }

    @Test void onlyPendingCasesCanBeMarkedInvalid() {
        i.setStatus(IncidentStatus.ASSIGNED);
        ApiException e = assertThrows(ApiException.class, () -> admin.markInvalid(3L, "Tin rác"));
        assertEquals("SOS_NOT_PENDING", e.getCode());
        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
        verifyNoInteractions(notifications);
    }

    @Test void adminCannotChooseKhongWhenAdjustingLevel() {
        i.setDangerLevel(DangerLevel.CAO);
        ApiException e = assertThrows(ApiException.class, () -> admin.adjustLevel(3L, DangerLevel.KHONG, null));
        assertEquals("USE_MARK_INVALID", e.getCode());
        assertEquals(DangerLevel.CAO, i.getDangerLevel());
        verify(adjustments, never()).save(any());
    }

    @Test void verifiedKhongCaseIsSentToOrganizationsWithTheNote() {
        admin.adjustLevel(3L, DangerLevel.TRUNG_BINH, "  Đã gọi lúc 21:40: nhà ngập 1 m, 2 người già, cần xuồng  ");

        assertEquals(DangerLevel.TRUNG_BINH, i.getDangerLevel());
        assertEquals(IncidentStatus.PENDING, i.getStatus());
        assertFalse(i.isNeedsVerification());
        verify(adjustments).save(any());
        verify(sos).notifyNewSos(i);
        assertSame(adminUser, i.getVerifiedBy()); // chọn mức sau khi liên hệ cũng ghi nhận là đã xác minh
        assertNotNull(i.getVerifiedAt());
        assertEquals("Đã gọi lúc 21:40: nhà ngập 1 m, 2 người già, cần xuồng", i.getVerificationNote()); // bỏ khoảng trắng thừa
    }

    @Test void verifyingKhongCaseRequiresANote() {
        for (String blank : new String[] { null, "", "   \n " }) {
            ApiException e = assertThrows(ApiException.class, () -> admin.adjustLevel(3L, DangerLevel.TRUNG_BINH, blank));
            assertEquals("NOTE_REQUIRED", e.getCode());
        }
        assertEquals(DangerLevel.KHONG, i.getDangerLevel()); // không đổi gì, chưa gửi tổ chức
        assertNull(i.getVerifiedBy());
        assertNull(i.getVerificationNote());
        verify(adjustments, never()).save(any());
        verify(sos, never()).notifyNewSos(any());
    }

    @Test void verificationNoteIsLimitedTo1000Characters() {
        assertDoesNotThrow(() -> admin.adjustLevel(3L, DangerLevel.CAO, "a".repeat(1000)));

        i.setDangerLevel(DangerLevel.KHONG); i.setVerificationNote(null);
        ApiException e = assertThrows(ApiException.class, () -> admin.adjustLevel(3L, DangerLevel.CAO, "a".repeat(1001)));
        assertEquals("NOTE_TOO_LONG", e.getCode());
        assertNull(i.getVerificationNote());
    }

    @Test void oldKhongCaseWithoutFlagIsStillRecordedAsVerified() {
        i.setNeedsVerification(false); // ca cũ Admin từng hạ xuống KHONG trước khi có cờ
        admin.adjustLevel(3L, DangerLevel.THAP, "Đã gọi, nước ngập sân");
        assertSame(adminUser, i.getVerifiedBy()); // ghi chú luôn có người ghi và thời điểm
        assertNotNull(i.getVerifiedAt());
    }

    @Test void noteIsOnlyForKhongCases() {
        i.setDangerLevel(DangerLevel.THAP); i.setNeedsVerification(false);
        ApiException e = assertThrows(ApiException.class, () -> admin.adjustLevel(3L, DangerLevel.CAO, "Ghi chú"));
        assertEquals("NOTE_NOT_ALLOWED", e.getCode());
        assertEquals(DangerLevel.THAP, i.getDangerLevel());

        admin.adjustLevel(3L, DangerLevel.CAO, "   "); // điều chỉnh mức thông thường: không có ghi chú
        assertEquals(DangerLevel.CAO, i.getDangerLevel());
        assertNull(i.getVerificationNote());
        assertNull(i.getVerifiedBy()); // không phải xác minh
    }

    @Test void confirmingVerificationKeepsLevelAndStatusAndRecordsTheAdmin() {
        i.setDangerLevel(DangerLevel.CAO); i.setStatus(IncidentStatus.ACCEPTED); // đã tiếp nhận: không đổi mức được nữa nhưng vẫn gỡ được cảnh báo

        admin.confirmVerification(3L);

        assertFalse(i.isNeedsVerification());
        assertEquals(DangerLevel.CAO, i.getDangerLevel());
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus());
        assertSame(adminUser, i.getVerifiedBy());
        assertNotNull(i.getVerifiedAt());
        verify(adjustments, never()).save(any()); // không đổi mức nên không có lịch sử điều chỉnh
        verifyNoInteractions(notifications);
    }

    @Test void khongCaseMustBeResolvedByChoosingALevelOrMarkingInvalid() {
        ApiException e = assertThrows(ApiException.class, () -> admin.confirmVerification(3L)); // i đang KHONG + Chờ xử lý
        assertEquals("USE_REVIEW_FLOW", e.getCode());
        assertTrue(i.isNeedsVerification());
        assertNull(i.getVerifiedBy());
    }

    @Test void confirmingVerificationRejectsFinishedOrUnflaggedCases() {
        i.setDangerLevel(DangerLevel.THAP); i.setStatus(IncidentStatus.COMPLETED);
        assertEquals("SOS_NOT_ACTIVE", assertThrows(ApiException.class, () -> admin.confirmVerification(3L)).getCode());

        i.setStatus(IncidentStatus.PENDING); i.setNeedsVerification(false); // Admin khác vừa xác minh xong
        assertEquals("NO_VERIFICATION_NEEDED", assertThrows(ApiException.class, () -> admin.confirmVerification(3L)).getCode());
        assertNull(i.getVerifiedBy());
    }

    private static Incident at(long id, double lat) {
        Incident x = new Incident();
        x.setId(id); x.setLatitude(lat); x.setLongitude(105.8);
        return x;
    }

    @Test void nearbyIsComputedNowIncludesLaterAndRecentlyCompletedCasesClosestFirst() {
        i.setLatitude(21.0); i.setLongitude(105.8); i.setCreatedAt(Instant.parse("2026-11-03T08:00:00Z"));
        when(incidents.findById(3L)).thenReturn(Optional.of(i));
        Incident later = at(4L, 21.003), close = at(5L, 21.001), done = at(6L, 21.002); // cách khoảng 334 m, 111 m, 222 m
        when(incidents.findWithinRadius(eq(SosService.ACTIVE), eq(Instant.EPOCH), eq(21.0), eq(105.8), eq(SosService.DUP_RADIUS_M)))
                .thenReturn(List.of(i, later, close)); // truy vấn trả cả chính ca đang xem
        // ca đã hoàn thành: chỉ lấy ca gửi từ 24 giờ trước ca đang xem trở về sau
        when(incidents.findWithinRadius(eq(EnumSet.of(IncidentStatus.COMPLETED)), eq(Instant.parse("2026-11-02T08:00:00Z")),
                eq(21.0), eq(105.8), eq(SosService.DUP_RADIUS_M))).thenReturn(List.of(done));

        when(mapper.toDtos(anyList(), eq(adminUser))).thenAnswer(inv -> ((List<?>) inv.getArgument(0)).stream().map(x -> (IncidentDto) null).toList());

        var out = admin.nearby(3L);

        assertEquals(List.of(111L, 222L, 334L), out.stream().map(x -> Math.round(x.distanceM())).toList()); // gần nhất trước, không có chính ca
        // chuyển cả danh sách một lần (không N+1), đúng thứ tự gần nhất trước, Admin xem đủ chi tiết
        verify(mapper).toDtos(List.of(close, done, later), adminUser);
        verify(mapper, never()).toDto(any(), any(), any(), any());
    }

    // ---------- Admin xác nhận hoàn thành ca Đã tiếp nhận ----------
    private User acceptedByOrg() {
        User orgUser = new User(); orgUser.setId(9L); orgUser.setRole(Role.RESCUE_ORG);
        RescueOrganization org = new RescueOrganization(); org.setId(5L); org.setUser(orgUser);
        i.setStatus(IncidentStatus.ACCEPTED); i.setDangerLevel(DangerLevel.CAO); i.setAssignedOrg(org);
        return orgUser;
    }

    @Test void adminCompletingRecordsAdminAndReasonAndTellsOrgAndCitizen() {
        User orgUser = acceptedByOrg();

        admin.complete(3L, "  Đã gọi người dân, nước đã rút  ");

        assertEquals(IncidentStatus.COMPLETED, i.getStatus());
        assertNotNull(i.getCompletedAt());
        assertEquals(CompletedBy.ADMIN, i.getCompletedByRole()); // không tính là thành tích cứu hộ của tổ chức
        assertSame(adminUser, i.getCompletedBy());
        assertEquals("Đã gọi người dân, nước đã rút", i.getCompletionNote());
        verify(notifications).notify(eq(orgUser), eq(NotificationType.SOS_STATUS), anyString(), contains("dừng cứu hộ"), eq("INCIDENT"), eq(3L));
        verify(notifications).notify(eq(citizen), eq(NotificationType.SOS_STATUS), anyString(), contains("nước đã rút"), eq("INCIDENT"), eq(3L));
    }

    @Test void adminCanOnlyCompleteAcceptedCases() {
        for (IncidentStatus st : EnumSet.of(IncidentStatus.PENDING, IncidentStatus.ASSIGNED, IncidentStatus.COMPLETED,
                IncidentStatus.CANCELLED, IncidentStatus.INVALID)) {
            i.setStatus(st);
            ApiException e = assertThrows(ApiException.class, () -> admin.complete(3L, "Người dân đã an toàn"));
            assertEquals("SOS_NOT_ACCEPTED", e.getCode());
            assertEquals(st, i.getStatus());
        }
        verifyNoInteractions(notifications);
    }

    @Test void adminMustGiveAReasonToComplete() {
        acceptedByOrg();
        ApiException e = assertThrows(ApiException.class, () -> admin.complete(3L, "   "));
        assertEquals("REASON_REQUIRED", e.getCode());
        assertEquals(IncidentStatus.ACCEPTED, i.getStatus());
        verifyNoInteractions(notifications);
    }

    // ---------- Admin hủy phân công ca Đã phân công ----------
    private RescueOrganization assignedTo(AssignmentStatus last) {
        User orgUser = new User(); orgUser.setId(9L); orgUser.setRole(Role.RESCUE_ORG);
        RescueOrganization org = new RescueOrganization(); org.setId(5L); org.setUser(orgUser);
        i.setStatus(IncidentStatus.ASSIGNED); i.setDangerLevel(DangerLevel.CAO); i.setAssignedOrg(org); i.setAssignedAt(Instant.now());
        IncidentAssignment a = new IncidentAssignment(); a.setIncident(i); a.setOrg(org); a.setStatus(last);
        when(assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L)).thenReturn(Optional.of(a));
        return org;
    }

    @Test void unassignWhileWaitingCancelsTheAssignmentAndReturnsTheCase() {
        RescueOrganization org = assignedTo(AssignmentStatus.WAITING_CONFIRM);
        IncidentAssignment a = assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L).orElseThrow();

        admin.unassign(3L, "  Đã gọi, đội không có xuồng  ");

        // quyết định của Admin, không phải tổ chức từ chối: không trừ điểm W của AI Matching
        assertEquals(AssignmentStatus.CANCELLED, a.getStatus());
        assertNotNull(a.getRespondedAt());
        assertEquals("Admin hủy phân công: Đã gọi, đội không có xuồng", a.getRejectReason());
        verify(notifications).notify(eq(org.getUser()), eq(NotificationType.SOS_STATUS), anyString(),
                contains("Đã gọi, đội không có xuồng"), eq("INCIDENT"), eq(3L));
        verify(rescue).returnToPending(i, org); // về Chờ xử lý, báo người dân và tổ chức gần đó (trừ tổ chức này)
    }

    @Test void unassignAfterTimeoutKeepsTimeout() {
        RescueOrganization org = assignedTo(AssignmentStatus.TIMEOUT);
        IncidentAssignment a = assignments.findFirstByIncidentIdOrderByCreatedAtDesc(3L).orElseThrow();

        admin.unassign(3L, "Đội không nghe máy");

        assertEquals(AssignmentStatus.TIMEOUT, a.getStatus()); // tổ chức thật sự không phản hồi: vẫn tính vào điểm W
        verify(rescue).returnToPending(i, org);
    }

    @Test void unassignIsRejectedWhenTheOrgJustAcceptedOrTheCaseIsNotAssigned() {
        assignedTo(AssignmentStatus.ACCEPTED);
        i.setStatus(IncidentStatus.ACCEPTED); // tổ chức vừa bấm xác nhận đúng lúc đó
        assertEquals("SOS_ALREADY_ACCEPTED", assertThrows(ApiException.class, () -> admin.unassign(3L, "Lý do")).getCode());

        for (IncidentStatus st : EnumSet.of(IncidentStatus.PENDING, IncidentStatus.COMPLETED, IncidentStatus.CANCELLED, IncidentStatus.INVALID)) {
            i.setStatus(st);
            assertEquals("SOS_NOT_ASSIGNED", assertThrows(ApiException.class, () -> admin.unassign(3L, "Lý do")).getCode());
        }
        verifyNoInteractions(notifications, rescue);
    }

    @Test void unassignRequiresAReasonOfAtMost500Characters() {
        assignedTo(AssignmentStatus.WAITING_CONFIRM);
        for (String blank : new String[] { null, "", "   " })
            assertEquals("REASON_REQUIRED", assertThrows(ApiException.class, () -> admin.unassign(3L, blank)).getCode());
        assertEquals("REASON_TOO_LONG", assertThrows(ApiException.class, () -> admin.unassign(3L, "a".repeat(501))).getCode());
        assertEquals(IncidentStatus.ASSIGNED, i.getStatus());
        verifyNoInteractions(notifications, rescue);
    }
}
