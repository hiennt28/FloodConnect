package vn.floodconnect.rescue.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.rescue.entity.AssignmentStatus;
import vn.floodconnect.rescue.entity.IncidentAssignment;
import java.time.Instant;
import java.util.*;

public interface IncidentAssignmentRepository extends JpaRepository<IncidentAssignment, Long> {
    List<IncidentAssignment> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);
    Optional<IncidentAssignment> findFirstByIncidentIdAndOrgIdAndStatusOrderByCreatedAtDesc(Long incidentId, Long orgId, AssignmentStatus st);
    Optional<IncidentAssignment> findFirstByIncidentIdOrderByCreatedAtDesc(Long incidentId);
    boolean existsByIncidentIdAndOrgIdAndStatusIn(Long incidentId, Long orgId, Collection<AssignmentStatus> st);
    /** [orgId, số lần] ở các trạng thái này (vd. từ chối / không phản hồi) của nhiều tổ chức; tổ chức không có lần nào thì không có dòng. */
    @Query("select a.org.id, count(a) from IncidentAssignment a where a.org.id in :ids and a.status in :st group by a.org.id")
    List<Object[]> countByOrgIdsAndStatusIn(@Param("ids") Collection<Long> orgIds, @Param("st") Collection<AssignmentStatus> st);
    /**
     * Như trên nhưng chỉ đếm lần phân công kết thúc (tổ chức trả lời, hoặc hết hạn không phản hồi) từ thời điểm since trở về sau:
     * điểm W của AI Matching chỉ trừ cho lần từ chối / không phản hồi GẦN ĐÂY (cửa sổ app.matching.refusal-window-hours).
     * Lần chưa có responded_at (dữ liệu cũ) tính theo lúc tạo.
     */
    @Query("select a.org.id, count(a) from IncidentAssignment a where a.org.id in :ids and a.status in :st"
            + " and coalesce(a.respondedAt, a.createdAt) >= :since group by a.org.id")
    List<Object[]> countByOrgIdsAndStatusInSince(@Param("ids") Collection<Long> orgIds, @Param("st") Collection<AssignmentStatus> st,
                                                 @Param("since") java.time.Instant since);
    /** Các tổ chức đã có lần phân công ở các trạng thái này với ca (vd. đã từ chối / không phản hồi ca), trong một câu. */
    @Query("select distinct a.org.id from IncidentAssignment a where a.incident.id = :iid and a.status in :st")
    List<Long> findOrgIdsByIncidentIdAndStatusIn(@Param("iid") Long incidentId, @Param("st") Collection<AssignmentStatus> st);

    /** Các lần phân công của nhiều ca trong một truy vấn, mới nhất trước (bản đầu tiên của mỗi ca là lần gần nhất). */
    @Query("select a from IncidentAssignment a where a.incident.id in :ids order by a.createdAt desc, a.id desc")
    List<IncidentAssignment> findByIncidentIdsLatestFirst(@Param("ids") Collection<Long> incidentIds);

    /** Mã các ca mà tổ chức đã có lần phân công ở các trạng thái này (vd. đã từ chối / không phản hồi), trong một truy vấn. */
    @Query("select distinct a.incident.id from IncidentAssignment a where a.org.id = :orgId and a.status in :st")
    List<Long> findIncidentIdsByOrgIdAndStatusIn(@Param("orgId") Long orgId, @Param("st") Collection<AssignmentStatus> st);

    /** [số phút trung bình, số lần]: tổ chức phản hồi lời chỉ định của Admin (nhận hoặc từ chối).
     *  Không tính tổ chức tự nhận (phản hồi ngay), hết hạn không phản hồi (TIMEOUT) và phân công bị hủy. */
    @Query(value = "select avg(extract(epoch from (responded_at - created_at))) / 60, count(*) from incident_assignments "
            + "where type = 'ADMIN_ASSIGN' and status in ('ACCEPTED', 'REJECTED') and responded_at is not null and created_at >= :from",
            nativeQuery = true)
    List<Object[]> avgMinutesOrgResponse(@Param("from") Instant from);
}
