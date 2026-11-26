package vn.floodconnect.user.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import java.time.Instant;
import java.util.*;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByPhone(String phone);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByPhone(String phone);
    boolean existsByRole(Role role);
    List<User> findByRole(Role role);
    List<User> findByStatusAndLockedUntilBefore(UserStatus status, Instant t);
    long countByRoleAndStatus(Role role, UserStatus status);

    /**
     * Tìm người dân theo tên, KHÔNG phân biệt dấu và hoa/thường ("nguyen van an" ra "Nguyễn Văn An"). Bỏ tài khoản bị khóa và
     * tài khoản chưa xác thực email (đăng ký bỏ dở, sẽ bị xóa sau 24 giờ).
     * @param q từ khóa đã chuẩn hóa và thoát ký tự đặc biệt của LIKE (UserService.searchKeyword)
     */
    default Page<User> searchByName(Role role, UserStatus locked, String q, Pageable p) {
        return searchByNameNative(role.name(), locked.name(), q, p);
    }

    /** unaccent (PostgreSQL, Flyway V5) bỏ dấu cả hai phía rồi mới so sánh; SQL thuần nên vai trò/trạng thái truyền dạng chuỗi. */
    @Query(value = "select * from users u where u.role = :role and u.status <> :locked and u.email_verified = true "
            + "and unaccent(lower(u.full_name)) like '%' || unaccent(lower(:q)) || '%' escape '\\' order by u.full_name",
            countQuery = "select count(*) from users u where u.role = :role and u.status <> :locked and u.email_verified = true "
            + "and unaccent(lower(u.full_name)) like '%' || unaccent(lower(:q)) || '%' escape '\\'",
            nativeQuery = true)
    Page<User> searchByNameNative(@Param("role") String role, @Param("locked") String locked, @Param("q") String q, Pageable p);

    /**
     * Admin: mọi người dân (kể cả bị khóa, chưa xác thực), lọc theo tên (không phân biệt dấu, hoa/thường), email hoặc số điện thoại;
     * xếp theo tên, phân trang. q rỗng = tất cả. q đã chuẩn hóa và thoát ký tự LIKE (SearchKeyword.like).
     */
    @Query(value = "select * from users u where u.role = 'CITIZEN' and (:q = '' "
            + "or unaccent(lower(u.full_name)) like '%' || unaccent(lower(:q)) || '%' escape '\\' "
            + "or lower(u.email) like '%' || lower(:q) || '%' escape '\\' or u.phone like '%' || :q || '%' escape '\\') "
            + "order by u.full_name, u.id",
            countQuery = "select count(*) from users u where u.role = 'CITIZEN' and (:q = '' "
            + "or unaccent(lower(u.full_name)) like '%' || unaccent(lower(:q)) || '%' escape '\\' "
            + "or lower(u.email) like '%' || lower(:q) || '%' escape '\\' or u.phone like '%' || :q || '%' escape '\\')",
            nativeQuery = true)
    Page<User> adminSearchCitizens(@Param("q") String q, Pageable p);

    @Query("select u from User u where u.role = :role and u.status = :status order by u.lockedUntil asc")
    List<User> findLocked(@Param("role") Role role, @Param("status") UserStatus status);
}
