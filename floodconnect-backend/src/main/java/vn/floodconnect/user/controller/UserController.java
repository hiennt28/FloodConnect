package vn.floodconnect.user.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.dto.CommonDtos.MessageResponse;
import vn.floodconnect.common.response.PageResponse;
import vn.floodconnect.post.dto.PostDtos.PostDto;
import vn.floodconnect.post.service.PostService;
import vn.floodconnect.user.dto.UserDtos.ChangePasswordRequest;
import vn.floodconnect.user.dto.UserDtos.GpsRequest;
import vn.floodconnect.user.dto.UserDtos.PublicProfile;
import vn.floodconnect.user.dto.UserDtos.SearchResult;
import vn.floodconnect.user.dto.UserDtos.UpdateProfileRequest;
import vn.floodconnect.user.dto.UserDtos.UserProfile;
import vn.floodconnect.user.service.UserService;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService users;
    private final PostService posts;

    @GetMapping("/me") public UserProfile me() { return users.me(); }
    @PutMapping("/me") public UserProfile update(@Valid @RequestBody UpdateProfileRequest r) { return users.updateProfile(r); }
    @PutMapping("/me/password") public MessageResponse password(@Valid @RequestBody ChangePasswordRequest r) {
        users.changePassword(r);
        return new MessageResponse("Đổi mật khẩu thành công.");
    }
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserProfile avatar(@RequestPart("file") MultipartFile file) { return users.updateAvatar(file); }
    @DeleteMapping("/me/avatar") public UserProfile removeAvatar() { return users.removeAvatar(); }
    @PutMapping("/me/gps") public UserProfile gps(@Valid @RequestBody GpsRequest r) { return users.updateGps(r); }

    /** type = CITIZEN (mặc định) | ORG */
    @GetMapping("/search")
    public PageResponse<SearchResult> search(@RequestParam(defaultValue = "") String q, @RequestParam(defaultValue = "CITIZEN") String type,
                                             @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return users.search(q, type, page, size);
    }

    @GetMapping("/{id}") public PublicProfile profile(@PathVariable Long id) { return users.publicProfile(id); }
    @GetMapping("/{id}/posts")
    public PageResponse<PostDto> userPosts(@PathVariable Long id, @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "10") int size) {
        return posts.byAuthor(id, page, size);
    }
    @PostMapping("/{id}/follow") public MessageResponse follow(@PathVariable Long id) { users.follow(id); return new MessageResponse("Đã theo dõi."); }
    @DeleteMapping("/{id}/follow") public MessageResponse unfollow(@PathVariable Long id) { users.unfollow(id); return new MessageResponse("Đã bỏ theo dõi."); }
}
