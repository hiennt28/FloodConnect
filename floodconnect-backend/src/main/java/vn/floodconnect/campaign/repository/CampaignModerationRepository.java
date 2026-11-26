package vn.floodconnect.campaign.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.campaign.entity.CampaignModeration;
import vn.floodconnect.common.enums.ModerationDecision;
import java.time.Instant;
import java.util.*;

public interface CampaignModerationRepository extends JpaRepository<CampaignModeration, Long> {
    @Query("select count(m) from CampaignModeration m where m.campaign.org.id = :oid and m.decision = :d")
    long countByOrg(@Param("oid") Long orgId, @Param("d") ModerationDecision d);

    /** Gộp cho cả danh sách tổ chức: [mã tổ chức, số chiến dịch bị xử lý]. */
    @Query("select m.campaign.org.id, count(m) from CampaignModeration m where m.campaign.org.id in :oids and m.decision = :d group by m.campaign.org.id")
    List<Object[]> countByOrgs(@Param("oids") Collection<Long> orgIds, @Param("d") ModerationDecision d);

    @Query("select m from CampaignModeration m where m.campaign.org.id = :oid and m.decision = :d order by m.createdAt asc")
    List<CampaignModeration> findByOrg(@Param("oid") Long orgId, @Param("d") ModerationDecision d);
}
