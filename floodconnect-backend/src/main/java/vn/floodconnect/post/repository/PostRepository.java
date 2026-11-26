package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.post.entity.ContentStatus;
import vn.floodconnect.post.entity.Post;
import java.time.Instant;
import java.util.*;

public interface PostRepository extends JpaRepository<Post, Long> {
    Page<Post> findByStatusOrderByCreatedAtDesc(ContentStatus st, Pageable p);
    Page<Post> findByAuthorIdAndStatusOrderByCreatedAtDesc(Long authorId, ContentStatus st, Pageable p);

    @Query("select p from Post p where p.status = :st and p.author.id in "
            + "(select f.followingId from Follow f where f.followerId = :uid) order by p.createdAt desc")
    Page<Post> findFollowing(@Param("uid") Long uid, @Param("st") ContentStatus st, Pageable p);

    long countByAuthorIdAndStatus(Long authorId, ContentStatus st);
}
