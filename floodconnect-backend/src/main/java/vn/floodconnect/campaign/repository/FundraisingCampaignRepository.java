package vn.floodconnect.campaign.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import java.time.Instant;
import java.util.*;

public interface FundraisingCampaignRepository extends JpaRepository<FundraisingCampaign, Long>, JpaSpecificationExecutor<FundraisingCampaign> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from FundraisingCampaign c where c.id = :id")
    Optional<FundraisingCampaign> findByIdForUpdate(@Param("id") Long id);

    List<FundraisingCampaign> findByOrgIdOrderByCreatedAtDesc(Long orgId);
    long countByStatus(CampaignStatus st);
    List<FundraisingCampaign> findByOrgIdAndStatusIn(Long orgId, Collection<CampaignStatus> st);
    /*
     * Tác vụ định kỳ chỉ lấy id (xếp tăng dần để luôn khóa theo cùng thứ tự), rồi khóa và kiểm tra lại từng chiến dịch trong
     * transaction riêng: tránh ghi đè số tiền vừa được cộng, và một chiến dịch lỗi không làm hỏng cả lượt chạy.
     */
    @Query("select c.id from FundraisingCampaign c where c.status = :st and c.startDate <= :t order by c.id")
    List<Long> findIdsToStart(@Param("st") CampaignStatus st, @Param("t") Instant t);

    @Query("select c.id from FundraisingCampaign c where c.status = :st and c.endDate <= :t order by c.id")
    List<Long> findIdsToEnd(@Param("st") CampaignStatus st, @Param("t") Instant t);
}
