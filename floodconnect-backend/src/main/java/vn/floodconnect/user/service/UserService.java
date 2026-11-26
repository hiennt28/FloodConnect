package vn.floodconnect.user.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.organization.service.OrgMapper;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.common.util.SearchKeyword;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.dto.OrganizationDtos.OrgInfo;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.RescueOrganizationRepository;
import vn.floodconnect.post.entity.Follow;
import vn.floodconnect.post.repository.FollowRepository;
import vn.floodconnect.auth.repository.RefreshTokenRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.dto.UserDtos.ChangePasswordRequest;
import vn.floodconnect.user.dto.UserDtos.GpsRequest;
import vn.floodconnect.user.dto.UserDtos.PublicProfile;
import vn.floodconnect.user.dto.UserDtos.SearchResult;
import vn.floodconnect.user.dto.UserDtos.UpdateProfileRequest;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import vn.floodconnect.user.entity.GpsPermission;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.UserRepository;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final RescueOrganizationRepository orgs;
    private final FollowRepository follows;
    private final PasswordEncoder encoder;
    private final CurrentUser current;
    private final FileStorageService storage;
    private final NotificationPublisher notifications;
    private final OrgMapper orgMapper;
    private final UserMapper userMapper;
    private final RefreshTokenRepository refreshTokens;

    @Transactional(readOnly = true)
    public UserProfile me() { return userMapper.profile(current.get()); }

    @Transactional
    public UserProfile updateProfile(UpdateProfileRequest r) {
        User u = current.get();
        if (r.fullName().isBlank()) throw ApiException.bad("Họ tên không được để trống.");
        if (Validators.tooLong(r.fullName(), Validators.TEXT_MAX)) throw ApiException.bad("INVALID_NAME", Validators.tooLongMsg("Họ tên", Validators.TEXT_MAX));
        if (!Validators.phoneOk(r.phone())) throw ApiException.bad(Validators.PHONE_MSG);
        users.findByPhone(r.phone()).filter(x -> !x.getId().equals(u.getId()))
                .ifPresent(x -> { throw ApiException.bad("PHONE_EXISTS", "Số điện thoại đã được sử dụng."); });
        u.setFullName(r.fullName().trim());
        u.setPhone(r.phone()); // email không cho sửa tự do
        return userMapper.profile(u);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest r) {
        User u = current.get();
        if (!encoder.matches(r.currentPassword(), u.getPasswordHash())) throw ApiException.bad("WRONG_PASSWORD", "Mật khẩu hiện tại không đúng.");
        if (!Validators.passwordOk(r.newPassword())) throw ApiException.bad("WEAK_PASSWORD", Validators.PASSWORD_MSG);
        if (!r.newPassword().equals(r.confirmPassword())) throw ApiException.bad("PASSWORD_MISMATCH", "Mật khẩu xác nhận không khớp.");
        u.setPasswordHash(encoder.encode(r.newPassword()));
        refreshTokens.revokeAll(u.getId(), Instant.now()); // đổi mật khẩu: đăng xuất khỏi mọi thiết bị khác, chỉ phiên hiện tại còn hiệu lực đến khi access token hết hạn
    }

    /** Đổi ảnh đại diện (tổ chức: logo). Tệp ảnh cũ được xóa sau khi lưu thành công để không tồn đọng tệp thừa. */
    @Transactional
    public UserProfile updateAvatar(MultipartFile file) {
        User u = current.get();
        String old = u.getAvatarUrl();
        u.setAvatarUrl(storage.store(file, "avatars", FileStorageService.AVATAR).url());
        storage.deleteAfterCommit(old);
        return userMapper.profile(u);
    }

    /** Xóa ảnh đại diện: giao diện quay về ảnh mặc định (chữ cái đầu của tên). Chưa có ảnh thì không làm gì. */
    @Transactional
    public UserProfile removeAvatar() {
        User u = current.get();
        String old = u.getAvatarUrl();
        if (old != null) {
            u.setAvatarUrl(null);
            storage.deleteAfterCommit(old);
        }
        return userMapper.profile(u);
    }

    /** Quản lý quyền GPS. Chỉ lưu tọa độ khi người dùng cho phép; vị trí dùng cho bản đồ, bán kính sự cố và SOS. */
    @Transactional
    public UserProfile updateGps(GpsRequest r) {
        User u = current.get();
        u.setGpsPermission(r.permission());
        if (r.permission() == GpsPermission.GRANTED) {
            Validators.coords(r.latitude(), r.longitude());
            u.setLatitude(r.latitude()); u.setLongitude(r.longitude());
        } else {
            u.setLatitude(null); u.setLongitude(null); // thu hồi quyền: xóa tọa độ cũ, không dùng vị trí đã lỗi thời nữa
        }
        return userMapper.profile(u);
    }

    // ---------------- Tìm kiếm / hồ sơ công khai / theo dõi ----------------
    @Transactional(readOnly = true)
    public PageResponse<SearchResult> search(String q, String type, int page, int size) {
        Long me = current.idOrNull();
        String kw = searchKeyword(q); // CSDL so sánh không phân biệt dấu, hoa/thường (unaccent, Flyway V5)
        // Cả trang một lần: "đang theo dõi" tra một câu; tên tổ chức lấy luôn từ tổ chức vừa tìm được (không tra lại theo tài khoản)
        if ("ORG".equalsIgnoreCase(type)) {
            return PageResponse.ofAll(orgs.searchByName(OrgStatus.ACTIVE, kw, Paging.of(page, size)), list -> {
                Set<Long> followed = followedAmong(me, list.stream().map(o -> o.getUser().getId()).toList());
                return list.stream().map(o -> new SearchResult(userMapper.user(o.getUser(), Map.of(o.getUser().getId(), o.getName())),
                        orgMapper.orgPublic(o), followed.contains(o.getUser().getId()))).toList();
            });
        }
        return PageResponse.ofAll(users.searchByName(Role.CITIZEN, UserStatus.LOCKED, kw, Paging.of(page, size)), list -> {
            Set<Long> followed = followedAmong(me, list.stream().map(User::getId).toList());
            return list.stream().map(u -> new SearchResult(userMapper.user(u, Map.of()), null, followed.contains(u.getId()))).toList();
        });
    }

    /** Trong các tài khoản ids, những tài khoản người xem đang theo dõi (khách: không có). */
    private Set<Long> followedAmong(Long viewerId, List<Long> ids) {
        return viewerId == null || ids.isEmpty() ? Set.of() : new HashSet<>(follows.findFollowingIdsIn(viewerId, ids));
    }

    /** Chuẩn hóa từ khóa (NFC, gộp khoảng trắng, thoát ký tự LIKE): xem {@link SearchKeyword}. */
    static String searchKeyword(String q) { return SearchKeyword.like(q); }

    @Transactional(readOnly = true)
    public PublicProfile publicProfile(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Người dùng không tồn tại."));
        // Đăng ký chưa xác thực email: chưa phải thành viên (chưa đăng nhập được, quá 24 giờ sẽ bị xóa), không hiện hồ sơ công khai
        if (!u.isEmailVerified()) throw ApiException.notFound("Người dùng không tồn tại.");
        OrgInfo oi = null;
        if (u.getRole() == Role.RESCUE_ORG) {
            RescueOrganization o = orgs.findByUserId(id).orElse(null);
            if (o == null || o.getStatus() == OrgStatus.PENDING || o.getStatus() == OrgStatus.REJECTED)
                throw ApiException.notFound("Người dùng không tồn tại.");
            oi = orgMapper.orgPublic(o); // không lộ hồ sơ pháp lý, số nhà và tọa độ trụ sở
        }
        // Không trả email, SĐT, mật khẩu, GPS chính xác
        return new PublicProfile(userMapper.user(u), follows.countByFollowingId(id), follows.countByFollowerId(id),
                userMapper.followedBy(current.idOrNull(), id), oi, u.getCreatedAt());
    }

    @Transactional
    public void follow(Long targetId) {
        User me = current.get();
        if (me.getId().equals(targetId)) throw ApiException.bad("Bạn không thể tự theo dõi chính mình.");
        User target = users.findById(targetId).filter(User::isEmailVerified) // chưa xác thực email: coi như chưa tồn tại
                .orElseThrow(() -> ApiException.notFound("Người dùng không tồn tại."));
        if (follows.existsByFollowerIdAndFollowingId(me.getId(), targetId)) return; // không theo dõi trùng
        follows.save(new Follow(me.getId(), targetId));
        notifications.notify(target, NotificationType.FOLLOW, "Người theo dõi mới", me.getFullName() + " đã theo dõi bạn.", "USER", me.getId());
    }

    @Transactional
    public void unfollow(Long targetId) { follows.deleteByFollowerIdAndFollowingId(current.id(), targetId); }
}
