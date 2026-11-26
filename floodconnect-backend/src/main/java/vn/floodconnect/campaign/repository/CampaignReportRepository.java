package vn.floodconnect.campaign.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.campaign.entity.CampaignReport;
import vn.floodconnect.common.enums.ReportStatus;
import java.time.Instant;
import java.util.*;

public interface CampaignReportRepository extends JpaRepository<CampaignReport, Long> {
    boolean existsByCampaignIdAndReporterId(Long campaignId, Long reporterId);
    /** Trong các chiến dịch này, những chiến dịch người dùng đã báo cáo. */
    @Query("select r.campaign.id from CampaignReport r where r.reporter.id = :uid and r.campaign.id in :ids")
    List<Long> findReportedCampaignIds(@Param("uid") Long reporterId, @Param("ids") Collection<Long> campaignIds);
    List<CampaignReport> findByCampaignIdAndStatus(Long campaignId, ReportStatus st);
    long countByCampaignIdAndStatus(Long campaignId, ReportStatus st);
    Page<CampaignReport> findAllByOrderByCreatedAtDesc(Pageable p);

    /** [campaignId, số báo cáo đang chờ] mỗi chiến dịch; câu đếm viết riêng như PostReportRepository.pendingCountsByPost. */
    @Query(value = "select r.campaign.id, count(r) from CampaignReport r where r.status = :st group by r.campaign.id order by count(r) desc, r.campaign.id",
            countQuery = "select count(distinct r.campaign.id) from CampaignReport r where r.status = :st")
    Page<Object[]> pendingCountsByCampaign(@Param("st") ReportStatus st, Pageable p);
    @Query("select count(distinct r.campaign.id) from CampaignReport r where r.status = :st")
    long countReportedCampaigns(@Param("st") ReportStatus st);

    @Modifying
    @Query("update CampaignReport r set r.status = :done where r.campaign.id = :cid and r.status = :pending")
    int completeAll(@Param("cid") Long campaignId, @Param("pending") ReportStatus pending, @Param("done") ReportStatus done);
}
