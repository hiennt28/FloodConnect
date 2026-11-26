package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.post.entity.Comment;
import vn.floodconnect.post.entity.ContentStatus;
import java.time.Instant;
import java.util.*;

public interface CommentRepository extends JpaRepository<Comment, Long> {
    /** Bình luận cấp 1: còn hiển thị, hoặc đã xóa nhưng vẫn còn phản hồi hiển thị. */
    @Query("select c from Comment c where c.post.id = :pid and c.parentComment is null and "
            + "(c.status = :vis or exists (select 1 from Comment r where r.parentComment = c and r.status = :vis)) "
            + "order by c.createdAt desc, c.id desc")
    Page<Comment> findTopLevel(@Param("pid") Long postId, @Param("vis") ContentStatus vis, Pageable p);

    Page<Comment> findByParentCommentIdAndStatusOrderByCreatedAtAsc(Long parentId, ContentStatus st, Pageable p);
    long countByPostIdAndStatus(Long postId, ContentStatus st);
    long countByParentCommentIdAndStatus(Long parentId, ContentStatus st);
    /** [postId, số bình luận (kể cả trả lời) có trạng thái st] của nhiều bài, cùng cách đếm với countByPostIdAndStatus. */
    @Query("select c.post.id, count(c) from Comment c where c.post.id in :ids and c.status = :st group by c.post.id")
    List<Object[]> countByPostIds(@Param("ids") Collection<Long> postIds, @Param("st") ContentStatus st);
    /** [id bình luận gốc, số trả lời có trạng thái st] của nhiều bình luận gốc. */
    @Query("select c.parentComment.id, count(c) from Comment c where c.parentComment.id in :ids and c.status = :st group by c.parentComment.id")
    List<Object[]> countRepliesByParentIds(@Param("ids") Collection<Long> parentIds, @Param("st") ContentStatus st);
}
