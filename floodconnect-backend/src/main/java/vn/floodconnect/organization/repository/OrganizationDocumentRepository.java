package vn.floodconnect.organization.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.organization.entity.OrganizationDocument;
import java.time.Instant;
import java.util.*;

public interface OrganizationDocumentRepository extends JpaRepository<OrganizationDocument, Long> {
    List<OrganizationDocument> findByOrgIdOrderByIdAsc(Long orgId);
    void deleteByOrgId(Long orgId);
}
