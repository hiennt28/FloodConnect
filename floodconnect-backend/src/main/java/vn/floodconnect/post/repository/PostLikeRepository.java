package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.post.entity.PostLike;
import java.time.Instant;
import java.util.*;

public interface PostLikeRepository extends JpaRepository<PostLike, PostLike.Key> {
    boolean existsByPostIdAndUserId(Long postId, Long userId);
    long countByPostId(Long postId);
    /** [postId, số lượt thích] của nhiều bài; bài chưa có lượt thích không có dòng. */
    @Query("select l.postId, count(l) from PostLike l where l.postId in :ids group by l.postId")
    List<Object[]> countByPostIds(@Param("ids") Collection<Long> postIds);
    /** Trong các bài này, những bài người dùng đã thích. */
    @Query("select l.postId from PostLike l where l.userId = :uid and l.postId in :ids")
    List<Long> findLikedPostIds(@Param("uid") Long userId, @Param("ids") Collection<Long> postIds);
    void deleteByPostIdAndUserId(Long postId, Long userId);
}
