package vn.floodconnect.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.auth.dto.AuthDtos.AuthResponse;
import vn.floodconnect.auth.dto.AuthDtos.EmailRequest;
import vn.floodconnect.auth.dto.AuthDtos.LoginRequest;
import vn.floodconnect.auth.dto.AuthDtos.OrgRegisterRequest;
import vn.floodconnect.auth.dto.AuthDtos.RefreshRequest;
import vn.floodconnect.auth.dto.AuthDtos.RegisterRequest;
import vn.floodconnect.auth.dto.AuthDtos.ResetPasswordRequest;
import vn.floodconnect.auth.dto.AuthDtos.VerifyEmailRequest;
import vn.floodconnect.auth.service.AuthService;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.organization.service.OrgDocumentService;
import java.util.List;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    @PostMapping("/register")
    public MessageResponse register(@Valid @RequestBody RegisterRequest r) { return auth.registerCitizen(r); }

    /**
     * multipart: phần "data" (JSON OrgRegisterRequest) + tệp hồ sơ theo từng ô (PDF/JPG/PNG, ≤5MB/tệp): "documents" (giấy tờ chính),
     * "idFront", "idBack" (CCCD hai mặt của trưởng nhóm thiện nguyện, mỗi mặt 1 tệp), "evidence" (minh chứng hoạt động, không bắt buộc).
     */
    @PostMapping(value = "/register-org", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MessageResponse registerOrg(@Valid @RequestPart("data") OrgRegisterRequest data,
                                       @RequestPart(value = "documents", required = false) List<MultipartFile> documents,
                                       @RequestPart(value = "idFront", required = false) List<MultipartFile> idFront,
                                       @RequestPart(value = "idBack", required = false) List<MultipartFile> idBack,
                                       @RequestPart(value = "evidence", required = false) List<MultipartFile> evidence) {
        return auth.registerOrg(data, OrgDocumentService.Uploads.of(documents, idFront, idBack, evidence));
    }

    @PostMapping("/login") public AuthResponse login(@Valid @RequestBody LoginRequest r) { return auth.login(r); }
    @PostMapping("/refresh") public AuthResponse refresh(@Valid @RequestBody RefreshRequest r) { return auth.refresh(r.refreshToken()); }
    @PostMapping("/logout") public MessageResponse logout(@RequestBody(required = false) RefreshRequest r) {
        auth.logout(r == null ? null : r.refreshToken());
        return new MessageResponse("Đã đăng xuất.");
    }
    /** Xác thực bằng mã OTP 6 số gửi qua email (thay cho liên kết trước đây). */
    @PostMapping("/verify-email") public MessageResponse verify(@Valid @RequestBody VerifyEmailRequest r) { return auth.verifyEmail(r.email(), r.code()); }
    @PostMapping("/resend-verification") public MessageResponse resend(@Valid @RequestBody EmailRequest r) { return auth.resendVerification(r.email()); }
    @PostMapping("/forgot-password") public MessageResponse forgot(@Valid @RequestBody EmailRequest r) { return auth.forgotPassword(r.email()); }
    @PostMapping("/reset-password") public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest r) { return auth.resetPassword(r); }
}
