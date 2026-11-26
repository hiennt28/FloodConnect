package vn.floodconnect.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.auth.dto.AuthDtos.AuthResponse;
import vn.floodconnect.auth.dto.AuthDtos.LoginRequest;
import vn.floodconnect.auth.dto.AuthDtos.OrgRegisterRequest;
import vn.floodconnect.auth.dto.AuthDtos.RegisterRequest;
import vn.floodconnect.auth.dto.AuthDtos.ResetPasswordRequest;
import vn.floodconnect.auth.entity.AuthToken;
import vn.floodconnect.auth.entity.AuthTokenType;
import vn.floodconnect.auth.entity.RefreshToken;
import vn.floodconnect.auth.repository.AuthTokenRepository;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.user.service.UserMapper;
import vn.floodconnect.common.util.TokenUtil;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.notification.repository.NotificationRepository;
import vn.floodconnect.common.util.EmailDomainValidator;
import org.springframework.http.HttpStatus;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.security.JwtService;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.UserRepository;
import vn.floodconnect.user.service.AccountLockService;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository users;
    private final RescueOrganizationRepository orgs;
    private final OrganizationDocumentRepository orgDocs;
    private final AuthTokenRepository authTokens;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final MailService mail;
    private final NotificationPublisher notifications;
    private final OrgDocumentService orgDocuments;
    private final AccountLockService lockService;
    private final UserMapper userMapper;
    private final NotificationRepository notificationRepo;
    private final EmailDomainValidator emailDomainValidator;
    private final GeoService geo;
    @Value("${app.jwt.refresh-days}") private long refreshDays;

    private static final int OTP_LENGTH = 6;
    private static final Duration OTP_TTL = Duration.ofMinutes(10);
    private static final Duration RESEND_COOLDOWN = Duration.ofSeconds(60);
    private static final int MAX_OTP_ATTEMPTS = 5;
    /** Mỗi tài khoản nhận tối đa ngần này mã (mỗi loại) trong {@link #OTP_WINDOW}: chặn dò mã bằng cách xin mã mới liên tục và chặn spam hộp thư. */
    static final int MAX_OTP_PER_WINDOW = 5;
    static final Duration OTP_WINDOW = Duration.ofHours(1);
    /** Tài khoản đăng ký nhưng chưa xác thực email quá thời gian này: email/SĐT được giải phóng cho người khác đăng ký lại. */
    private static final Duration UNVERIFIED_RELEASE_AFTER = Duration.ofHours(24);

    // ---------------- Đăng ký ----------------
    private void validateAccount(RegisterRequest r, Map<String, String> e) {
        if (r.fullName() == null || r.fullName().isBlank()) e.put("fullName", "Vui lòng nhập họ tên.");
        else if (Validators.tooLong(r.fullName(), Validators.TEXT_MAX)) e.put("fullName", Validators.tooLongMsg("Họ tên", Validators.TEXT_MAX));
        if (!Validators.emailOk(r.email())) e.put("email", "Email không hợp lệ.");
        else {
            String email = r.email().trim();
            Optional<User> existing = users.findByEmailIgnoreCase(email);
            if (existing.isPresent() && !releaseIfStale(existing.get()))
                e.put("email", existing.get().isEmailVerified() ? "Email đã được sử dụng."
                        : "Email này đã đăng ký nhưng chưa xác thực. Vui lòng nhập mã xác thực đã gửi, hoặc thử lại sau "
                                + UNVERIFIED_RELEASE_AFTER.toHours() + " giờ kể từ lúc đăng ký.");
            else emailDomainValidator.check(email, e);
        }
        if (!Validators.phoneOk(r.phone())) e.put("phone", Validators.PHONE_MSG);
        else {
            Optional<User> existing = users.findByPhone(r.phone());
            if (existing.isPresent() && !releaseIfStale(existing.get()))
                e.put("phone", existing.get().isEmailVerified() ? "Số điện thoại đã được sử dụng."
                        : "Số điện thoại đang dùng cho một đăng ký chưa xác thực. Vui lòng thử lại sau "
                                + UNVERIFIED_RELEASE_AFTER.toHours() + " giờ kể từ lúc đăng ký đó.");
        }
        if (!Validators.passwordOk(r.password())) e.put("password", Validators.PASSWORD_MSG);
        if (r.password() != null && !r.password().equals(r.confirmPassword())) e.put("confirmPassword", "Mật khẩu xác nhận không khớp.");
    }

    /**
     * Tài khoản trùng email/SĐT nhưng chưa xác thực và đã tạo quá {@link #UNVERIFIED_RELEASE_AFTER}: coi là đăng ký bỏ dở,
     * xóa hẳn để giải phóng email/SĐT cho người khác (tài khoản này chưa từng đăng nhập được nên không có dữ liệu nào khác
     * gắn với nó, trừ khi là hồ sơ tổ chức Admin đã xét duyệt — trường hợp đó giữ nguyên, không xóa).
     * @return true nếu đã giải phóng (xóa) tài khoản cũ.
     */
    private boolean releaseIfStale(User u) {
        // Tài khoản Admin (do Admin gốc tạo, chờ người đó tự đặt mật khẩu) không bao giờ bị tự xóa
        if (u.getRole() == Role.ADMIN) return false;
        if (u.isEmailVerified() || u.getCreatedAt().isAfter(Instant.now().minus(UNVERIFIED_RELEASE_AFTER))) return false;
        RescueOrganization o = orgs.findByUserId(u.getId()).orElse(null);
        if (o != null) {
            if (o.getStatus() != OrgStatus.PENDING) return false; // Admin đã duyệt/từ chối: giữ lại để không mất hồ sơ xét duyệt
            orgDocuments.deleteAllOf(o); // xóa cả tệp giấy tờ (riêng tư), không chỉ dòng dữ liệu
            orgs.delete(o);
        }
        notificationRepo.deleteByUserId(u.getId());
        authTokens.deleteByUserId(u.getId());
        refreshTokens.revokeAll(u.getId(), Instant.now()); // chưa từng đăng nhập nên thường rỗng, revoke cho an toàn trước khi xóa
        users.delete(u); // các dòng follows liên quan bị CSDL xóa theo (khóa ngoại ON DELETE CASCADE, Flyway V4)
        users.flush(); // đảm bảo bản ghi cũ đã biến mất trước khi lưu bản ghi mới trong cùng transaction
        return true;
    }

    private User newUser(RegisterRequest r, Role role) {
        User u = new User();
        u.setFullName(r.fullName().trim()); u.setEmail(r.email().trim().toLowerCase()); u.setPhone(r.phone());
        u.setPasswordHash(encoder.encode(r.password())); u.setRole(role);
        return users.save(u);
    }

    /**
     * Sinh và lưu một OTP cho {@code type}, trả về mã gốc (chưa hash) để gửi email.
     * Chặn nếu còn trong thời gian chờ gửi lại (60 giây) hoặc đã gửi đủ {@link #MAX_OTP_PER_WINDOW} mã trong 1 giờ.
     */
    private String issueOtp(User u, AuthTokenType type) {
        Instant now = Instant.now();
        authTokens.findTopByUserIdAndTypeOrderByCreatedAtDesc(u.getId(), type).ifPresent(last -> {
            long left = RESEND_COOLDOWN.minus(Duration.between(last.getCreatedAt(), now)).toSeconds();
            if (left > 0) throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RESEND_COOLDOWN",
                    "Vui lòng chờ " + left + " giây trước khi gửi lại mã.", Map.of("retryAfterSeconds", String.valueOf(left)));
        });
        List<AuthToken> recent = authTokens.findByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtAsc(u.getId(), type, now.minus(OTP_WINDOW));
        if (recent.size() >= MAX_OTP_PER_WINDOW) {
            // Được gửi tiếp khi mã cũ nhất trong 1 giờ qua ra khỏi cửa sổ
            Instant oldest = recent.get(0).getCreatedAt() == null ? now : recent.get(0).getCreatedAt();
            long secs = Math.max(1, Duration.between(now, oldest.plus(OTP_WINDOW)).toSeconds());
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "OTP_LIMIT",
                    "Bạn đã yêu cầu quá " + MAX_OTP_PER_WINDOW + " mã trong 1 giờ. Vui lòng thử lại sau " + ((secs + 59) / 60) + " phút.",
                    Map.of("retryAfterSeconds", String.valueOf(secs)));
        }
        String code = TokenUtil.randomOtp(OTP_LENGTH);
        AuthToken t = new AuthToken();
        t.setUser(u); t.setType(type); t.setTokenHash(TokenUtil.sha256(code));
        t.setExpiresAt(Instant.now().plus(OTP_TTL));
        authTokens.save(t);
        return code;
    }

    /** So khớp OTP người dùng nhập với OTP đang chờ của {@code type}; đếm số lần sai, hủy mã sau {@link #MAX_OTP_ATTEMPTS} lần. */
    private void consumeOtp(User u, AuthTokenType type, String code) {
        AuthToken t = authTokens.findTopByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(u.getId(), type)
                .orElseThrow(() -> ApiException.bad("TOKEN_INVALID", "Không có mã xác thực nào đang chờ. Vui lòng gửi lại mã."));
        if (t.getExpiresAt().isBefore(Instant.now()))
            throw ApiException.bad("TOKEN_EXPIRED", "Mã xác thực đã hết hạn. Vui lòng gửi lại mã.");
        if (!t.getTokenHash().equals(TokenUtil.sha256(code == null ? "" : code.trim()))) {
            t.setFailedAttempts(t.getFailedAttempts() + 1);
            int left = MAX_OTP_ATTEMPTS - t.getFailedAttempts();
            if (left <= 0) {
                t.setUsedAt(Instant.now());
                throw ApiException.bad("TOKEN_INVALID", "Mã xác thực không đúng. Bạn đã nhập sai quá số lần cho phép, vui lòng gửi lại mã.");
            }
            throw ApiException.bad("WRONG_CODE", "Mã xác thực không đúng. Còn " + left + " lần thử.");
        }
        t.setUsedAt(Instant.now());
    }

    private void sendVerification(User u) { mail.sendVerify(u.getEmail(), issueOtp(u, AuthTokenType.EMAIL_VERIFY)); }

    @Transactional
    public MessageResponse registerCitizen(RegisterRequest r) {
        Map<String, String> e = new LinkedHashMap<>();
        validateAccount(r, e);
        if (!e.isEmpty()) throw ApiException.validation(e);
        sendVerification(newUser(r, Role.CITIZEN));
        return new MessageResponse("Đăng ký thành công. Vui lòng kiểm tra email để xác thực tài khoản.");
    }

    @Transactional
    public MessageResponse registerOrg(OrgRegisterRequest r, OrgDocumentService.Uploads uploads) {
        Map<String, String> e = new LinkedHashMap<>();
        validateAccount(r.account(), e);
        orgDocuments.check(r.orgType(), uploads, Set.of(), e);
        String website = r.websiteUrl() == null || r.websiteUrl().isBlank() ? null : r.websiteUrl().trim();
        if (website != null && !Validators.urlOk(website)) e.put("websiteUrl", Validators.URL_MSG);
        if (Validators.tooLong(r.orgName(), Validators.TEXT_MAX)) e.put("orgName", Validators.tooLongMsg("Tên tổ chức", Validators.TEXT_MAX));
        // Mô tả bắt buộc: AI Matching suy năng lực của tổ chức (cứu hộ, y tế, tiếp tế, vận chuyển) từ đây để tính điểm C
        String descriptionError = Validators.orgDescriptionError(r.description());
        if (descriptionError != null) e.put("description", descriptionError);
        if (Validators.tooLong(r.streetAddress(), Validators.TEXT_MAX))
            e.put("address", Validators.tooLongMsg("Thôn/xóm, số nhà, đường", Validators.TEXT_MAX));
        // Tên tỉnh, xã lấy từ bảng địa giới theo mã xã. Tọa độ trụ sở (AI Matching tính khoảng cách tới ca SOS) là ghim người
        // đại diện đặt trên bản đồ, phải nằm trong xã đã chọn. Bảng địa giới chưa nạp (503) thì để lỗi đi thẳng ra ngoài.
        WardCenter w = null;
        try { w = geo.center(r.wardCode()); }
        catch (ApiException ex) { if (ex.getStatus().is5xxServerError()) throw ex; e.put("address", ex.getMessage()); }
        if (w != null) {
            try { geo.requireInWard(r.latitude(), r.longitude(), w); }
            catch (ApiException ex) { if (ex.getStatus().is5xxServerError()) throw ex; e.put("location", ex.getMessage()); }
        }
        if (!e.isEmpty()) throw ApiException.validation(e);

        User u = newUser(r.account(), Role.RESCUE_ORG);
        RescueOrganization o = new RescueOrganization();
        o.setUser(u); o.setName(r.orgName().trim()); o.setOrgType(r.orgType());
        o.setProvince(w.provinceName()); o.setDistrict(null); o.setWard(w.name()); o.setStreetAddress(r.streetAddress().trim());
        o.setDescription(r.description().trim()); o.setLatitude(r.latitude()); o.setLongitude(r.longitude()); o.setServiceAreas(r.serviceAreas());
        o.setWebsiteUrl(website);
        o.setMaxConcurrentCases(r.maxConcurrentCases()); o.setServiceRadiusKm(r.serviceRadiusKm());
        o.setStatus(OrgStatus.PENDING); o.setOperatingStatus(OperatingStatus.INACTIVE);
        orgs.save(o);
        orgDocuments.saveAll(o, uploads);
        sendVerification(u);

        notifications.notifyAdmins(NotificationType.ORG_REVIEW, "Hồ sơ tổ chức mới",
                "Hồ sơ tổ chức mới cần xét duyệt: " + o.getName() + ".", "ORG", o.getId());
        notifications.notify(u, NotificationType.ORG_REVIEW, "Đã nhận hồ sơ",
                "FloodConnect đã nhận hồ sơ đăng ký tổ chức của bạn. Vui lòng chờ Admin xét duyệt.", "ORG", o.getId());
        mail.send(u.getEmail(), "FloodConnect - Đã nhận hồ sơ đăng ký tổ chức", "Hồ sơ đã gửi, vui lòng chờ Admin xét duyệt.");
        return new MessageResponse("Hồ sơ đã gửi, vui lòng xác thực email và chờ Admin xét duyệt.");
    }

    // ---------------- Xác thực email (OTP 6 số, hiệu lực 10 phút) ----------------
    /**
     * noRollbackFor: nhập sai mã thì consumeOtp tăng failedAttempts (và hủy mã ở lần sai thứ 5) rồi ném ApiException.
     * Mặc định Spring hoàn tác transaction khi gặp RuntimeException nên bộ đếm sẽ không bao giờ được lưu và giới hạn
     * 5 lần không có tác dụng (giống lý do ở login). Mọi lỗi khác trong hàm xảy ra trước khi có thay đổi dữ liệu.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse verifyEmail(String email, String code) {
        User u = users.findByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> ApiException.bad("TOKEN_INVALID", "Email hoặc mã xác thực không đúng."));
        if (u.isEmailVerified()) return new MessageResponse("Email đã được xác thực trước đó.");
        consumeOtp(u, AuthTokenType.EMAIL_VERIFY, code);
        u.setEmailVerified(true);
        return new MessageResponse("Xác thực email thành công. Bạn có thể đăng nhập.");
    }

    /** Không lộ việc tài khoản có tồn tại hay không, nhưng cooldown gửi lại được báo thật vì người gọi đang ở màn hình nhập mã của chính họ. */
    @Transactional
    public MessageResponse resendVerification(String email) {
        users.findByEmailIgnoreCase(email.trim()).filter(u -> !u.isEmailVerified()).ifPresent(this::sendVerification);
        return new MessageResponse("Nếu email hợp lệ và chưa xác thực, bạn sẽ nhận được mã xác thực mới.");
    }

    // ---------------- Đăng nhập / token ----------------
    /**
     * noRollbackFor: khi sai mật khẩu ta tăng failedLoginCount rồi ném ApiException. Mặc định Spring hoàn tác
     * transaction khi gặp RuntimeException nên bộ đếm sẽ không bao giờ được lưu và khóa 5 lần sai không hoạt động.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse login(LoginRequest r) {
        // Mật khẩu có chữ có dấu/dấu cách không thể đúng (mọi mật khẩu đã đặt đều qua Validators.passwordOk):
        // báo ngay để người dùng tắt bộ gõ tiếng Việt, không tính là một lần sai, không tra tài khoản.
        if (!Validators.passwordCharsOk(r.password())) throw ApiException.bad("INVALID_PASSWORD_CHARS", Validators.PASSWORD_CHARS_MSG);
        String id = r.identifier().trim();
        User u = (id.contains("@") ? users.findByEmailIgnoreCase(id) : users.findByPhone(id))
                .orElseThrow(() -> ApiException.unauthorized("Thông tin đăng nhập không chính xác."));
        Instant now = Instant.now();
        if (u.getLoginLockedUntil() != null && u.getLoginLockedUntil().isAfter(now)) {
            throw loginLocked(Duration.between(now, u.getLoginLockedUntil()));
        }
        if (!encoder.matches(r.password(), u.getPasswordHash())) {
            u.setFailedLoginCount(u.getFailedLoginCount() + 1);
            boolean lockedNow = false;
            if (u.getFailedLoginCount() >= 5) { u.setLoginLockedUntil(now.plus(LOGIN_LOCK)); u.setFailedLoginCount(0); lockedNow = true; }
            users.saveAndFlush(u);
            // Lần sai thứ 5: báo khóa ngay (kèm số giây còn lại cho đồng hồ đếm ngược) thay vì đợi lần thử kế tiếp
            if (lockedNow) throw loginLocked(LOGIN_LOCK);
            throw ApiException.unauthorized("Thông tin đăng nhập không chính xác.");
        }
        u.setFailedLoginCount(0); u.setLoginLockedUntil(null);
        // Admin bị Admin gốc vô hiệu hóa: không đăng nhập được (khác người dân / tổ chức bị khóa vẫn đăng nhập để gửi SOS, ủng hộ)
        if (adminDisabled(u)) throw adminDisabledError();
        // Kèm email để người đăng nhập bằng số điện thoại cũng sang được trang nhập mã. Chỉ trả khi đã đúng mật khẩu nên không lộ email cho người lạ.
        if (!u.isEmailVerified())
            throw new ApiException(HttpStatus.FORBIDDEN, "EMAIL_NOT_VERIFIED",
                    "Tài khoản chưa xác thực email. Vui lòng kiểm tra hộp thư hoặc gửi lại email xác thực.", Map.of("email", u.getEmail()));
        // Khóa đã hết hạn nhưng scheduler chưa kịp chạy
        if (u.getStatus() == UserStatus.LOCKED && u.getLockedUntil() != null && u.getLockedUntil().isBefore(now)) lockService.unlock(u, null);
        return issue(u);
    }

    private static final Duration LOGIN_LOCK = Duration.ofMinutes(5);

    /** 429 LOGIN_LOCKED. errors.retryAfterSeconds = số giây còn lại, frontend dùng để chạy đồng hồ đếm ngược. */
    private static ApiException loginLocked(Duration remaining) {
        long secs = Math.max(1, (remaining.toMillis() + 999) / 1000);
        long mins = (secs + 59) / 60;
        return new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "LOGIN_LOCKED",
                "Bạn đã nhập sai quá nhiều lần. Vui lòng thử lại sau " + mins + " phút.",
                Map.of("retryAfterSeconds", String.valueOf(secs)));
    }

    private AuthResponse issue(User u) {
        String raw = TokenUtil.random();
        RefreshToken t = new RefreshToken();
        t.setUser(u); t.setTokenHash(TokenUtil.sha256(raw)); t.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshDays)));
        refreshTokens.save(t);
        return new AuthResponse(jwt.generate(u), raw, "Bearer", jwt.accessSeconds(), userMapper.profile(u));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken t = refreshTokens.findByTokenHash(TokenUtil.sha256(refreshToken))
                .orElseThrow(() -> ApiException.unauthorized("Phiên đăng nhập không hợp lệ."));
        if (t.getRevokedAt() != null || t.getExpiresAt().isBefore(Instant.now())) throw ApiException.unauthorized("Phiên đăng nhập đã hết hạn.");
        t.setRevokedAt(Instant.now()); // xoay vòng refresh token
        if (adminDisabled(t.getUser())) throw ApiException.unauthorized("Tài khoản Admin đã bị vô hiệu hóa.");
        return issue(t.getUser());
    }

    /** Tài khoản Admin đang bị Admin gốc vô hiệu hóa. */
    public static boolean adminDisabled(User u) { return u.getRole() == Role.ADMIN && u.isLocked(); }

    public static ApiException adminDisabledError() {
        return ApiException.forbidden("ADMIN_DISABLED", "Tài khoản Admin của bạn đã bị vô hiệu hóa. Vui lòng liên hệ Admin gốc.");
    }

    /**
     * Admin gốc vừa tạo tài khoản Admin mới (hoặc bấm gửi lại): gửi mã OTP đặt mật khẩu tới email người đó. Dùng chung luồng
     * "Đặt lại mật khẩu" (PASSWORD_RESET): đặt mật khẩu xong thì email cũng được xác thực và người đó đăng nhập được.
     * Áp dụng cùng giới hạn gửi mã (chờ 60 giây giữa hai lần, tối đa 5 mã/giờ): vượt giới hạn thì báo lỗi 429 cho Admin gốc.
     */
    @Transactional
    public void sendAdminInvite(User u) { mail.sendAdminInvite(u.getEmail(), issueOtp(u, AuthTokenType.PASSWORD_RESET)); }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null) return;
        refreshTokens.findByTokenHash(TokenUtil.sha256(refreshToken)).ifPresent(t -> t.setRevokedAt(Instant.now()));
    }

    // ---------------- Quên / đặt lại mật khẩu (OTP 6 số, hiệu lực 10 phút) ----------------
    /** Không lộ việc tài khoản có tồn tại hay không: chờ gửi lại 60 giây và giới hạn 5 mã/giờ bị nuốt lặng lẽ thay vì báo lỗi 429. */
    @Transactional
    public MessageResponse forgotPassword(String email) {
        users.findByEmailIgnoreCase(email.trim()).ifPresent(u -> {
            try { mail.sendReset(u.getEmail(), issueOtp(u, AuthTokenType.PASSWORD_RESET)); }
            catch (ApiException ex) { /* đang chờ gửi lại hoặc đã đủ 5 mã/giờ: bỏ qua, không lộ thông tin tài khoản */ }
        });
        return new MessageResponse("Nếu email hợp lệ, bạn sẽ nhận được mã đặt lại mật khẩu.");
    }

    /**
     * Kiểm tra mật khẩu mới TRƯỚC rồi mới kiểm OTP: mật khẩu yếu/không khớp thì báo ngay, mã không bị tính một lần thử và
     * người dùng gửi lại được với cùng mã. Việc này không phụ thuộc tài khoản nên không lộ email có tồn tại hay không.
     * noRollbackFor: như verifyEmail, để lần nhập sai mã được ghi nhận thật.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse resetPassword(ResetPasswordRequest r) {
        if (!Validators.passwordOk(r.newPassword())) throw ApiException.bad("WEAK_PASSWORD", Validators.PASSWORD_MSG);
        if (!r.newPassword().equals(r.confirmPassword())) throw ApiException.bad("PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp.");
        User u = users.findByEmailIgnoreCase(r.email().trim())
                .orElseThrow(() -> ApiException.bad("TOKEN_INVALID", "Email hoặc mã xác thực không đúng."));
        consumeOtp(u, AuthTokenType.PASSWORD_RESET, r.code());
        u.setPasswordHash(encoder.encode(r.newPassword()));
        // Đã chứng minh quyền sở hữu email qua OTP: coi như xác thực email luôn, và mở khóa đăng nhập do nhập sai nhiều lần (nếu có)
        u.setEmailVerified(true);
        u.setFailedLoginCount(0);
        u.setLoginLockedUntil(null);
        refreshTokens.revokeAll(u.getId(), Instant.now());
        return new MessageResponse("Đặt lại mật khẩu thành công.");
    }
}