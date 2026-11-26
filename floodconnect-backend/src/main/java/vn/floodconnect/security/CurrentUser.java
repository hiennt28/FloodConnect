package vn.floodconnect.security;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.Format;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;

@Component
@RequiredArgsConstructor
public class CurrentUser {
    private final UserRepository users;

    public Long idOrNull() {
        var a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof Long id)) return null;
        return id;
    }
    public Long id() {
        Long id = idOrNull();
        if (id == null) throw ApiException.unauthorized();
        return id;
    }
    public User get() { return users.findById(id()).orElseThrow(ApiException::unauthorized); }
    public User getOrNull() {
        Long id = idOrNull();
        return id == null ? null : users.findById(id).orElse(null);
    }
    /** Admin gốc (chỉ người này được tạo, vô hiệu hóa, mở lại tài khoản Admin khác). Admin thường và vai trò khác nhận 403. */
    public User requireRootAdmin() {
        User u = get();
        if (u.getRole() != vn.floodconnect.user.entity.Role.ADMIN || !u.isRootAdmin())
            throw ApiException.forbidden("ROOT_ADMIN_ONLY", "Chỉ Admin gốc mới quản lý được tài khoản Admin.");
        return u;
    }

    /** Người dùng không bị khóa (dùng cho đăng bài, bình luận, thả tim, báo cáo...). */
    public User getActive() {
        User u = get();
        if (u.isLocked()) throw ApiException.forbidden("ACCOUNT_LOCKED",
                "Tài khoản của bạn đang bị khóa" + (u.getLockedUntil() != null ? " đến " + Format.dateTime(u.getLockedUntil()) : "") + ", không thể thực hiện thao tác này.");
        return u;
    }
}
