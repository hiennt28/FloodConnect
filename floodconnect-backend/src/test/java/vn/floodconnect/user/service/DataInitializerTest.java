package vn.floodconnect.user.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Tạo Admin lần đầu: bắt buộc ADMIN_PASSWORD hợp lệ (không phải "Admin12345"); Admin có sẵn thì chỉ cảnh báo, không chặn. */
class DataInitializerTest {
    private final UserRepository users = mock(UserRepository.class);
    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4); // cost thấp cho test nhanh

    private DataInitializer init(String password) {
        DataInitializer d = new DataInitializer(users, encoder);
        ReflectionTestUtils.setField(d, "email", "Admin@FloodConnect.vn");
        ReflectionTestUtils.setField(d, "phone", "0900000000");
        ReflectionTestUtils.setField(d, "password", password);
        return d;
    }

    @Test void chuaCoAdminVaMatKhauTrongThiKhongKhoiDong() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> init("").run());
        assertThrows(IllegalStateException.class, () -> init(null).run());
        verify(users, never()).save(any());
    }

    @Test void chuaCoAdminVaDungMatKhauMauCuThiKhongKhoiDong() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of());
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> init("Admin12345").run());
        assertTrue(e.getMessage().contains("ADMIN_PASSWORD"));
        verify(users, never()).save(any());
    }

    @Test void chuaCoAdminVaMatKhauYeuThiKhongKhoiDong() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of());
        assertThrows(IllegalStateException.class, () -> init("abcdefgh").run()); // không có số
        assertThrows(IllegalStateException.class, () -> init("a1b2").run());     // quá ngắn
        verify(users, never()).save(any());
    }

    @Test void chuaCoAdminVaMatKhauHopLeThiTaoAdmin() {
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of());
        init("Quantri2026").run();
        ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
        verify(users).save(saved.capture());
        User a = saved.getValue();
        assertEquals(Role.ADMIN, a.getRole());
        assertEquals("admin@floodconnect.vn", a.getEmail());
        assertTrue(a.isEmailVerified());
        assertTrue(a.isRootAdmin()); // Admin đầu tiên là Admin gốc
        assertTrue(encoder.matches("Quantri2026", a.getPasswordHash()));
    }

    /** Dữ liệu nhập tay thiếu Admin gốc: gắn cho Admin tạo sớm nhất, để luôn có người quản lý được tài khoản Admin. */
    @Test void daCoAdminNhungChuaCoAdminGocThiGanChoAdminSomNhat() {
        User later = new User(); later.setId(7L); later.setRole(Role.ADMIN); later.setEmail("b@x.vn"); later.setPasswordHash(encoder.encode("Khac2026x"));
        User first = new User(); first.setId(2L); first.setRole(Role.ADMIN); first.setEmail("a@x.vn"); first.setPasswordHash(encoder.encode("Khac2026x"));
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(later, first));

        init("").run();

        assertTrue(first.isRootAdmin());
        assertFalse(later.isRootAdmin());
        verify(users).save(first);
    }

    /** Máy đang dùng: Admin đã có (kể cả còn mật khẩu mẫu cũ) thì ứng dụng vẫn chạy, chỉ ghi cảnh báo, không tạo hay sửa gì. */
    @Test void daCoAdminThiKhongChanKeCaKhiThieuAdminPassword() {
        User old = new User();
        old.setRole(Role.ADMIN); old.setEmail("admin@floodconnect.vn"); old.setPasswordHash(encoder.encode("Admin12345")); old.setRootAdmin(true);
        when(users.findByRole(Role.ADMIN)).thenReturn(List.of(old));
        assertDoesNotThrow(() -> init("").run());
        verify(users, never()).save(any());
        assertTrue(encoder.matches("Admin12345", old.getPasswordHash()), "không tự đổi mật khẩu Admin đã có");
    }
}
