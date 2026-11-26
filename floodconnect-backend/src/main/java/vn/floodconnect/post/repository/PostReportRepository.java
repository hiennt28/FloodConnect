package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.post.entity.PostReport;
import java.time.Instant;
import java.util.*;

public interface PostReportRepository extends JpaRepository<PostReport, Long> {
    boolean existsByPostIdAndReporterId(Long postId, Long reporterId);
    List<PostReport> findByPostIdOrderByCreatedAtDesc(Long postId);
    List<PostReport> findByPostIdAndStatus(Long postId, ReportStatus st);
    long countByPostIdAndStatus(Long postId, ReportStatus st);
    Page<PostReport> findAllByOrderByCreatedAtDesc(Pageable p);

    /** [postId, số báo cáo đang chờ] mỗi bài, nhiều báo cáo trước (cùng số thì bài có id nhỏ trước, để các trang không trùng / sót bài).
     *  Câu đếm viết riêng: câu Spring tự sinh từ truy vấn GROUP BY trả nhiều dòng thay vì số nhóm. */
    @Query(value = "select r.post.id, count(r) from PostReport r where r.status = :st group by r.post.id order by count(r) desc, r.post.id",
            countQuery = "select count(distinct r.post.id) from PostReport r where r.status = :st")
    Page<Object[]> pendingCountsByPost(@Param("st") ReportStatus st, Pageable p);
    @Query("select count(distinct r.post.id) from PostReport r where r.status = :st")
    long countReportedPosts(@Param("st") ReportStatus st);

    @Modifying
    @Query("update PostReport r set r.status = :done where r.post.id = :pid and r.status = :pending")
    int completeAll(@Param("pid") Long postId, @Param("pending") ReportStatus pending, @Param("done") ReportStatus done);
}
