package vn.floodconnect.auth.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.auth.entity.AuthToken;
import vn.floodconnect.auth.entity.AuthTokenType;
import java.time.Instant;
import java.util.*;

public interface AuthTokenRepository extends JpaRepository<AuthToken, Long> {
    /**
     * OTP đang chờ xác thực mới nhất (chưa dùng) của một người, dùng để so khớp mã người dùng nhập.
     * Khóa dòng (SELECT ... FOR UPDATE): nhiều lần nhập sai gửi cùng lúc phải xếp hàng, nếu không cả loạt cùng đọc
     * failedAttempts = 0 rồi cùng ghi 1, kẻ dò mã sẽ có nhiều hơn 5 lần thử.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AuthToken> findTopByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(Long userId, AuthTokenType type);
    /** OTP gần nhất đã gửi (kể cả đã dùng/hết hạn), dùng để tính thời gian chờ gửi lại. */
    Optional<AuthToken> findTopByUserIdAndTypeOrderByCreatedAtDesc(Long userId, AuthTokenType type);
    /** Các OTP đã gửi từ mốc since trở đi, cũ nhất trước: giới hạn số mã mỗi giờ và tính lúc được gửi tiếp. */
    List<AuthToken> findByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtAsc(Long userId, AuthTokenType type, Instant since);

    @Modifying
    @Query("delete from AuthToken t where t.user.id = :userId")
    void deleteByUserId(@Param("userId") Long userId);
}
