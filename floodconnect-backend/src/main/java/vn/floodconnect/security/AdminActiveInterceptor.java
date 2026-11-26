package vn.floodconnect.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import vn.floodconnect.auth.service.AuthService;
import vn.floodconnect.user.repository.UserRepository;

/**
 * Chạy trước mọi API /api/admin/** (đăng ký ở WebConfig). Access token (JWT, 30 phút) chỉ ghi vai trò ADMIN, không biết tài khoản
 * vừa bị Admin gốc vô hiệu hóa; nên mỗi yêu cầu đọc lại trạng thái tài khoản trong CSDL và từ chối ngay (403 ADMIN_DISABLED),
 * thay vì để người bị vô hiệu hóa dùng tiếp tới khi token hết hạn. SecurityConfig đã bảo đảm người gọi có vai trò ADMIN.
 * Lỗi ném ra ở đây vẫn được GlobalExceptionHandler chuyển thành JSON như mọi lỗi khác.
 */
@Component
@RequiredArgsConstructor
public class AdminActiveInterceptor implements HandlerInterceptor {
    private final CurrentUser current;
    private final UserRepository users;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        Long id = current.idOrNull();
        if (id != null && users.findById(id).map(AuthService::adminDisabled).orElse(false)) throw AuthService.adminDisabledError();
        return true;
    }
}
