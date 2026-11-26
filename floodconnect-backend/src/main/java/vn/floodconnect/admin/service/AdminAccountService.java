package vn.floodconnect.admin.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.admin.dto.AdminDtos.CitizenAdminDetail;
import vn.floodconnect.admin.dto.AdminDtos.CitizenAdminRow;
import vn.floodconnect.admin.dto.AdminDtos.LockRequest;
import vn.floodconnect.admin.dto.AdminDtos.ViolationItem;
import vn.floodconnect.common.enums.ModerationDecision;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.util.SearchKeyword;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.user.service.UserMapper;
import vn.floodconnect.post.repository.PostModerationRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.entity.UserStatus;
import vn.floodconnect.user.repository.UserRepository;
import vn.floodconnect.user.service.AccountLockService;
import java.time.*;
import java.util.*;

/** Admin quản lý tài khoản người dân: danh sách vi phạm, khóa/mở khóa. */
@Service
@RequiredArgsConstructor
public class AdminAccountService {
    private final UserRepository users;
    private final PostModerationRepository postModerations;
    private final AccountLockService lockService;
    private final CurrentUser current;
    private final UserMapper userMapper;

    // =====================================================================
    //  Người dân
    // =====================================================================
    /**
     * Dòng danh sách cho nhiều người một lần: số bài bị xóa và lần bị xóa gần nhất tra GỘP trong một truy vấn cho cả danh sách,
     * thay vì hai truy vấn cho mỗi người (lỗi N+1).
     */
    private List<CitizenAdminRow> rows(List<User> list) {
        if (list.isEmpty()) return List.of();
        Map<Long, Object[]> stats = new HashMap<>();
        for (Object[] r : postModerations.countByAuthors(list.stream().map(User::getId).toList(), ModerationDecision.REMOVE))
            stats.put((Long) r[0], r);
        return list.stream().map(u -> {
            Object[] s = stats.get(u.getId());
            return new CitizenAdminRow(u.getId(), u.getFullName(), u.getEmail(), u.getStatus(),
                    s == null ? 0 : ((Number) s[1]).longValue(), s == null ? null : (Instant) s[2], u.getLockedUntil(),
                    u.getPhone(), u.isEmailVerified());
        }).toList();
    }

    /** filter = VIOLATING (có bài bị xóa, chưa khóa, nhiều trước) | LOCKED (khóa lâu nhất trước). */
    @Transactional(readOnly = true)
    public List<CitizenAdminRow> listCitizens(String filter) {
        if ("LOCKED".equalsIgnoreCase(filter)) return rows(users.findLocked(Role.CITIZEN, UserStatus.LOCKED));
        // Chỉ người dân có bài bị xóa (một truy vấn), không tải toàn bộ người dân rồi lọc
        return rows(postModerations.findModeratedAuthors(ModerationDecision.REMOVE, Role.CITIZEN, UserStatus.ACTIVE)).stream()
                .filter(r -> r.removedPosts() > 0)
                .sorted(Comparator.comparingLong(CitizenAdminRow::removedPosts).reversed()).toList();
    }

    /**
     * Tab "Tất cả người dân": mọi tài khoản người dân (đang hoạt động, bị khóa, chưa xác thực email), tìm theo tên (không phân biệt
     * dấu, hoa/thường), email hoặc số điện thoại; phân trang để không tải hết người dân một lần.
     */
    @Transactional(readOnly = true)
    public PageResponse<CitizenAdminRow> searchCitizens(String q, int page, int size) {
        return PageResponse.ofAll(users.adminSearchCitizens(SearchKeyword.like(q), Paging.of(page, size)), this::rows);
    }

    private User citizen(Long id) {
        User u = users.findById(id).orElseThrow(() -> ApiException.notFound("Người dùng không tồn tại."));
        if (u.getRole() != Role.CITIZEN) throw ApiException.bad("Tài khoản này không phải người dân.");
        return u;
    }

    @Transactional(readOnly = true)
    public CitizenAdminDetail citizenDetail(Long id) {
        User u = citizen(id);
        List<ViolationItem> v = postModerations.findByAuthor(id, ModerationDecision.REMOVE).stream()
                .map(m -> new ViolationItem(m.getPost().getId(), m.getPost().getContent(), m.getReason(), m.getCreatedAt(), m.getDescription())).toList();
        return new CitizenAdminDetail(userMapper.profile(u), v.size(), v);
    }

    @Transactional
    public UserProfile lockCitizen(Long id, LockRequest r) {
        User u = citizen(id);
        if (u.isLocked()) throw ApiException.conflict("ALREADY_LOCKED", "Tài khoản đã bị khóa.");
        lockService.lock(u, current.get(), r.reason(), r.months() == null ? 1 : r.months());
        return userMapper.profile(u);
    }

    @Transactional
    public UserProfile unlockCitizen(Long id) {
        User u = citizen(id);
        if (u.getStatus() != UserStatus.LOCKED) throw ApiException.conflict("NOT_LOCKED", "Tài khoản không bị khóa.");
        lockService.unlock(u, current.get());
        return userMapper.profile(u);
    }
}
