package vn.floodconnect.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgMapper;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Mục #29: đăng ký chưa xác thực email chưa phải thành viên: không có hồ sơ công khai, không theo dõi được. */
class UnverifiedUserVisibilityTest {
    private final UserRepository users = mock(UserRepository.class);
    private final FollowRepository follows = mock(FollowRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final UserService service = new UserService(users, mock(RescueOrganizationRepository.class), follows,
            mock(PasswordEncoder.class), current, mock(FileStorageService.class), mock(NotificationPublisher.class),
            mock(OrgMapper.class), mock(UserMapper.class), mock(RefreshTokenRepository.class));

    private final User me = new User(), pending = new User();

    @BeforeEach void setUp() {
        me.setId(1L); me.setRole(Role.CITIZEN); me.setEmailVerified(true);
        pending.setId(2L); pending.setRole(Role.CITIZEN); pending.setEmailVerified(false);
        when(current.get()).thenReturn(me);
        when(users.findById(2L)).thenReturn(Optional.of(pending));
    }

    @Test void unverifiedUserHasNoPublicProfile() {
        ApiException e = assertThrows(ApiException.class, () -> service.publicProfile(2L));
        assertEquals("NOT_FOUND", e.getCode());
    }

    @Test void cannotFollowUnverifiedUser() {
        assertThrows(ApiException.class, () -> service.follow(2L));
        verify(follows, never()).save(any());
    }

    @Test void verifiedUserCanBeFollowed() {
        pending.setEmailVerified(true);
        service.follow(2L);
        verify(follows).save(any());
    }
}
