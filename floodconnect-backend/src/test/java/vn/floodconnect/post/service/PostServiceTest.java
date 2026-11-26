package vn.floodconnect.post.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.dto.GeoDtos.WardRef;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.post.dto.PostDtos.CommentRequest;
import vn.floodconnect.post.dto.PostDtos.PostRequest;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;
import vn.floodconnect.common.enums.ReportStatus;
import java.util.function.IntFunction;
import org.mockito.ArgumentCaptor;
import vn.floodconnect.post.entity.Comment;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostMedia;
import vn.floodconnect.post.repository.*;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Vị trí bài viết, sửa bài, thông báo bình luận và báo cáo. */
class PostServiceTest {
    private final PostRepository posts = mock(PostRepository.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final CommentRepository comments = mock(CommentRepository.class);
    private final PostReportRepository reports = mock(PostReportRepository.class);
    private final NotificationPublisher notifications = mock(NotificationPublisher.class);
    private final PostMediaRepository mediaRepo = mock(PostMediaRepository.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final GeoService geo = mock(GeoService.class);
    private final PostService service = new PostService(posts, mediaRepo, mock(PostLikeRepository.class),
            comments, reports, mock(UserRepository.class), current,
            storage, notifications, mock(PostMapper.class), geo);

    private final User u = new User();

    @BeforeEach void setUp() {
        u.setId(1L); u.setFullName("An");
        when(current.getActive()).thenReturn(u);
    }

    // ---------- Vị trí bài đăng: chọn Tỉnh, Phường/Xã + thôn/xóm, server tự tính tọa độ ----------
    private static final WardCenter HAI_CHAU = new WardCenter("20194", "Phường Hải Châu", "48", "Thành phố Đà Nẵng", 16.06, 108.22);
    private static final WardRef HAI_CHAU_REF = new WardRef("20194", "Phường Hải Châu", "48", "Thành phố Đà Nẵng");

    @Test void locationUsesWardCenterAndServerBuildsTheAddress() {
        when(geo.center("20194")).thenReturn(HAI_CHAU);
        service.create(new PostRequest("Nước lên nhanh", "20194", "  Kiệt 12 Trần Phú ", null, null, null), List.of());
        verify(posts).save(argThat((Post p) -> "20194".equals(p.getWardCode()) && "Kiệt 12 Trần Phú".equals(p.getAddressDetail())
                && "Kiệt 12 Trần Phú, Phường Hải Châu, Thành phố Đà Nẵng".equals(p.getLocationName())
                && p.getLatitude() == 16.06 && p.getLongitude() == 108.22));
        verify(geo, never()).locate(any(), any());
    }

    @Test void blankDetailGivesWardAndProvinceOnly() {
        when(geo.center("20194")).thenReturn(HAI_CHAU);
        service.create(new PostRequest("Nước lên nhanh", "20194", "   ", null, null, null), List.of());
        verify(posts).save(argThat((Post p) -> p.getAddressDetail() == null && "Phường Hải Châu, Thành phố Đà Nẵng".equals(p.getLocationName())));
    }

    @Test void gpsInsideTheChosenWardIsKept() {
        when(geo.center("20194")).thenReturn(HAI_CHAU);
        when(geo.locate(16.0612, 108.2234)).thenReturn(new LocateResult(HAI_CHAU_REF, false));
        service.create(new PostRequest("Nước lên nhanh", "20194", null, 16.0612, 108.2234, null), List.of());
        verify(posts).save(argThat((Post p) -> p.getLatitude() == 16.0612 && p.getLongitude() == 108.2234));
    }

    /** Lấy GPS xong rồi đổi sang xã khác: GPS không còn khớp địa chỉ, dùng điểm đại diện của xã đã chọn */
    @Test void gpsOutsideTheChosenWardFallsBackToWardCenter() {
        when(geo.center("20194")).thenReturn(HAI_CHAU);
        when(geo.locate(21.03, 105.83)).thenReturn(new LocateResult(new WardRef("00004", "Phường Ba Đình", "01", "Thành phố Hà Nội"), false));
        service.create(new PostRequest("Nước lên nhanh", "20194", null, 21.03, 105.83, null), List.of());
        verify(posts).save(argThat((Post p) -> p.getLatitude() == 16.06 && p.getLongitude() == 108.22));
    }

    @Test void noWardMeansNoLocationEvenIfCoordinatesAreSent() {
        service.create(new PostRequest("Không vị trí", null, "Cầu Rồng", 16.05, 108.2, null), List.of());
        verify(posts).save(argThat((Post p) -> p.getWardCode() == null && p.getLatitude() == null && p.getLocationName() == null));
        verifyNoInteractions(geo);
    }

    @Test void addressDetailIsAtMost100Characters() {
        ApiException e = assertThrows(ApiException.class,
                () -> service.create(new PostRequest("Nội dung", "20194", "a".repeat(101), null, null, null), List.of()));
        assertEquals("INVALID_ADDRESS_DETAIL", e.getCode());
        verify(posts, never()).save(any());
    }

    /** Vị trí sai khi sửa bài: báo lỗi trước khi đụng tới ảnh */
    @Test void invalidWardOnEditDoesNotTouchImages() {
        Post p = new Post();
        p.setId(5L); p.setAuthor(u); p.setContent("Cũ");
        when(posts.findById(5L)).thenReturn(Optional.of(p));
        when(geo.center("99999")).thenThrow(ApiException.bad("INVALID_WARD", "Phường/Xã đã chọn không hợp lệ, vui lòng chọn lại."));
        assertThrows(ApiException.class, () -> service.update(5L, new PostRequest("Mới", "99999", null, null, null, List.of(11L)), List.of()));
        verify(mediaRepo, never()).deleteAll(any());
        assertEquals("Cũ", p.getContent());
    }

    // ---------- Thông báo bình luận / phản hồi mở đúng bình luận ----------
    private Post postBy(User author) {
        Post p = new Post();
        p.setId(9L); p.setAuthor(author); p.setContent("Nước lên nhanh");
        when(posts.findById(9L)).thenReturn(Optional.of(p));
        when(comments.save(any(Comment.class))).thenAnswer(inv -> { Comment c = inv.getArgument(0); c.setId(55L); return c; });
        return p;
    }

    @Test void editingPostDeletesFilesOfRemovedImagesOnly() {
        Post p = new Post();
        p.setId(5L); p.setAuthor(u); p.setContent("Cũ");
        when(posts.findById(5L)).thenReturn(Optional.of(p));
        PostMedia bo = media(11L, p, "/files/posts/bo.jpg");
        PostMedia giu = media(12L, p, "/files/posts/giu.jpg");
        when(mediaRepo.findByPostIdOrderBySortOrderAsc(5L)).thenReturn(List.of(bo, giu));

        service.update(5L, new PostRequest("Mới", null, null, null, null, List.of(11L)), List.of());

        verify(mediaRepo).deleteAll(List.of(bo));
        verify(storage).deleteAfterCommit("/files/posts/bo.jpg");
        verify(storage, never()).deleteAfterCommit("/files/posts/giu.jpg");
    }

    private static PostMedia media(Long id, Post p, String url) {
        PostMedia m = new PostMedia();
        m.setId(id); m.setPost(p); m.setMediaUrl(url);
        return m;
    }

    @Test void commentNotificationCarriesTheCommentId() {
        User owner = new User(); owner.setId(2L);
        postBy(owner);
        service.addComment(9L, new CommentRequest("Nhà tôi cũng ngập", null));
        verify(notifications).notify(eq(owner), eq(NotificationType.COMMENT), anyString(), anyString(), eq("POST"), eq(9L), eq(55L));
    }

    @Test void replyNotificationCarriesTheReplyId() {
        User owner = new User(); owner.setId(2L);
        User other = new User(); other.setId(3L);
        Post p = postBy(owner);
        Comment parent = new Comment();
        parent.setId(40L); parent.setPost(p); parent.setAuthor(other);
        when(comments.findById(40L)).thenReturn(Optional.of(parent));
        service.addComment(9L, new CommentRequest("Đã gọi cứu hộ chưa?", 40L));
        verify(notifications).notify(eq(other), eq(NotificationType.REPLY), anyString(), anyString(), eq("POST"), eq(9L), eq(55L));
        verify(notifications).notify(eq(owner), eq(NotificationType.COMMENT), anyString(), anyString(), eq("POST"), eq(9L), eq(55L));
    }

    // ---------- Báo cáo bài viết: báo Admin, gộp theo bài ----------
    @Test @SuppressWarnings("unchecked") void reportingAPostNotifiesAdminsGroupedByPost() {
        User owner = new User(); owner.setId(2L); owner.setFullName("Bình");
        postBy(owner);
        when(reports.countByPostIdAndStatus(9L, ReportStatus.PENDING)).thenReturn(3L);

        service.report(9L, new ReportRequest("fake_news", null));

        ArgumentCaptor<IntFunction<String>> content = ArgumentCaptor.forClass(IntFunction.class);
        verify(notifications).notifyAdminsGrouped(eq(NotificationType.REPORT_NEW), eq("report:post:9"), eq("Bài viết bị báo cáo"),
                content.capture(), eq("POST"), eq(9L));
        // nội dung ghi số báo cáo đang chờ (lấy từ CSDL, đúng cả khi Admin đã đọc thông báo cũ), không phụ thuộc bộ đếm của nhóm
        assertEquals("Bài viết của Bình \"Nước lên nhanh\" có 3 báo cáo chờ xử lý (mới nhất: Tin giả).", content.getValue().apply(1));
    }
}
