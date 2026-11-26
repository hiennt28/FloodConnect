package vn.floodconnect.auth.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.auth.dto.AuthDtos.ResetPasswordRequest;
import vn.floodconnect.auth.entity.AuthToken;
import vn.floodconnect.auth.entity.AuthTokenType;
import vn.floodconnect.auth.repository.AuthTokenRepository;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.EmailDomainValidator;
import vn.floodconnect.common.util.TokenUtil;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.notification.repository.NotificationRepository;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.security.JwtService;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import vn.floodconnect.user.service.AccountLockService;
import vn.floodconnect.user.service.UserMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** OTP: giới hạn 5 lần nhập sai, kiểm tra mật khẩu trước khi đụng tới mã, giới hạn 5 mã/giờ. */
class AuthServiceOtpTest {
    private final UserRepository users = mock(UserRepository.class);
    private final AuthTokenRepository authTokens = mock(AuthTokenRepository.class);
    private final RefreshTokenRepository refreshTokens = mock(RefreshTokenRepository.class);
    private final PasswordEncoder encoder = mock(PasswordEncoder.class);
    private final MailService mail = mock(MailService.class);
    private final AuthService service = new AuthService(users, mock(RescueOrganizationRepository.class), mock(OrganizationDocumentRepository.class),
            authTokens, refreshTokens, encoder, mock(JwtService.class), mail, mock(NotificationPublisher.class), mock(OrgDocumentService.class),
            mock(AccountLockService.class), mock(UserMapper.class), mock(NotificationRepository.class), mock(EmailDomainValidator.class),
            mock(GeoService.class));

    private final User u = new User();

    @BeforeEach void setUp() {
        u.setId(7L); u.setEmail("an@example.com"); u.setEmailVerified(false); u.setPasswordHash("OLD");
        when(users.findByEmailIgnoreCase("an@example.com")).thenReturn(Optional.of(u));
    }

    private AuthToken pending(AuthTokenType type, String code) {
        AuthToken t = new AuthToken();
        t.setUser(u); t.setType(type); t.setTokenHash(TokenUtil.sha256(code));
        t.setExpiresAt(Instant.now().plus(Duration.ofMinutes(10))); t.setCreatedAt(Instant.now());
        when(authTokens.findTopByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(7L, type)).thenReturn(Optional.of(t));
        return t;
    }

    private static ApiException fail(Runnable r) { return assertThrows(ApiException.class, r::run); }

    // ---------------- Nhập sai mã ----------------
    @Test void nhapSaiMaThiDemLuiVaLanThuNamHuyMa() {
        AuthToken t = pending(AuthTokenType.EMAIL_VERIFY, "123456");
        for (int left = 4; left >= 1; left--) {
            ApiException e = fail(() -> service.verifyEmail("an@example.com", "000000"));
            assertEquals("WRONG_CODE", e.getCode());
            assertTrue(e.getMessage().contains("Còn " + left + " lần thử"), e.getMessage());
        }
        ApiException last = fail(() -> service.verifyEmail("an@example.com", "000000"));
        assertEquals("TOKEN_INVALID", last.getCode());
        assertEquals(5, t.getFailedAttempts());
        assertNotNull(t.getUsedAt(), "lần sai thứ 5 phải hủy mã");
        assertFalse(u.isEmailVerified());
    }

    /** Bộ đếm chỉ được lưu nếu transaction KHÔNG bị hoàn tác khi ném ApiException (lỗi gốc của mục #1). */
    @Test void verifyVaResetKhongRollbackKhiNhapSaiMa() throws Exception {
        Transactional verify = AuthService.class.getMethod("verifyEmail", String.class, String.class).getAnnotation(Transactional.class);
        Transactional reset = AuthService.class.getMethod("resetPassword", ResetPasswordRequest.class).getAnnotation(Transactional.class);
        assertTrue(Arrays.asList(verify.noRollbackFor()).contains(ApiException.class));
        assertTrue(Arrays.asList(reset.noRollbackFor()).contains(ApiException.class));
    }

    @Test void dungMaThiXacThucEmail() {
        AuthToken t = pending(AuthTokenType.EMAIL_VERIFY, "123456");
        service.verifyEmail("an@example.com", "123456");
        assertTrue(u.isEmailVerified());
        assertNotNull(t.getUsedAt());
        assertEquals(0, t.getFailedAttempts());
    }

    // ---------------- Đặt lại mật khẩu ----------------
    @Test void matKhauKhongKhopThiKhongDungToiMa() {
        AuthToken t = pending(AuthTokenType.PASSWORD_RESET, "123456");
        ApiException e = fail(() -> service.resetPassword(new ResetPasswordRequest("an@example.com", "123456", "Demo12345", "b1234567")));
        assertEquals("PASSWORD_MISMATCH", e.getCode());
        assertEquals(0, t.getFailedAttempts());
        assertNull(t.getUsedAt(), "mã vẫn dùng lại được sau khi sửa mật khẩu");
        verify(authTokens, never()).findTopByUserIdAndTypeAndUsedAtIsNullOrderByCreatedAtDesc(anyLong(), any());
    }

    @Test void matKhauYeuThiKhongDungToiMa() {
        AuthToken t = pending(AuthTokenType.PASSWORD_RESET, "123456");
        ApiException e = fail(() -> service.resetPassword(new ResetPasswordRequest("an@example.com", "000000", "abc", "abc")));
        assertEquals("WEAK_PASSWORD", e.getCode());
        assertEquals(0, t.getFailedAttempts());
    }

    @Test void saiMaKhiDatLaiMatKhauCungBiDem() {
        AuthToken t = pending(AuthTokenType.PASSWORD_RESET, "123456");
        ApiException e = fail(() -> service.resetPassword(new ResetPasswordRequest("an@example.com", "000000", "Demo12345", "Demo12345")));
        assertEquals("WRONG_CODE", e.getCode());
        assertEquals(1, t.getFailedAttempts());
        assertEquals("OLD", u.getPasswordHash());
    }

    @Test void dungMaVaMatKhauHopLeThiDoiMatKhauVaDangXuatMoiThietBi() {
        AuthToken t = pending(AuthTokenType.PASSWORD_RESET, "123456");
        when(encoder.encode("Demo12345")).thenReturn("NEW");
        service.resetPassword(new ResetPasswordRequest("an@example.com", "123456", "Demo12345", "Demo12345"));
        assertEquals("NEW", u.getPasswordHash());
        assertNotNull(t.getUsedAt());
        assertTrue(u.isEmailVerified());
        verify(refreshTokens).revokeAll(eq(7L), any());
    }

    // ---------------- Giới hạn 5 mã / giờ ----------------
    private void sentRecently(AuthTokenType type, int count, Duration oldestAgo) {
        AuthToken last = new AuthToken();
        last.setCreatedAt(Instant.now().minus(Duration.ofMinutes(2))); // đã qua 60 giây chờ gửi lại
        when(authTokens.findTopByUserIdAndTypeOrderByCreatedAtDesc(7L, type)).thenReturn(Optional.of(last));
        List<AuthToken> recent = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            AuthToken t = new AuthToken();
            t.setCreatedAt(Instant.now().minus(oldestAgo).plus(Duration.ofMinutes(i)));
            recent.add(t);
        }
        when(authTokens.findByUserIdAndTypeAndCreatedAtAfterOrderByCreatedAtAsc(eq(7L), eq(type), any())).thenReturn(recent);
    }

    @Test void guiLaiMaXacThucQuaNamMaMotGioThiBaoLoi() {
        sentRecently(AuthTokenType.EMAIL_VERIFY, 5, Duration.ofMinutes(50));
        ApiException e = fail(() -> service.resendVerification("an@example.com"));
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, e.getStatus());
        assertEquals("OTP_LIMIT", e.getCode());
        long secs = Long.parseLong(e.getErrors().get("retryAfterSeconds"));
        assertTrue(secs > 9 * 60 && secs <= 10 * 60, "mã cũ nhất gửi 50 phút trước: còn khoảng 10 phút, được " + secs);
        verify(authTokens, never()).save(any());
        verify(mail, never()).sendVerify(any(), any());
    }

    @Test void quenMatKhauQuaNamMaMotGioThiImLangKhongGui() {
        sentRecently(AuthTokenType.PASSWORD_RESET, 5, Duration.ofMinutes(30));
        assertDoesNotThrow(() -> service.forgotPassword("an@example.com")); // không lộ tài khoản có tồn tại hay không
        verify(authTokens, never()).save(any());
        verify(mail, never()).sendReset(any(), any());
    }

    @Test void duoiNamMaMotGioThiVanGui() {
        sentRecently(AuthTokenType.EMAIL_VERIFY, 4, Duration.ofMinutes(50));
        service.resendVerification("an@example.com");
        verify(authTokens).save(any());
        verify(mail).sendVerify(eq("an@example.com"), anyString());
    }
}
