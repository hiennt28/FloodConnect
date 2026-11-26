package vn.floodconnect.organization.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.organization.entity.OrganizationReview;
import java.time.Instant;
import java.util.*;

public interface OrganizationReviewRepository extends JpaRepository<OrganizationReview, Long> {
    List<OrganizationReview> findByOrgIdOrderByCreatedAtDesc(Long orgId);
}
