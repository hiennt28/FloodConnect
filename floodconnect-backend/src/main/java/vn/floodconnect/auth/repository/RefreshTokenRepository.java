package vn.floodconnect.auth.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.auth.entity.RefreshToken;
import java.time.Instant;
import java.util.*;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String hash);

    @Modifying
    @Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :uid and t.revokedAt is null")
    void revokeAll(@Param("uid") Long userId, @Param("now") Instant now);
}
