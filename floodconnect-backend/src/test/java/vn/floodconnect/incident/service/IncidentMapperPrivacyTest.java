package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.service.UserMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Quyền xem chi tiết ca SOS: chỉ Admin, chủ ca, tổ chức phụ trách ca và tổ chức đang hoạt động thấy mô tả, địa chỉ, SĐT người gửi và tọa độ chính xác. */
class IncidentMapperPrivacyTest {
    private final RescueOrganizationRepository orgs = mock(RescueOrganizationRepository.class);
    private final IncidentMapper mapper = new IncidentMapper(mock(IncidentAiAssessmentRepository.class),
            mock(IncidentAssignmentRepository.class), orgs, mock(UserMapper.class), new ObjectMapper());

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id); u.setRole(role); u.setPhone("09000000" + id);
        return u;
    }

    private static Incident sos(User citizen) {
        Incident i = new Incident();
        i.setCitizen(citizen); i.setStatus(IncidentStatus.PENDING); i.setDangerLevel(DangerLevel.CAO);
        i.setDescription("Nhà có 2 người già bị kẹt"); i.setAddress("Kiệt 126 Nguyễn Trãi, gần chợ");
        i.setLatitude(16.05437); i.setLongitude(108.20291); i.setCreatedAt(Instant.now());
        return i;
    }

    private RescueOrganization org(User u, OrgStatus status) {
        RescueOrganization o = new RescueOrganization();
        o.setId(u.getId()); o.setUser(u); o.setStatus(status);
        when(orgs.findByUserId(u.getId())).thenReturn(Optional.of(o));
        return o;
    }

    private static void assertHidden(IncidentDto d) {
        assertNull(d.description());
        assertNull(d.address());
        assertNull(d.citizenPhone());
        assertEquals(16.054, d.latitude()); // tọa độ vẫn trả vì bản đồ công khai cần, nhưng làm tròn ~100 m
        assertEquals(108.203, d.longitude());
    }

    private static void assertVisible(IncidentDto d) {
        assertEquals("Nhà có 2 người già bị kẹt", d.description());
        assertEquals("Kiệt 126 Nguyễn Trãi, gần chợ", d.address());
        assertNotNull(d.citizenPhone());
        assertEquals(16.05437, d.latitude()); // người có quyền cần tọa độ chính xác để đến cứu
        assertEquals(108.20291, d.longitude());
    }

    @Test void otherCitizenSeesNoDetail() {
        assertHidden(mapper.toDto(sos(user(1, Role.CITIZEN)), user(2, Role.CITIZEN)));
    }

    @Test void ownerAndAdminSeeDetail() {
        User owner = user(1, Role.CITIZEN);
        Incident i = sos(owner);
        assertVisible(mapper.toDto(i, owner));
        assertVisible(mapper.toDto(i, user(3, Role.ADMIN)));
    }

    @Test void lockedOrgSeesOnlyItsOwnCase() {
        Incident i = sos(user(1, Role.CITIZEN));
        User active = user(10, Role.RESCUE_ORG), locked = user(11, Role.RESCUE_ORG);
        org(active, OrgStatus.ACTIVE);
        RescueOrganization lockedOrg = org(locked, OrgStatus.LOCKED);
        assertVisible(mapper.toDto(i, active));
        assertHidden(mapper.toDto(i, locked));

        i.setAssignedOrg(lockedOrg); i.setStatus(IncidentStatus.ACCEPTED); // ca tổ chức bị khóa đang phụ trách dở
        assertVisible(mapper.toDto(i, locked));
    }

    /** Ghi chú xác minh đi cùng mô tả: Admin, chủ ca, tổ chức đang hoạt động thấy (kèm thời điểm); người khác không; tên Admin chỉ Admin thấy. */
    @Test void verificationNoteFollowsDescriptionVisibility() {
        User owner = user(1, Role.CITIZEN), orgUser = user(10, Role.RESCUE_ORG), adminUser = user(3, Role.ADMIN);
        adminUser.setFullName("Quản trị viên");
        org(orgUser, OrgStatus.ACTIVE);
        Incident i = sos(owner);
        Instant at = Instant.parse("2026-11-13T14:40:00Z");
        i.setVerificationNote("Đã gọi: nhà ngập 1 m, 2 người già"); i.setVerifiedBy(adminUser); i.setVerifiedAt(at);

        for (User allowed : List.of(owner, orgUser, adminUser)) {
            IncidentDto d = mapper.toDto(i, allowed);
            assertEquals("Đã gọi: nhà ngập 1 m, 2 người già", d.verificationNote());
            assertEquals(at, d.verifiedAt());
        }
        assertEquals("Quản trị viên", mapper.toDto(i, adminUser).verifiedByName());
        assertNull(mapper.toDto(i, orgUser).verifiedByName());
        assertNull(mapper.toDto(i, owner).verifiedByName());

        IncidentDto other = mapper.toDto(i, user(2, Role.CITIZEN));
        assertNull(other.verificationNote());
        assertNull(other.verifiedAt());
    }

    /** Ca "Nên xác minh" được Admin xác minh (không có ghi chú): thời điểm xác minh vẫn chỉ Admin thấy như trước. */
    @Test void verifiedAtWithoutNoteStaysAdminOnly() {
        User owner = user(1, Role.CITIZEN), orgUser = user(10, Role.RESCUE_ORG);
        org(orgUser, OrgStatus.ACTIVE);
        Incident i = sos(owner);
        i.setVerifiedAt(Instant.now());
        assertNull(mapper.toDto(i, owner).verifiedAt());
        assertNull(mapper.toDto(i, orgUser).verifiedAt());
        assertNotNull(mapper.toDto(i, user(3, Role.ADMIN)).verifiedAt());
    }

    /** Trụ sở tổ chức phụ trách: tọa độ chính xác và SĐT chỉ cho Admin, chủ ca, chính tổ chức đó; người khác chỉ thấy tên. */
    @Test void assignedOrgHqVisibleOnlyToAdminOwnerAndThatOrg() {
        User owner = user(1, Role.CITIZEN), orgUser = user(10, Role.RESCUE_ORG), otherOrgUser = user(12, Role.RESCUE_ORG);
        RescueOrganization o = org(orgUser, OrgStatus.ACTIVE);
        o.setName("Đội A"); o.setLatitude(16.0611); o.setLongitude(108.2111);
        org(otherOrgUser, OrgStatus.ACTIVE);
        Incident i = sos(owner);
        i.setAssignedOrg(o); i.setStatus(IncidentStatus.ACCEPTED);

        for (User allowed : List.of(owner, orgUser, user(3, Role.ADMIN))) {
            IncidentDto d = mapper.toDto(i, allowed);
            assertEquals(16.0611, d.assignedOrg().latitude());
            assertNotNull(d.assignedOrg().phone());
        }
        for (User other : List.of(user(2, Role.CITIZEN), otherOrgUser)) {
            IncidentDto d = mapper.toDto(i, other);
            assertEquals("Đội A", d.assignedOrg().name());
            assertNull(d.assignedOrg().latitude());
            assertNull(d.assignedOrg().longitude());
            assertNull(d.assignedOrg().phone());
        }
    }
}
