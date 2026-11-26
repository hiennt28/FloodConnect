package vn.floodconnect.user.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.List;

/**
 * Admin không tự đăng ký: lần chạy đầu (DB chưa có Admin) hệ thống tạo tài khoản Admin GỐC từ ADMIN_EMAIL, ADMIN_PHONE, ADMIN_PASSWORD.
 * Các Admin khác do Admin gốc tạo ở trang Quản lý Admin (AdminUserService).
 *
 * - DB chưa có Admin: bắt buộc đặt ADMIN_PASSWORD trong .env (đúng quy tắc mật khẩu, khác mật khẩu mẫu cũ "Admin12345"),
 *   nếu không ứng dụng không khởi động. Trước đây mặc định là "Admin12345", ghi công khai trong mã nguồn và tài liệu,
 *   nên ai cũng đăng nhập được Admin của bản cài đặt nào quên đổi.
 * - DB đã có Admin: không đổi gì (đổi ADMIN_PASSWORD sau lần chạy đầu KHÔNG đổi mật khẩu Admin đã có; muốn đổi thì vào
 *   Trang cá nhân → Đổi mật khẩu). Chỉ ghi cảnh báo vào log mỗi lần khởi động nếu Admin vẫn dùng mật khẩu mẫu cũ.
 */
@Component
@Order(1)
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements CommandLineRunner {
    /** Mật khẩu mặc định cũ, đã công khai: không bao giờ được dùng làm mật khẩu Admin. */
    static final String OLD_DEFAULT_PASSWORD = "Admin12345";

    private final UserRepository users;
    private final PasswordEncoder encoder;
    @Value("${app.admin.email}") private String email;
    @Value("${app.admin.phone}") private String phone;
    @Value("${app.admin.password:}") private String password;

    @Override
    public void run(String... args) {
        List<User> admins = users.findByRole(Role.ADMIN);
        if (!admins.isEmpty()) {
            // Luôn có đúng một Admin gốc (Flyway V11 gắn cho Admin có sẵn). Phòng dữ liệu nhập tay thiếu: gắn cho Admin tạo sớm nhất
            if (admins.stream().noneMatch(User::isRootAdmin)) {
                User first = admins.stream().min(java.util.Comparator.comparing(User::getId)).orElseThrow();
                first.setRootAdmin(true);
                users.save(first);
                log.warn("Chưa có Admin gốc: đã gắn quyền Admin gốc cho tài khoản {}.", first.getEmail());
            }
            for (User a : admins)
                if (encoder.matches(OLD_DEFAULT_PASSWORD, a.getPasswordHash()))
                    log.warn("Tài khoản Admin {} vẫn dùng mật khẩu mặc định cũ \"{}\" (đã công khai trong tài liệu). "
                            + "Hãy đăng nhập và đổi mật khẩu ngay tại Trang cá nhân → Đổi mật khẩu.", a.getEmail(), OLD_DEFAULT_PASSWORD);
            return;
        }
        String pw = password == null ? "" : password;
        if (pw.isBlank() || OLD_DEFAULT_PASSWORD.equals(pw) || !Validators.passwordOk(pw))
            throw new IllegalStateException("Chưa có tài khoản Admin và ADMIN_PASSWORD chưa hợp lệ (trống, là mật khẩu mẫu cũ \""
                    + OLD_DEFAULT_PASSWORD + "\" hoặc không đúng quy tắc: " + Validators.PASSWORD_MSG + "). "
                    + "Hãy đặt ADMIN_PASSWORD trong file .env rồi chạy lại; đó sẽ là mật khẩu đăng nhập Admin lần đầu.");
        User a = new User();
        a.setFullName("Quản trị viên"); a.setEmail(email.trim().toLowerCase()); a.setPhone(phone);
        a.setPasswordHash(encoder.encode(pw)); a.setRole(Role.ADMIN); a.setEmailVerified(true);
        a.setRootAdmin(true); // Admin đầu tiên là Admin gốc: chỉ người này tạo / vô hiệu hóa được Admin khác
        users.save(a);
        log.info("Đã tạo tài khoản Admin gốc {} với mật khẩu lấy từ ADMIN_PASSWORD.", a.getEmail());
    }
}
