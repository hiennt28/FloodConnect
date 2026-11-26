package vn.floodconnect.campaign.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.campaign.entity.CampaignMedia;
import java.time.Instant;
import java.util.*;

public interface CampaignMediaRepository extends JpaRepository<CampaignMedia, Long> {
    List<CampaignMedia> findByCampaignIdOrderBySortOrderAsc(Long campaignId);
    /** Ảnh của nhiều chiến dịch trong một câu (chuyển cả danh sách sang DTO). */
    List<CampaignMedia> findByCampaignIdInOrderBySortOrderAsc(Collection<Long> campaignIds);
    long countByCampaignId(Long campaignId);
}
