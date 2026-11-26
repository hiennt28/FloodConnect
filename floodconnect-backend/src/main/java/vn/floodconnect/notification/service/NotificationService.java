package vn.floodconnect.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.notification.dto.NotificationDtos.NotificationDto;
import vn.floodconnect.notification.entity.Notification;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.repository.NotificationRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@RequiredArgsConstructor
public class NotificationService {
    /** Lượt tạo ra nhóm (vd. lượt thích đầu tiên) được lưu trước thông báo vài mili giây trong cùng giao dịch: so sánh có chừa sai số. */
    private static final Duration GROUP_START_TOLERANCE = Duration.ofSeconds(2);

    private final NotificationRepository repo;
    private final UserRepository users;
    private final SimpMessagingTemplate ws;
    private final CurrentUser current;

    public static NotificationDto toDto(Notification n) {
        return new NotificationDto(n.getId(), n.getType(), n.getTitle(), n.getContent(), n.getReferenceType(),
                n.getReferenceId(), n.getSubReferenceId(), n.getGroupCount(), n.getRead(), n.getCreatedAt());
    }

    /** Đẩy realtime. Nếu đang trong transaction thì chờ commit xong mới đẩy (rollback thì không đẩy gì). */
    private void push(Notification n) {
        final String userId = String.valueOf(n.getUser().getId());
        final NotificationDto dto = toDto(n);
        Runnable send = () -> {
            try { ws.convertAndSendToUser(userId, "/queue/notifications", dto); }
            catch (Exception ignored) { /* người dùng ngoại tuyến: đã lưu DB để xem lại */ }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { send.run(); }
            });
        } else {
            send.run();
        }
    }

    @Transactional
    public void notify(User to, NotificationType type, String title, String content, String refType, Long refId) {
        notify(to, type, title, content, refType, refId, null);
    }

    /** subRefId: đối tượng con để mở đúng chỗ (vd. mã bình luận trong bài viết refId). */
    @Transactional
    public void notify(User to, NotificationType type, String title, String content, String refType, Long refId, Long subRefId) {
        if (to == null) return;
        Notification n = new Notification();
        n.setUser(to); n.setType(type); n.setTitle(title); n.setContent(content);
        n.setReferenceType(refType); n.setReferenceId(refId); n.setSubReferenceId(subRefId);
        push(repo.save(n));
    }

    /** Thông báo gộp: nếu còn thông báo chưa đọc cùng groupKey thì tăng bộ đếm thay vì tạo mới. */
    @Transactional
    public void notifyGrouped(User to, NotificationType type, String groupKey, String title,
                              java.util.function.IntFunction<String> contentByCount, String refType, Long refId) {
        var existing = repo.findFirstByUserIdAndGroupKeyAndReadFalse(to.getId(), groupKey);
        Notification n = existing.orElseGet(Notification::new);
        if (existing.isPresent()) n.setGroupCount(n.getGroupCount() + 1);
        else { n.setUser(to); n.setType(type); n.setGroupKey(groupKey); n.setReferenceType(refType); n.setReferenceId(refId); n.setGroupCount(1); }
        n.setTitle(title);
        n.setContent(contentByCount.apply(n.getGroupCount()));
        push(repo.save(n));
    }

    /**
     * Rút một lượt khỏi thông báo gộp chưa đọc (vd. bỏ thích), để thích rồi bỏ thích rồi thích lại không bị đếm hai lần.
     * Chỉ rút khi lượt đó được tính vào nhóm đang mở (xảy ra sau khi nhóm bắt đầu); nhóm còn 1 lượt thì xóa thông báo.
     * Không đẩy realtime: người nhận thấy số mới ở lần tải lại kế tiếp, không nhận thêm thông báo "lượt thích mới".
     */
    @Transactional
    public void retractGrouped(User to, String groupKey, Instant contributedAt, java.util.function.IntFunction<String> contentByCount) {
        if (to == null || contributedAt == null) return;
        repo.findFirstByUserIdAndGroupKeyAndReadFalse(to.getId(), groupKey).ifPresent(n -> {
            if (contributedAt.isBefore(n.getCreatedAt().minus(GROUP_START_TOLERANCE))) return; // lượt cũ, thuộc nhóm đã đọc trước đó
            if (n.getGroupCount() <= 1) { repo.delete(n); return; }
            n.setGroupCount(n.getGroupCount() - 1);
            n.setContent(contentByCount.apply(n.getGroupCount()));
        });
    }

    @Transactional
    public void notifyUserId(Long userId, NotificationType type, String title, String content, String refType, Long refId) {
        users.findById(userId).ifPresent(u -> notify(u, type, title, content, refType, refId));
    }

    /** Admin đang làm việc: bỏ tài khoản Admin bị Admin gốc vô hiệu hóa (không đăng nhập được nên không cần nhận thông báo). */
    private List<User> activeAdmins() {
        return users.findByRole(Role.ADMIN).stream().filter(a -> !a.isLocked()).toList();
    }

    @Transactional
    public void notifyAdmins(NotificationType type, String title, String content, String refType, Long refId) {
        for (User a : activeAdmins()) notify(a, type, title, content, refType, refId);
    }

    /** Thông báo gộp tới mọi Admin (vd. nhiều người cùng báo cáo một bài: mỗi Admin chỉ có một thông báo, nội dung được cập nhật). */
    @Transactional
    public void notifyAdminsGrouped(NotificationType type, String groupKey, String title,
                                    java.util.function.IntFunction<String> contentByCount, String refType, Long refId) {
        for (User a : activeAdmins()) notifyGrouped(a, type, groupKey, title, contentByCount, refType, refId);
    }

    /** Việc đã được xử lý: đánh dấu đã đọc mọi thông báo chưa đọc của nhóm, để lần sau phát sinh lại thì mở nhóm mới, đếm lại từ đầu. */
    @Transactional
    public void closeGroup(String groupKey) { repo.markGroupRead(groupKey); }

    @Transactional(readOnly = true)
    public PageResponse<NotificationDto> list(int page, int size) {
        return PageResponse.of(repo.findByUserIdOrderByCreatedAtDesc(current.id(), Paging.of(page, size)), NotificationService::toDto);
    }

    @Transactional(readOnly = true)
    public long unreadCount() { return repo.countByUserIdAndReadFalse(current.id()); }

    @Transactional
    public void markRead(Long id) {
        Notification n = repo.findById(id).orElseThrow(() -> ApiException.notFound("Thông báo không tồn tại."));
        if (!n.getUser().getId().equals(current.id())) throw ApiException.forbidden("Bạn chỉ xem được thông báo của mình.");
        n.setRead(true);
    }

    @Transactional
    public int markAllRead() { return repo.markAllRead(current.id()); }
}