package vn.floodconnect.incident.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.incident.entity.IncidentDangerAdjustment;
import java.time.Instant;
import java.util.*;

public interface IncidentDangerAdjustmentRepository extends JpaRepository<IncidentDangerAdjustment, Long> {
    boolean existsByIncidentId(Long incidentId);
    List<IncidentDangerAdjustment> findByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    /** Trong các ca đã cho, ca nào Admin đã điều chỉnh mức (một truy vấn cho cả danh sách; dùng khi gửi ca lân cận cho AI). */
    @Query("select distinct a.incident.id from IncidentDangerAdjustment a where a.incident.id in :ids")
    List<Long> findAdjustedIncidentIds(@Param("ids") Collection<Long> incidentIds);
}
