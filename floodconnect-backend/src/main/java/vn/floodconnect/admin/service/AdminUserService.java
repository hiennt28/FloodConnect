package vn.floodconnect.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.admin.dto.AdminDtos.AdminAccountRow;
import vn.floodconnect.admin.dto.AdminDtos.CreateAdminRequest;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.auth.service.AuthService;
import vn.floodconnect.auth.service.MailService;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.EmailDomainValidator;
import vn.floodconnect.common.util.TokenUtil;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.AccountLock;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.AccountLockRepository;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Quản lý tài khoản Admin. CHỈ Admin gốc dùng được (một tài khoản duy nhất, Flyway V11); Admin thường gọi các hàm này nhận 403.
 *
 * - Tạo Admin: Admin gốc nhập họ tên, email, SĐT; không nhập mật khẩu. Tài khoản được tạo với mật khẩu ngẫu nhiên không ai biết,
 *   chưa xác thực email, và hệ thống gửi mã đặt mật khẩu tới email đó (luồng "Đặt lại mật khẩu"): người được tạo tự đặt mật khẩu,
 *   đồng thời xác thực luôn email. Admin gốc không biết mật khẩu của người khác.
 * - Vô hiệu hóa: bắt buộc lý do, không có thời hạn (chỉ Admin gốc mở lại). Thu hồi mọi phiên đăng nhập; từ đó tài khoản không đăng
 *   nhập, không làm mới phiên được và mọi yêu cầu tới /api/admin/** bị từ chối ngay (AdminActiveInterceptor), không nhận thông báo SOS.
 * - Không ai vô hiệu hóa được Admin gốc, nên hệ thống luôn còn một người quản lý được tài khoản Admin.
 */
@Service
@RequiredArgsConstructor
public class AdminUserService {
    public static final int REASON_MAX = 500;

    private final UserRepository users;
    private final AccountLockRepository locks;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final AuthService auth;
    private final MailService mail;
    private final EmailDomainValidator emailDomainValidator;

    /** Mọi tài khoản Admin: Admin gốc đầu tiên, còn lại theo thời gian tạo. */
    @Transactional(readOnly = true)
    public List<AdminAccountRow> list() {
        current.requireRootAdmin();
        return users.findByRole(Role.ADMIN).stream()
                .sorted(Comparator.comparing((User u) -> !u.isRootAdmin()).thenComparing(User::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(User::getId))
                .map(this::row).toList();
    }

    @Transactional
    public AdminAccountRow create(CreateAdminRequest r) {
        current.requireRootAdmin();
        Map<String, String> e = new LinkedHashMap<>();
        String name = r.fullName() == null ? "" : r.fullName().trim();
        if (name.isEmpty()) e.put("fullName", "Vui lòng nhập họ tên.");
        else if (name.length() > Validators.TEXT_MAX) e.put("fullName", Validators.tooLongMsg("Họ tên", Validators.TEXT_MAX));
        String email = r.email() == null ? "" : r.email().trim().toLowerCase();
        if (!Validators.emailOk(email)) e.put("email", "Email không hợp lệ.");
        else if (users.existsByEmailIgnoreCase(email)) e.put("email", "Email đã được sử dụng.");
        else emailDomainValidator.check(email, e);
        String phone = r.phone() == null ? "" : r.phone().trim();
        if (!Validators.phoneOk(phone)) e.put("phone", Validators.PHONE_MSG);
        else if (users.existsByPhone(phone)) e.put("phone", "Số điện thoại đã được sử dụng.");
        if (!e.isEmpty()) throw ApiException.validation(e);

        User u = new User();
        u.setFullName(name); u.setEmail(email); u.setPhone(phone); u.setRole(Role.ADMIN);
        u.setEmailVerified(false); // xác thực khi người đó đặt mật khẩu bằng mã gửi qua email
        u.setPasswordHash(encoder.encode(TokenUtil.random())); // mật khẩu ngẫu nhiên không ai biết, chỉ dùng được sau khi tự đặt lại
        users.save(u);
        auth.sendAdminInvite(u);
        return row(u);
    }

    /** Gửi lại mã đặt mật khẩu cho Admin chưa kích hoạt (mã cũ hết hạn). Cùng giới hạn gửi mã: chờ 60 giây, tối đa 5 mã/giờ. */
    @Transactional
    public AdminAccountRow resendInvite(Long id) {
        current.requireRootAdmin();
        User u = admin(id);
        if (u.isEmailVerified())
            throw ApiException.conflict("ALREADY_ACTIVATED", "Tài khoản này đã đặt mật khẩu và đang hoạt động, không cần gửi lại mã.");
        auth.sendAdminInvite(u);
        return row(u);
    }

    @Transactional
    public AdminAccountRow disable(Long id, String reason) {
        User root = current.requireRootAdmin();
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty()) throw ApiException.bad("REASON_REQUIRED", "Vui lòng nhập lý do vô hiệu hóa.");
        if (r.length() > REASON_MAX) throw ApiException.bad("REASON_TOO_LONG", "Lý do tối đa " + REASON_MAX + " ký tự.");
        User u = admin(id);
        if (u.isRootAdmin()) throw ApiException.bad("CANNOT_DISABLE_ROOT", "Không thể vô hiệu hóa Admin gốc.");
        if (u.isLocked()) throw ApiException.conflict("ALREADY_DISABLED", "Tài khoản Admin này đã bị vô hiệu hóa.");
        Instant now = Instant.now();
        u.setStatus(UserStatus.LOCKED);
        u.setLockedUntil(null); // không có thời hạn: chỉ Admin gốc mở lại (tác vụ tự mở khóa chỉ xét tài khoản có hạn)
        AccountLock l = new AccountLock();
        l.setUser(u); l.setLockedBy(root); l.setReason(r); l.setDurationDays(0); l.setLockedAt(now); l.setLockedUntil(null);
        locks.save(l);
        refreshTokens.revokeAll(u.getId(), now); // đăng xuất khỏi mọi thiết bị
        mail.send(u.getEmail(), "FloodConnect - Tài khoản Quản trị viên bị vô hiệu hóa",
                "Tài khoản Quản trị viên FloodConnect của bạn đã bị Admin gốc vô hiệu hóa. Lý do: " + r + ".");
        return row(u);
    }

    @Transactional
    public AdminAccountRow enable(Long id) {
        User root = current.requireRootAdmin();
        User u = admin(id);
        if (u.getStatus() != UserStatus.LOCKED) throw ApiException.conflict("NOT_DISABLED", "Tài khoản Admin này không bị vô hiệu hóa.");
        Instant now = Instant.now();
        u.setStatus(UserStatus.ACTIVE);
        u.setLockedUntil(null);
        locks.findByUserIdAndUnlockedAtIsNull(u.getId()).forEach(l -> { l.setUnlockedAt(now); l.setUnlockedBy(root); });
        mail.send(u.getEmail(), "FloodConnect - Tài khoản Quản trị viên được mở lại",
                "Tài khoản Quản trị viên FloodConnect của bạn đã được Admin gốc mở lại. Bạn có thể đăng nhập bình thường.");
        return row(u);
    }

    private User admin(Long id) {
        return users.findById(id).filter(u -> u.getRole() == Role.ADMIN)
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy tài khoản Admin."));
    }

    private AdminAccountRow row(User u) {
        String state = u.isLocked() ? "DISABLED" : u.isEmailVerified() ? "ACTIVE" : "INVITED";
        AccountLock active = !u.isLocked() ? null : locks.findByUserIdAndUnlockedAtIsNull(u.getId()).stream()
                .max(Comparator.comparing(AccountLock::getLockedAt)).orElse(null);
        return new AdminAccountRow(u.getId(), u.getFullName(), u.getEmail(), u.getPhone(), u.isRootAdmin(), state, u.getCreatedAt(),
                active == null ? null : active.getReason(), active == null ? null : active.getLockedAt());
    }
}
