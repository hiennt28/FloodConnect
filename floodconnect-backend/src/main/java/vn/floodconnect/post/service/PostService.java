package vn.floodconnect.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.util.Paging;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.enums.ReportStatus;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.service.GeoService;
import vn.floodconnect.notification.entity.NotificationType;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.post.dto.PostDtos.CommentDto;
import vn.floodconnect.post.dto.PostDtos.CommentRequest;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.dto.PostDtos.PostRequest;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;
import vn.floodconnect.post.entity.Comment;
import vn.floodconnect.post.entity.ContentStatus;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostLike;
import vn.floodconnect.post.entity.PostMedia;
import vn.floodconnect.post.entity.PostReport;
import vn.floodconnect.post.entity.PostReportReason;
import vn.floodconnect.post.repository.CommentRepository;
import vn.floodconnect.post.repository.PostLikeRepository;
import vn.floodconnect.post.repository.PostMediaRepository;
import vn.floodconnect.post.repository.PostReportRepository;
import vn.floodconnect.post.repository.PostRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class PostService {
    public static final int MAX_FILES = 5;
    private final PostRepository posts;
    private final PostMediaRepository media;
    private final PostLikeRepository likes;
    private final CommentRepository comments;
    private final PostReportRepository reports;
    private final UserRepository users;
    private final CurrentUser current;
    private final FileStorageService storage;
    private final NotificationPublisher notifications;
    private final PostMapper postMapper;
    private final GeoService geo;

    // ---------------- Bảng tin ----------------
    @Transactional(readOnly = true)
    public PageResponse<PostDto> feed(String filter, int page, int size) {
        Long me = current.idOrNull();
        PageRequest pr = Paging.of(page, size);
        Page<Post> p;
        if ("following".equalsIgnoreCase(filter)) {
            if (me == null) throw ApiException.unauthorized("Vui lòng đăng nhập để xem tab Đang theo dõi.");
            p = posts.findFollowing(me, ContentStatus.VISIBLE, pr);
        } else p = posts.findByStatusOrderByCreatedAtDesc(ContentStatus.VISIBLE, pr);
        return PageResponse.ofAll(p, l -> postMapper.posts(l, me));
    }

    @Transactional(readOnly = true)
    public PageResponse<PostDto> byAuthor(Long authorId, int page, int size) {
        Long me = current.idOrNull();
        return PageResponse.ofAll(posts.findByAuthorIdAndStatusOrderByCreatedAtDesc(authorId, ContentStatus.VISIBLE, Paging.of(page, size)),
                l -> postMapper.posts(l, me));
    }

    private Post visible(Long id) {
        Post p = posts.findById(id).orElseThrow(() -> ApiException.notFound("Bài viết không tồn tại hoặc đã bị gỡ."));
        if (p.getStatus() != ContentStatus.VISIBLE) throw ApiException.notFound("Bài viết không tồn tại hoặc đã bị gỡ.");
        return p;
    }

    @Transactional(readOnly = true)
    public PostDto get(Long id) { return postMapper.post(visible(id), current.idOrNull()); }

    // ---------------- Đăng / sửa / xóa ----------------
    private String validContent(String content) {
        String c = content == null ? "" : content.trim();
        if (c.isEmpty() || c.length() > 2000) throw ApiException.bad("INVALID_CONTENT", "Nội dung bài đăng phải từ 1 đến 2.000 ký tự.");
        return c;
    }

    /** Phần địa chỉ người đăng tự gõ (thôn/xóm, số nhà, đường) tối đa 100 ký tự, để địa chỉ ghép xong vừa cột 200 ký tự. */
    static final int MAX_ADDRESS_DETAIL = 100;

    /** Vị trí đã kiểm tra, sẵn sàng ghi vào bài. NONE: bài không có vị trí. */
    record Location(String wardCode, String addressDetail, Double lat, Double lng, String name) {
        static final Location NONE = new Location(null, null, null, null, null);
        void applyTo(Post p) {
            p.setWardCode(wardCode); p.setAddressDetail(addressDetail); p.setLatitude(lat); p.setLongitude(lng); p.setLocationName(name);
        }
    }

    /**
     * Người dân không biết vĩ độ, kinh độ nên chỉ chọn Tỉnh, Phường/Xã và gõ thôn/xóm, số nhà. Server tự quyết định tọa độ:
     *  - Có GPS (bấm "Vị trí GPS") và GPS nằm đúng xã đã chọn: dùng GPS, chính xác hơn.
     *  - Không có GPS, hoặc GPS nằm ở xã khác (người đăng đã đổi xã sau khi lấy GPS): dùng điểm đại diện của xã.
     * Tên tỉnh, xã lấy từ bảng địa giới chứ không tin chuỗi client gửi; địa chỉ hiển thị do server ghép.
     */
    private Location location(PostRequest r) {
        if (r.wardCode() == null || r.wardCode().isBlank()) return Location.NONE;
        String detail = r.addressDetail() == null || r.addressDetail().isBlank() ? null : r.addressDetail().trim();
        if (detail != null && detail.length() > MAX_ADDRESS_DETAIL)
            throw ApiException.bad("INVALID_ADDRESS_DETAIL", "Thôn/xóm, số nhà, đường tối đa " + MAX_ADDRESS_DETAIL + " ký tự.");
        WardCenter w = geo.center(r.wardCode().trim());
        double lat = w.lat(), lng = w.lng();
        if (r.latitude() != null || r.longitude() != null) {
            Validators.coords(r.latitude(), r.longitude());
            LocateResult at = geo.locate(r.latitude(), r.longitude());
            if (at.ward() != null && at.ward().code().equals(w.code())) { lat = r.latitude(); lng = r.longitude(); }
        }
        String name = String.join(", ", Stream.of(detail, w.name(), w.provinceName()).filter(Objects::nonNull).toList());
        return new Location(w.code(), detail, lat, lng, name.length() > 200 ? name.substring(0, 200) : name);
    }

    private void addMedia(Post p, List<MultipartFile> files, int startOrder) {
        int order = startOrder;
        for (MultipartFile f : files) {
            var s = storage.store(f, "posts", FileStorageService.IMAGES);
            PostMedia m = new PostMedia();
            m.setPost(p); m.setMediaUrl(s.url()); m.setMediaType(s.mime()); m.setSortOrder(order++);
            media.save(m);
        }
    }

    @Transactional
    public PostDto create(PostRequest r, List<MultipartFile> files) {
        User u = current.getActive();
        String content = validContent(r.content());
        List<MultipartFile> fs = FileStorageService.nonEmpty(files);
        if (fs.size() > MAX_FILES) throw ApiException.bad("TOO_MANY_FILES", "Tối đa 5 tệp đính kèm mỗi bài.");
        Location loc = location(r);
        Post p = new Post();
        p.setAuthor(u); p.setContent(content); loc.applyTo(p);
        posts.save(p);
        addMedia(p, fs, 0);
        return postMapper.post(p, u.getId());
    }

    @Transactional
    public PostDto update(Long id, PostRequest r, List<MultipartFile> newFiles) {
        User u = current.getActive();
        Post p = posts.findById(id).orElseThrow(() -> ApiException.notFound("Bài viết không còn tồn tại hoặc không thể chỉnh sửa."));
        if (p.getStatus() != ContentStatus.VISIBLE) throw ApiException.notFound("Bài viết không còn tồn tại hoặc không thể chỉnh sửa.");
        if (!p.getAuthor().getId().equals(u.getId())) throw ApiException.forbidden("Chỉ chủ bài đăng mới được chỉnh sửa.");
        String content = validContent(r.content());
        Location loc = location(r); // kiểm tra trước khi xóa ảnh, để vị trí sai không làm mất ảnh

        List<PostMedia> existing = media.findByPostIdOrderBySortOrderAsc(id);
        List<Long> remove = r.removeMediaIds() == null ? List.of() : r.removeMediaIds();
        List<PostMedia> toRemove = existing.stream().filter(m -> remove.contains(m.getId())).toList();
        List<MultipartFile> fs = FileStorageService.nonEmpty(newFiles);
        if (existing.size() - toRemove.size() + fs.size() > MAX_FILES)
            throw ApiException.bad("TOO_MANY_FILES", "Tổng số tệp sau chỉnh sửa tối đa 5.");
        media.deleteAll(toRemove);
        toRemove.forEach(m -> storage.deleteAfterCommit(m.getMediaUrl())); // ảnh bị bỏ khi sửa bài: xóa cả tệp, sau khi lưu thành công
        addMedia(p, fs, existing.size() + 1);

        p.setContent(content); loc.applyTo(p); p.setEdited(true);
        return postMapper.post(p, u.getId());
    }

    @Transactional
    public void delete(Long id) {
        User u = current.get();
        Post p = posts.findById(id).orElseThrow(() -> ApiException.notFound("Bài viết không tồn tại."));
        if (p.getStatus() == ContentStatus.DELETED) throw ApiException.notFound("Bài viết không tồn tại.");
        if (!p.getAuthor().getId().equals(u.getId())) throw ApiException.forbidden("Chỉ chủ bài đăng mới được xóa bài của mình.");
        p.setStatus(ContentStatus.DELETED); // xóa mềm: giữ bằng chứng khi có báo cáo
        p.setDeletedAt(Instant.now());
        reports.completeAll(id, ReportStatus.PENDING, ReportStatus.COMPLETED); // báo cáo đang chờ: "Bài đã bị xóa"
    }

    // ---------------- Thả tim ----------------
    @Transactional
    public Map<String, Object> toggleLike(Long postId) {
        User u = current.getActive();
        Post p = visible(postId);
        boolean liked;
        var existing = likes.findById(new PostLike.Key(postId, u.getId()));
        if (existing.isPresent()) {
            likes.delete(existing.get());
            liked = false;
            // Rút lượt này khỏi thông báo gộp chưa đọc của chủ bài, để thích lại sau đó không bị đếm thành người thứ hai
            if (!p.getAuthor().getId().equals(u.getId()))
                notifications.retractGrouped(p.getAuthor(), "like:" + postId, existing.get().getCreatedAt(),
                        n -> n + " người đã thích bài viết của bạn.");
        } else {
            liked = true;
            try {
                likes.saveAndFlush(new PostLike(postId, u.getId()));
            } catch (DataIntegrityViolationException dup) {
                // Bấm thích 2 lần gần như đồng thời (race): coi như đã thích, không báo lỗi, không gửi thêm thông báo
                return Map.of("liked", true, "likeCount", likes.countByPostId(postId));
            }
            if (!p.getAuthor().getId().equals(u.getId())) {
                String name = u.getFullName();
                notifications.notifyGrouped(p.getAuthor(), NotificationType.LIKE, "like:" + postId, "Lượt thích mới",
                        n -> n <= 1 ? name + " đã thích bài viết của bạn." : name + " và " + (n - 1) + " người khác đã thích bài viết của bạn.",
                        "POST", postId);
            }
        }
        return Map.of("liked", liked, "likeCount", likes.countByPostId(postId));
    }

    // ---------------- Bình luận ----------------
    @Transactional(readOnly = true)
    public PageResponse<CommentDto> comments(Long postId, int page, int size) {
        visible(postId);
        return PageResponse.ofAll(comments.findTopLevel(postId, ContentStatus.VISIBLE, Paging.of(page, size)), postMapper::comments);
    }

    @Transactional(readOnly = true)
    public PageResponse<CommentDto> replies(Long commentId, int page, int size) {
        Comment parent = comments.findById(commentId).orElseThrow(() -> ApiException.notFound("Bình luận không tồn tại."));
        visible(parent.getPost().getId()); // chặn đọc phản hồi của bài đã bị gỡ/xóa
        return PageResponse.ofAll(comments.findByParentCommentIdAndStatusOrderByCreatedAtAsc(commentId, ContentStatus.VISIBLE, Paging.of(page, size)),
                postMapper::comments);
    }

    /**
     * Một bình luận theo mã: mở từ thông báo (?comment=) khi bình luận chưa có trong các trang đã tải (bình luận cũ, hoặc phản hồi
     * của bình luận chưa mở). Trả cả bình luận đã xóa (deleted = true, không nội dung) để giao diện báo "đã bị xóa".
     */
    @Transactional(readOnly = true)
    public CommentDto comment(Long id) {
        Comment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Bình luận không tồn tại."));
        visible(c.getPost().getId()); // bài đã bị gỡ / xóa thì không xem được bình luận
        return postMapper.comment(c);
    }

    /** Trích đoạn đầu bài viết để Admin nhận ra bài trong thông báo. */
    private static String excerpt(String content) {
        if (content == null || content.isBlank()) return "";
        String t = content.strip().replaceAll("\\s+", " ");
        return " \"" + (t.length() > 60 ? t.substring(0, 60).strip() + "…" : t) + "\"";
    }

    private String validComment(String c) {
        String t = c == null ? "" : c.trim();
        if (t.isEmpty() || t.length() > 1000) throw ApiException.bad("INVALID_CONTENT", "Bình luận phải từ 1 đến 1.000 ký tự.");
        return t;
    }

    @Transactional
    public CommentDto addComment(Long postId, CommentRequest r) {
        User u = current.getActive();
        Post p = visible(postId);
        String content = validComment(r.content());
        Comment c = new Comment();
        c.setPost(p); c.setAuthor(u); c.setContent(content);
        User replyTo = null;
        if (r.parentCommentId() != null) {
            Comment target = comments.findById(r.parentCommentId()).orElseThrow(() -> ApiException.bad("Không thể trả lời bình luận này."));
            if (target.getStatus() == ContentStatus.DELETED || !target.getPost().getId().equals(postId))
                throw ApiException.bad("Không thể trả lời bình luận này.");
            // phản hồi luôn gắn vào bình luận cấp 1
            Comment root = target.getParentComment() != null ? target.getParentComment() : target;
            c.setParentComment(root);
            replyTo = target.getAuthor();
            c.setReplyToUser(replyTo);
        }
        comments.save(c);

        String name = u.getFullName();
        if (replyTo != null && !replyTo.getId().equals(u.getId()))
            notifications.notify(replyTo, NotificationType.REPLY, "Phản hồi mới", name + " đã trả lời bình luận của bạn.", "POST", postId, c.getId());
        User owner = p.getAuthor();
        boolean ownerAlreadyNotified = replyTo != null && replyTo.getId().equals(owner.getId());
        if (!owner.getId().equals(u.getId()) && !ownerAlreadyNotified)
            notifications.notify(owner, NotificationType.COMMENT, "Bình luận mới", name + " đã bình luận bài viết của bạn.", "POST", postId, c.getId());
        return postMapper.comment(c);
    }

    private Comment ownComment(Long id) {
        Comment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Bình luận không tồn tại."));
        if (c.getStatus() == ContentStatus.DELETED || c.getPost().getStatus() != ContentStatus.VISIBLE)
            throw ApiException.notFound("Bình luận không còn tồn tại hoặc không thể chỉnh sửa.");
        return c;
    }

    @Transactional
    public CommentDto editComment(Long id, String content) {
        User u = current.getActive();
        Comment c = ownComment(id);
        if (!c.getAuthor().getId().equals(u.getId())) throw ApiException.forbidden("Chỉ chủ bình luận mới được chỉnh sửa.");
        c.setContent(validComment(content));
        c.setEdited(true);
        return postMapper.comment(c);
    }

    @Transactional
    public void deleteComment(Long id) {
        User u = current.get();
        Comment c = comments.findById(id).orElseThrow(() -> ApiException.notFound("Bình luận không tồn tại."));
        if (c.getStatus() == ContentStatus.DELETED) throw ApiException.notFound("Bình luận không tồn tại.");
        boolean allowed = c.getAuthor().getId().equals(u.getId()) || c.getPost().getAuthor().getId().equals(u.getId()) || u.getRole() == Role.ADMIN;
        if (!allowed) throw ApiException.forbidden("Bạn không có quyền xóa bình luận này.");
        c.setStatus(ContentStatus.DELETED); // xóa mềm
        c.setDeletedAt(Instant.now());
    }

    // ---------------- Báo cáo bài đăng ----------------
    @Transactional
    public MessageResponse report(Long postId, ReportRequest r) {
        User u = current.getActive();
        Post p = visible(postId);
        if (p.getAuthor().getId().equals(u.getId())) throw ApiException.bad("Bạn không thể tự báo cáo bài của mình.");
        if (reports.existsByPostIdAndReporterId(postId, u.getId())) throw ApiException.conflict("ALREADY_REPORTED", "Bạn đã báo cáo bài viết này rồi.");
        PostReportReason reason;
        try { reason = PostReportReason.valueOf(r.reason().trim().toUpperCase()); }
        catch (Exception e) { throw ApiException.bad("Lý do báo cáo không hợp lệ (FAKE_NEWS, INAPPROPRIATE, SPAM, HARASSMENT, OTHER)."); }
        PostReport pr = new PostReport();
        pr.setPost(p); pr.setReporter(u); pr.setReason(reason); pr.setDescription(r.description());
        reports.save(pr);
        // Báo Admin, gộp theo bài: nhiều người cùng báo cáo thì mỗi Admin chỉ có một thông báo, ghi số báo cáo đang chờ xử lý
        long pending = reports.countByPostIdAndStatus(postId, ReportStatus.PENDING);
        String body = "Bài viết của " + p.getAuthor().getFullName() + excerpt(p.getContent()) + " có " + pending
                + " báo cáo chờ xử lý (mới nhất: " + reason.label + ").";
        notifications.notifyAdminsGrouped(NotificationType.REPORT_NEW, NotificationPublisher.reportGroupKey("post", postId),
                "Bài viết bị báo cáo", n -> body, "POST", postId);
        return new MessageResponse("Đã ghi nhận báo cáo của bạn.");
    }
}
