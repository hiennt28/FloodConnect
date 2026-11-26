package vn.floodconnect.user.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.user.entity.AccountLock;
import java.time.Instant;
import java.util.*;

public interface AccountLockRepository extends JpaRepository<AccountLock, Long> {
    List<AccountLock> findByUserIdAndUnlockedAtIsNull(Long userId);
    List<AccountLock> findByUserIdOrderByLockedAtDesc(Long userId);
}
