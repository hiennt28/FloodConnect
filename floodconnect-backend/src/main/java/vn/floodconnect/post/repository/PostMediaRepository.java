package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.post.entity.PostMedia;
import java.time.Instant;
import java.util.*;

public interface PostMediaRepository extends JpaRepository<PostMedia, Long> {
    List<PostMedia> findByPostIdOrderBySortOrderAsc(Long postId);
    /** Ảnh của nhiều bài trong một câu (chuyển cả trang bảng tin sang DTO). */
    List<PostMedia> findByPostIdInOrderBySortOrderAsc(Collection<Long> postIds);
    long countByPostId(Long postId);
}
