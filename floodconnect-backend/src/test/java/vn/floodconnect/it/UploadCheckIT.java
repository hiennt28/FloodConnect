package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * #22 qua API thật: tệp đổi đuôi giả bị từ chối, và tệp đã lưu trước đó trong CÙNG yêu cầu bị xóa khi giao dịch bị hủy
 * (không thành tệp mồ côi). Thư mục lưu của profile it: target/it-uploads (application-it.yml).
 */
class UploadCheckIT extends AbstractIT {
    private static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    private static final byte[] HEIC = { 0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0 };
    private static final Path POSTS = Path.of("target/it-uploads/posts");

    private static long files() throws IOException {
        if (!Files.exists(POSTS)) return 0;
        try (Stream<Path> s = Files.list(POSTS)) { return s.count(); }
    }

    private MockMultipartFile data() {
        return new MockMultipartFile("data", "", "application/json", "{\"content\":\"Nước lên nhanh ở thôn 3\"}".getBytes(StandardCharsets.UTF_8));
    }

    @Test void anhDoiDuoiGiaBiTuChoiVaKhongDeLaiTepNao() throws Exception {
        User u = user(Role.CITIZEN);
        long before = files();
        mvc.perform(multipart("/api/posts").file(data())
                        .file(new MockMultipartFile("media", "anh1.png", "image/png", PNG))           // ảnh thật: lưu được trước
                        .file(new MockMultipartFile("media", "IMG_0001.jpg", "image/jpeg", HEIC))     // ảnh iPhone đổi tên .jpg
                        .header("Authorization", bearer(u)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_FILE_CONTENT"));
        assertEquals(before, files(), "ảnh thứ nhất đã lưu phải bị xóa khi giao dịch bị hủy");
        assertEquals(0L, (long) jdbc.queryForObject("select count(*) from posts", Long.class));
    }

    @Test void anhThatVanDangDuoc() throws Exception {
        User u = user(Role.CITIZEN);
        long before = files();
        mvc.perform(multipart("/api/posts").file(data()).file(new MockMultipartFile("media", "anh.png", "image/png", PNG))
                        .header("Authorization", bearer(u)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.media[0].type").value("image/png"));
        assertEquals(before + 1, files());
    }
}
