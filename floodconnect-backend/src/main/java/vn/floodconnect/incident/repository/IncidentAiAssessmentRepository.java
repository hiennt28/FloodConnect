package vn.floodconnect.incident.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.incident.entity.IncidentAiAssessment;
import java.time.Instant;
import java.util.*;

public interface IncidentAiAssessmentRepository extends JpaRepository<IncidentAiAssessment, Long> {
    Optional<IncidentAiAssessment> findFirstByIncidentIdOrderByCreatedAtDesc(Long incidentId);

    /** Đánh giá AI của nhiều ca trong một truy vấn, mới nhất trước (bản đầu tiên của mỗi ca là bản mới nhất). */
    @Query("select a from IncidentAiAssessment a where a.incident.id in :ids order by a.createdAt desc, a.id desc")
    List<IncidentAiAssessment> findByIncidentIdsLatestFirst(@Param("ids") Collection<Long> incidentIds);
}
