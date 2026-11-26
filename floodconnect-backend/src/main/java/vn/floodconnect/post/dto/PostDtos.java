package vn.floodconnect.post.dto;

import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import vn.floodconnect.common.dto.CommonDtos.MediaDto;
import vn.floodconnect.post.entity.ContentStatus;
import vn.floodconnect.user.dto.UserDtos.UserSummary;

public final class PostDtos {
    private PostDtos() {}

    /**
     * Vị trí bài đăng (không bắt buộc): wardCode = mã phường/xã chọn ở giao diện, addressDetail = thôn/xóm, số nhà, đường.
     * latitude/longitude chỉ gửi khi người đăng bấm "Vị trí GPS"; không gửi thì server lấy điểm đại diện của xã.
     * wardCode trống: bài không có vị trí (sửa bài mà để trống là bỏ vị trí).
     */
    public record PostRequest(String content, String wardCode, String addressDetail, Double latitude, Double longitude,
                              List<Long> removeMediaIds) {}
    public record CommentRequest(@NotBlank(message = "Nội dung không được để trống.") String content, Long parentCommentId) {}
    public record ReportRequest(@NotBlank String reason, String description) {}
    public record PostDto(Long id, UserSummary author, String content, Double latitude, Double longitude, String locationName,
                          String wardCode, String addressDetail,
                          List<MediaDto> media, long likeCount, long commentCount, boolean likedByMe, boolean edited,
                          ContentStatus status, Instant createdAt, Instant updatedAt) {}
    public record CommentDto(Long id, Long postId, Long parentCommentId, UserSummary author, UserSummary replyTo,
                             String content, boolean deleted, boolean edited, long replyCount, Instant createdAt) {}
}
