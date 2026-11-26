package vn.floodconnect.post.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.post.entity.PostModeration;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import java.time.Instant;
import java.util.*;

public interface PostModerationRepository extends JpaRepository<PostModeration, Long> {
    @Query("select count(m) from PostModeration m where m.post.author.id = :uid and m.decision = :d")
    long countByAuthor(@Param("uid") Long authorUserId, @Param("d") ModerationDecision d);

    @Query("select m from PostModeration m where m.post.author.id = :uid and m.decision = :d order by m.createdAt asc")
    List<PostModeration> findByAuthor(@Param("uid") Long authorUserId, @Param("d") ModerationDecision d);

    @Query("select max(m.createdAt) from PostModeration m where m.post.author.id = :uid and m.decision = :d")
    Instant lastByAuthor(@Param("uid") Long authorUserId, @Param("d") ModerationDecision d);

    /** Gộp cho cả danh sách (tránh 2 truy vấn mỗi người): [mã tác giả, số bài bị xử lý, lần xử lý gần nhất]. */
    @Query("select m.post.author.id, count(m), max(m.createdAt) from PostModeration m "
            + "where m.post.author.id in :uids and m.decision = :d group by m.post.author.id")
    List<Object[]> countByAuthors(@Param("uids") Collection<Long> authorUserIds, @Param("d") ModerationDecision d);
    /** Tác giả (đúng vai trò, đúng trạng thái tài khoản) có ít nhất một bài bị xử lý theo quyết định d: tab "vi phạm" của Admin,
     *  thay vì tải mọi người dùng rồi lọc. */
    @Query("select distinct m.post.author from PostModeration m where m.decision = :d and m.post.author.role = :role and m.post.author.status = :st")
    List<User> findModeratedAuthors(@Param("d") ModerationDecision d, @Param("role") Role role,
                                                         @Param("st") UserStatus st);
}
