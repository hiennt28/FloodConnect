package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.post.entity.Follow;
import java.time.Instant;
import java.util.*;

public interface FollowRepository extends JpaRepository<Follow, Follow.Key> {
    boolean existsByFollowerIdAndFollowingId(Long follower, Long following);
    /** Trong các tài khoản này, những tài khoản follower đang theo dõi. */
    @Query("select f.followingId from Follow f where f.followerId = :follower and f.followingId in :ids")
    List<Long> findFollowingIdsIn(@Param("follower") Long follower, @Param("ids") Collection<Long> ids);
    void deleteByFollowerIdAndFollowingId(Long follower, Long following);
    long countByFollowingId(Long id);
    long countByFollowerId(Long id);
}
