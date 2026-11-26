package vn.floodconnect.post.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.post.dto.PostDtos.CommentDto;
import vn.floodconnect.post.dto.PostDtos.CommentRequest;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.dto.PostDtos.PostRequest;
import vn.floodconnect.post.dto.PostDtos.ReportRequest;
import vn.floodconnect.post.service.PostService;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class PostController {
    private final PostService posts;

    // ---- bài đăng ----
    /** filter = all | following */
    @GetMapping("/api/posts")
    public PageResponse<PostDto> feed(@RequestParam(defaultValue = "all") String filter, @RequestParam(defaultValue = "0") int page,
                                      @RequestParam(defaultValue = "10") int size) { return posts.feed(filter, page, size); }

    @GetMapping("/api/posts/{id}") public PostDto get(@PathVariable Long id) { return posts.get(id); }

    @PostMapping(value = "/api/posts", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PostDto createJson(@RequestBody PostRequest r) { return posts.create(r, List.of()); }

    /** multipart: "data" (JSON PostRequest) + "media" (≤5 ảnh JPG/PNG/WEBP, ≤5MB). */
    @PostMapping(value = "/api/posts", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostDto create(@RequestPart("data") PostRequest r, @RequestPart(value = "media", required = false) List<MultipartFile> media) {
        return posts.create(r, media);
    }

    @PutMapping(value = "/api/posts/{id}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public PostDto updateJson(@PathVariable Long id, @RequestBody PostRequest r) { return posts.update(id, r, List.of()); }

    @PutMapping(value = "/api/posts/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PostDto update(@PathVariable Long id, @RequestPart("data") PostRequest r,
                          @RequestPart(value = "media", required = false) List<MultipartFile> media) { return posts.update(id, r, media); }

    @DeleteMapping("/api/posts/{id}") public MessageResponse delete(@PathVariable Long id) { posts.delete(id); return new MessageResponse("Đã xóa bài viết."); }
    @PostMapping("/api/posts/{id}/like") public Map<String, Object> like(@PathVariable Long id) { return posts.toggleLike(id); }
    @PostMapping("/api/posts/{id}/report") public MessageResponse report(@PathVariable Long id, @Valid @RequestBody ReportRequest r) { return posts.report(id, r); }

    // ---- bình luận ----
    @GetMapping("/api/posts/{id}/comments")
    public PageResponse<CommentDto> comments(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return posts.comments(id, page, size);
    }
    /** Một bình luận (mở từ thông báo khi bình luận chưa có trong trang đã tải). */
    @GetMapping("/api/comments/{id}") public CommentDto commentById(@PathVariable Long id) { return posts.comment(id); }
    @GetMapping("/api/comments/{id}/replies")
    public PageResponse<CommentDto> replies(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return posts.replies(id, page, size);
    }
    @PostMapping("/api/posts/{id}/comments") public CommentDto comment(@PathVariable Long id, @Valid @RequestBody CommentRequest r) { return posts.addComment(id, r); }
    @PutMapping("/api/comments/{id}") public CommentDto edit(@PathVariable Long id, @Valid @RequestBody CommentRequest r) { return posts.editComment(id, r.content()); }
    @DeleteMapping("/api/comments/{id}") public MessageResponse deleteComment(@PathVariable Long id) { posts.deleteComment(id); return new MessageResponse("Đã xóa bình luận."); }
}
