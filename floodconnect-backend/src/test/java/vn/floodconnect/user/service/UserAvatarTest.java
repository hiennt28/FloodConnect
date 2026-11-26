package vn.floodconnect.user.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.organization.service.OrgMapper;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Đổi / xóa ảnh đại diện: tệp ảnh cũ được xóa (sau khi commit) để không tồn đọng tệp thừa. */
class UserAvatarTest {
    private final CurrentUser current = mock(CurrentUser.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final UserService service = new UserService(mock(UserRepository.class), mock(RescueOrganizationRepository.class),
            mock(FollowRepository.class), mock(PasswordEncoder.class), current, storage, mock(NotificationPublisher.class),
            mock(OrgMapper.class), mock(UserMapper.class), mock(RefreshTokenRepository.class));
    private final User u = new User();

    @BeforeEach void setUp() { when(current.get()).thenReturn(u); }

    @Test void xoaAnhThiBoUrlVaXoaTepCu() {
        u.setAvatarUrl("http://localhost:8080/files/avatars/cu.jpg");
        service.removeAvatar();
        assertNull(u.getAvatarUrl());
        verify(storage).deleteAfterCommit("http://localhost:8080/files/avatars/cu.jpg");
    }

    @Test void chuaCoAnhThiXoaKhongLamGi() {
        service.removeAvatar();
        assertNull(u.getAvatarUrl());
        verify(storage, never()).deleteAfterCommit(any());
    }

    @Test void doiAnhThiLuuAnhMoiRoiXoaTepCu() {
        u.setAvatarUrl("http://localhost:8080/files/avatars/cu.jpg");
        when(storage.store(any(), eq("avatars"), any())).thenReturn(new FileStorageService.Stored("http://localhost:8080/files/avatars/moi.png", "image/png"));
        service.updateAvatar(new MockMultipartFile("file", "a.png", "image/png", new byte[] { 1 }));
        assertEquals("http://localhost:8080/files/avatars/moi.png", u.getAvatarUrl());
        verify(storage).deleteAfterCommit("http://localhost:8080/files/avatars/cu.jpg");
    }
}
