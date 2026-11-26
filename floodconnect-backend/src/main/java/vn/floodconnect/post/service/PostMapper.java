package vn.floodconnect.post.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import vn.floodconnect.common.dto.CommonDtos.MediaDto;
import vn.floodconnect.common.util.Counts;
import vn.floodconnect.post.dto.PostDtos.CommentDto;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.entity.Comment;
import vn.floodconnect.post.entity.ContentStatus;
import vn.floodconnect.post.entity.Post;
import vn.floodconnect.post.entity.PostMedia;
import vn.floodconnect.post.repository.CommentRepository;
import vn.floodconnect.post.repository.PostLikeRepository;
import vn.floodconnect.post.repository.PostMediaRepository;
import vn.floodconnect.user.entity.User;
import vn.floodconnect.user.service.UserMapper;
import java.util.*;

/**
 * Chuyển bài đăng, bình luận sang DTO. Phải gọi trong transaction (có quan hệ lazy).
 * Chuyển cả danh sách một lần (posts, comments): ảnh, số lượt thích, "tôi đã thích", số bình luận, số trả lời, tên tổ chức của
 * tác giả đều tra MỘT câu cho cả trang (IN (...) / GROUP BY), không tra riêng từng bài (lỗi N+1: 10 bài từng tốn ~50 câu SQL).
 * Bản một phần tử (post, comment) đi cùng đường đó với danh sách 1 phần tử.
 */
@Component
@RequiredArgsConstructor
public class PostMapper {
    private final PostMediaRepository postMedia;
    private final PostLikeRepository likes;
    private final CommentRepository commentRepo;
    private final UserMapper userMapper;

    // ---------- bài đăng ----------
    public PostDto post(Post p, Long viewerId) { return posts(List.of(p), viewerId).get(0); }

    /** Cùng thứ tự với list. viewerId null (khách, Admin xem báo cáo) thì likedByMe luôn false. */
    public List<PostDto> posts(List<Post> list, Long viewerId) {
        if (list.isEmpty()) return List.of();
        List<Long> ids = list.stream().map(Post::getId).toList();
        Map<Long, List<MediaDto>> media = new HashMap<>();
        for (PostMedia m : postMedia.findByPostIdInOrderBySortOrderAsc(ids))
            media.computeIfAbsent(m.getPost().getId(), k -> new ArrayList<>()).add(new MediaDto(m.getId(), m.getMediaUrl(), m.getMediaType()));
        Map<Long, Long> likeCount = Counts.byId(likes.countByPostIds(ids));
        Set<Long> liked = viewerId == null ? Set.of() : new HashSet<>(likes.findLikedPostIds(viewerId, ids));
        Map<Long, Long> commentCount = Counts.byId(commentRepo.countByPostIds(ids, ContentStatus.VISIBLE));
        Map<Long, String> orgNames = userMapper.orgNames(list.stream().map(Post::getAuthor).toList());
        return list.stream().map(p -> new PostDto(p.getId(), userMapper.user(p.getAuthor(), orgNames), p.getContent(),
                p.getLatitude(), p.getLongitude(), p.getLocationName(), p.getWardCode(), p.getAddressDetail(),
                media.getOrDefault(p.getId(), List.of()), likeCount.getOrDefault(p.getId(), 0L), commentCount.getOrDefault(p.getId(), 0L),
                liked.contains(p.getId()), p.isEdited(), p.getStatus(), p.getCreatedAt(), p.getUpdatedAt())).toList();
    }

    // ---------- bình luận ----------
    public CommentDto comment(Comment c) { return comments(List.of(c)).get(0); }

    /** Cùng thứ tự với list. Số trả lời chỉ tính cho bình luận gốc (trả lời không có trả lời con). */
    public List<CommentDto> comments(List<Comment> list) {
        if (list.isEmpty()) return List.of();
        List<Long> topIds = list.stream().filter(c -> c.getParentComment() == null).map(Comment::getId).toList();
        Map<Long, Long> replies = topIds.isEmpty() ? Map.of() : Counts.byId(commentRepo.countRepliesByParentIds(topIds, ContentStatus.VISIBLE));
        List<User> people = new ArrayList<>();
        for (Comment c : list) if (c.getStatus() != ContentStatus.DELETED) { people.add(c.getAuthor()); people.add(c.getReplyToUser()); }
        Map<Long, String> orgNames = userMapper.orgNames(people);
        return list.stream().map(c -> {
            boolean deleted = c.getStatus() == ContentStatus.DELETED;
            return new CommentDto(c.getId(), c.getPost().getId(), c.getParentComment() == null ? null : c.getParentComment().getId(),
                    deleted ? null : userMapper.user(c.getAuthor(), orgNames), deleted ? null : userMapper.user(c.getReplyToUser(), orgNames),
                    deleted ? "Bình luận đã bị xóa" : c.getContent(), deleted, c.isEdited(),
                    c.getParentComment() == null ? replies.getOrDefault(c.getId(), 0L) : 0, c.getCreatedAt());
        }).toList();
    }
}
