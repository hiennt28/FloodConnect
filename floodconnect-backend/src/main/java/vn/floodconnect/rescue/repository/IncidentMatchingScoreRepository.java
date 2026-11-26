package vn.floodconnect.rescue.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.rescue.entity.IncidentMatchingScore;
import java.time.Instant;
import java.util.*;

public interface IncidentMatchingScoreRepository extends JpaRepository<IncidentMatchingScore, Long> {
    Optional<IncidentMatchingScore> findByIncidentIdAndOrgId(Long incidentId, Long orgId);
    List<IncidentMatchingScore> findByIncidentId(Long incidentId);
}
