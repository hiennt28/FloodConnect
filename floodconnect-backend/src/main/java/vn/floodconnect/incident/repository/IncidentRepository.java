package vn.floodconnect.incident.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import java.time.Instant;
import java.util.*;

public interface IncidentRepository extends JpaRepository<Incident, Long>, JpaSpecificationExecutor<Incident> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Incident i where i.id = :id")
    Optional<Incident> findByIdForUpdate(@Param("id") Long id);

    Optional<Incident> findByCode(String code);
    List<Incident> findByCitizenIdOrderByCreatedAtDesc(Long citizenId);
    boolean existsByCitizenIdAndStatusIn(Long citizenId, Collection<IncidentStatus> st);
    List<Incident> findByAssignedOrgIdAndStatusIn(Long orgId, Collection<IncidentStatus> st);
    /** Các ca đang nhận của nhiều tổ chức trong một câu (AI Matching chấm cả danh sách tổ chức đề xuất). */
    List<Incident> findByAssignedOrgIdInAndStatusIn(Collection<Long> orgIds, Collection<IncidentStatus> st);
    List<Incident> findByAssignedOrgIdOrderByCreatedAtDesc(Long orgId);
    List<Incident> findByStatusIn(Collection<IncidentStatus> st);
    long countByAssignedOrgIdAndStatusIn(Long orgId, Collection<IncidentStatus> st);

    /** Ca có trạng thái thuộc st, tạo từ since trở đi, cách điểm (lat, lng) không quá radiusM mét. */
    default List<Incident> findWithinRadius(Collection<IncidentStatus> st, Instant since, double lat, double lng, double radiusM) {
        return findWithinRadiusNative(st.stream().map(Enum::name).toList(), since, lat, lng, radiusM);
    }

    /**
     * Lọc bằng PostGIS: ST_DWithin trên cột geog (kiểu geography, PostgreSQL tự tính từ latitude/longitude, có chỉ mục GiST;
     * tạo bởi Flyway, db/migration/V1__baseline_schema.sql). ST_MakePoint nhận kinh độ trước, vĩ độ sau. Truy vấn SQL thuần nên trạng thái truyền dạng chuỗi.
     */
    @Query(value = "select * from incidents i where i.status in (:st) and i.created_at >= :since "
            + "and ST_DWithin(i.geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), :radius)", nativeQuery = true)
    List<Incident> findWithinRadiusNative(@Param("st") Collection<String> st, @Param("since") Instant since,
                                          @Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radiusM);

    /*
     * Các truy vấn dưới chỉ trả id (xếp tăng dần để luôn khóa theo cùng một thứ tự). Nơi gọi khóa từng ca bằng findByIdForUpdate
     * rồi kiểm tra lại điều kiện: nếu đọc sẵn cả ca không khóa, Hibernate sẽ ghi lại mọi cột từ bản đọc cũ và xóa mất thao tác
     * (nhận ca, xác nhận, hoàn thành, điều phối lại) vừa được commit trong lúc đó.
     */
    @Query("select i.id from Incident i where i.status = :st and i.pendingTimeoutNotified = false and i.createdAt <= :before order by i.id")
    List<Long> findPendingTimedOutIds(@Param("st") IncidentStatus st, @Param("before") Instant before);

    @Query("select i.id from Incident i where i.status = :st and i.assignTimeoutNotified = false and i.assignedAt <= :before order by i.id")
    List<Long> findAssignedTimedOutIds(@Param("st") IncidentStatus st, @Param("before") Instant before);

    /** Ca Đã phân công đã bị đánh dấu quá hạn phản hồi (TIMEOUT) và được giao từ mốc before trở về trước: tự trả về Chờ xử lý. */
    @Query("select i.id from Incident i where i.status = :st and i.assignTimeoutNotified = true and i.assignedAt <= :before order by i.id")
    List<Long> findAssignReleaseIds(@Param("st") IncidentStatus st, @Param("before") Instant before);

    @Query("select i.id from Incident i where i.status = :st and i.acceptedTimeoutNotified = false and i.acceptedAt <= :before order by i.id")
    List<Long> findAcceptedTooLongIds(@Param("st") IncidentStatus st, @Param("before") Instant before);

    @Query("select i.id from Incident i where i.assignedOrg.id = :orgId and i.status in :st order by i.id")
    List<Long> findIdsByAssignedOrgIdAndStatusIn(@Param("orgId") Long orgId, @Param("st") Collection<IncidentStatus> st);

    @Query("select i.dangerLevel, count(i) from Incident i where i.createdAt >= :from and i.createdAt < :to group by i.dangerLevel")
    List<Object[]> countByLevelBetween(@Param("from") Instant from, @Param("to") Instant to);

    @Query("select i.status, count(i) from Incident i where i.createdAt >= :from and i.createdAt < :to group by i.status")
    List<Object[]> countByStatusBetween(@Param("from") Instant from, @Param("to") Instant to);

    long countByStatusIn(Collection<IncidentStatus> st);
    long countByStatus(IncidentStatus st);

    // ---- Thống kê theo ngày (giờ Việt Nam). Cột là timestamptz nên "AT TIME ZONE" đổi đúng sang giờ địa phương trước khi lấy ngày.
    /** [ngày, số ca được gửi] cho các ngày có ca, từ :from. */
    @Query(value = "select cast(created_at at time zone 'Asia/Ho_Chi_Minh' as date), count(*) from incidents "
            + "where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countCreatedByDay(@Param("from") Instant from);

    /** [ngày, số ca hoàn thành] tính theo ngày hoàn thành (không theo ngày gửi), từ :from. */
    @Query(value = "select cast(completed_at at time zone 'Asia/Ho_Chi_Minh' as date), count(*) from incidents "
            + "where status = 'COMPLETED' and completed_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countCompletedByDay(@Param("from") Instant from);

    /** [số phút trung bình, số ca]: từ lúc gửi SOS đến lần phân công đầu tiên (Admin chỉ định hoặc tổ chức tự nhận). */
    @Query(value = "select avg(extract(epoch from (a.first_at - i.created_at))) / 60, count(*) from incidents i "
            + "join (select incident_id, min(created_at) as first_at from incident_assignments group by incident_id) a on a.incident_id = i.id "
            + "where i.created_at >= :from", nativeQuery = true)
    List<Object[]> avgMinutesUntilFirstAssignment(@Param("from") Instant from);

    /** [số phút trung bình, số ca]: từ lúc gửi SOS đến khi hoàn thành, chỉ ca đã hoàn thành. */
    @Query(value = "select avg(extract(epoch from (completed_at - created_at))) / 60, count(*) from incidents "
            + "where status = 'COMPLETED' and completed_at is not null and created_at >= :from", nativeQuery = true)
    List<Object[]> avgMinutesUntilCompleted(@Param("from") Instant from);
}
