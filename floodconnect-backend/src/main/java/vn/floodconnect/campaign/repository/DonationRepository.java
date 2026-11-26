package vn.floodconnect.campaign.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.DonationStatus;
import java.time.Instant;
import java.util.*;

public interface DonationRepository extends JpaRepository<Donation, Long> {
    Page<Donation> findByCampaignIdAndStatusOrderByCreatedAtDesc(Long campaignId, DonationStatus st, Pageable p);
    List<Donation> findByDonorIdOrderByCreatedAtDesc(Long donorId);
    Optional<Donation> findByTransactionCode(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.transactionCode = :code")
    Optional<Donation> findByTransactionCodeForUpdate(@Param("code") String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from Donation d where d.id = :id")
    Optional<Donation> findByIdForUpdate(@Param("id") Long id);

    /** Chỉ lấy id: tác vụ hết hạn khóa từng giao dịch rồi kiểm tra lại (cổng có thể vừa báo kết quả). */
    @Query("select d.id from Donation d where d.status = :st and d.createdAt <= :before order by d.id")
    List<Long> findIdsByStatusCreatedBefore(@Param("st") DonationStatus st, @Param("before") Instant before);

    @Query("select o.id, o.name, sum(d.amount) from Donation d join d.campaign c join c.org o "
            + "where d.status = :st group by o.id, o.name order by sum(d.amount) desc")
    List<Object[]> topOrgs(@Param("st") DonationStatus st, Pageable p);

    @Query("select u.id, u.fullName, sum(d.amount) from Donation d join d.donor u "
            + "where d.status = :st group by u.id, u.fullName order by sum(d.amount) desc")
    List<Object[]> topDonors(@Param("st") DonationStatus st, Pageable p);

    @Query("select coalesce(sum(d.amount), 0) from Donation d where d.status = :st")
    long sumAmountByStatus(@Param("st") DonationStatus st);
}
