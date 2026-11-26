package vn.floodconnect.notification.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.notification.entity.Notification;
import java.time.Instant;
import java.util.*;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable p);
    long countByUserIdAndReadFalse(Long userId);
    Optional<Notification> findFirstByUserIdAndGroupKeyAndReadFalse(Long userId, String groupKey);

    @Modifying
    @Query("update Notification n set n.read = true where n.user.id = :uid and n.read = false")
    int markAllRead(@Param("uid") Long userId);

    /** Đóng nhóm thông báo (của mọi người nhận) khi việc nó nhắc tới đã được xử lý. */
    @Modifying
    @Query("update Notification n set n.read = true where n.groupKey = :key and n.read = false")
    int markGroupRead(@Param("key") String groupKey);

    @Modifying
    @Query("delete from Notification n where n.user.id = :uid")
    void deleteByUserId(@Param("uid") Long userId);
}
