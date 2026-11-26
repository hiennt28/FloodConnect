package vn.floodconnect.incident.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.incident.dto.IncidentDtos.IncidentDto;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentAiAssessmentRepository;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgMapper;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import vn.floodconnect.rescue.repository.IncidentAssignmentRepository;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.service.UserMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Chuyển danh sách ca SOS không bị N+1: dữ liệu ngoài bảng incidents được tra một lần cho cả danh sách, số truy vấn không
 * tăng theo số ca; kết quả của từng ca vẫn đúng (đánh giá AI mới nhất, lần phân công gần nhất, tên tổ chức của người gửi).
 */
class IncidentMapperBatchTest {
    private final IncidentAiAssessmentRepository assessments = mock(IncidentAiAssessmentRepository.class);
    private final IncidentAssignmentRepository assignments = mock(IncidentAssignmentRepository.class);
    private final RescueOrganizationRepository orgs = mock(RescueOrganizationRepository.class);
    private final IncidentMapper mapper = new IncidentMapper(assessments, assignments, orgs,
            new UserMapper(orgs, mock(FollowRepository.class), mock(OrgMapper.class)), new ObjectMapper());

    private final User viewer = user(50L, Role.RESCUE_ORG);
    private final User orgSender = user(60L, Role.RESCUE_ORG); // tài khoản tổ chức cũng có thể gửi SOS
    private Incident c1, c2, c3;

    private static User user(long id, Role role) {
        User u = new User();
        u.setId(id); u.setRole(role); u.setFullName("U" + id);
        return u;
    }

    private static Incident sos(long id, User citizen) {
        Incident i = new Incident();
        i.setId(id); i.setCode("SOS-" + id); i.setCitizen(citizen); i.setStatus(IncidentStatus.PENDING); i.setDangerLevel(DangerLevel.CAO);
        i.setDescription("Ngập"); i.setLatitude(16.05); i.setLongitude(108.2); i.setCreatedAt(Instant.now());
        return i;
    }

    private static IncidentAiAssessment ai(Incident i, String reasons) {
        IncidentAiAssessment a = new IncidentAiAssessment();
        a.setIncident(i); a.setReasons(reasons);
        return a;
    }

    private static IncidentAssignment asg(Incident i, AssignmentStatus st) {
        IncidentAssignment a = new IncidentAssignment();
        a.setIncident(i); a.setStatus(st);
        return a;
    }

    @BeforeEach void setUp() {
        RescueOrganization mine = new RescueOrganization();
        mine.setUser(viewer); mine.setStatus(OrgStatus.ACTIVE);
        when(orgs.findByUserId(50L)).thenReturn(Optional.of(mine));
        RescueOrganization senderOrg = new RescueOrganization();
        senderOrg.setUser(orgSender); senderOrg.setName("Đội B");
        when(orgs.findByUserIdIn(any())).thenReturn(List.of(senderOrg));

        c1 = sos(1L, user(10L, Role.CITIZEN)); c2 = sos(2L, orgSender); c3 = sos(3L, user(11L, Role.CITIZEN));
        // truy vấn trả mới nhất trước: mỗi ca lấy bản đầu tiên
        when(assessments.findByIncidentIdsLatestFirst(any())).thenReturn(List.of(ai(c1, "Mới"), ai(c2, "Ca 2"), ai(c1, "Cũ")));
        when(assignments.findByIncidentIdsLatestFirst(any())).thenReturn(List.of(asg(c1, AssignmentStatus.TIMEOUT), asg(c1, AssignmentStatus.WAITING_CONFIRM)));
    }

    @Test void listIsMappedWithAFixedNumberOfQueriesWhateverItsSize() {
        List<IncidentDto> out = mapper.toDtos(List.of(c1, c2, c3), viewer);

        assertEquals(3, out.size());
        verify(orgs, times(1)).findByUserId(50L);                    // tổ chức của người xem: một lần, không phải mỗi ca
        verify(assessments, times(1)).findByIncidentIdsLatestFirst(List.of(1L, 2L, 3L));
        verify(assignments, times(1)).findByIncidentIdsLatestFirst(List.of(1L, 2L, 3L));
        verify(orgs, times(1)).findByUserIdIn(List.of(60L));         // chỉ tra tên tổ chức của người gửi là tài khoản tổ chức
        verify(assessments, never()).findFirstByIncidentIdOrderByCreatedAtDesc(anyLong());
        verify(assignments, never()).findFirstByIncidentIdOrderByCreatedAtDesc(anyLong());
    }

    @Test void eachCaseGetsItsOwnLatestDataFromTheBatch() {
        List<IncidentDto> out = mapper.toDtos(List.of(c1, c2, c3), viewer);

        assertEquals(List.of("Mới"), out.get(0).aiReasons());       // bản mới nhất, không phải bản cũ
        assertEquals("TIMEOUT", out.get(0).assignmentStatus());     // lần phân công gần nhất
        assertEquals(List.of("Ca 2"), out.get(1).aiReasons());
        assertEquals("Đội B", out.get(1).citizen().organizationName());
        assertNull(out.get(1).assignmentStatus());
        assertEquals(List.of(), out.get(2).aiReasons());             // ca chưa có đánh giá AI
        assertNull(out.get(2).citizen().organizationName());
    }

    /** Lý do lần phân công gần nhất (từ chối, hủy phân công, tổ chức bị khóa...) chỉ Admin nhận: lý do từ chối là thông tin nội bộ. */
    @Test void lastAssignmentReasonIsOnlyForAdmins() {
        IncidentAssignment revoked = asg(c1, AssignmentStatus.CANCELLED);
        revoked.setRejectReason("Admin hủy phân công: Đội không có xuồng");
        when(assignments.findByIncidentIdsLatestFirst(any())).thenReturn(List.of(revoked));

        IncidentDto forAdmin = mapper.toDto(c1, user(1L, Role.ADMIN));
        assertEquals("CANCELLED", forAdmin.assignmentStatus());
        assertEquals("Admin hủy phân công: Đội không có xuồng", forAdmin.assignmentNote());

        assertNull(mapper.toDto(c1, viewer).assignmentNote()); // tổ chức đang hoạt động: không thấy
        assertNull(mapper.toDto(c1, c1.getCitizen()).assignmentNote()); // chủ ca: không thấy
    }

    @Test void singleCaseGoesThroughTheSamePathAsTheList() {
        IncidentDto one = mapper.toDto(c1, viewer);
        IncidentDto fromList = mapper.toDtos(List.of(c1), viewer).get(0);
        assertEquals(fromList, one);
    }

    @Test void emptyListRunsNoQuery() {
        assertEquals(List.of(), mapper.toDtos(List.of(), viewer));
        verifyNoInteractions(assessments, assignments, orgs);
    }
}
