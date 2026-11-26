package vn.floodconnect.organization.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import vn.floodconnect.organization.service.OrgDocumentService;
import vn.floodconnect.organization.service.OrgDocumentService.DocFile;
import java.net.URI;

/**
 * Tải giấy tờ pháp lý / CCCD của tổ chức (lưu riêng tư, không có đường dẫn công khai). Chỉ Admin và chính tổ chức sở hữu giấy tờ;
 * người khác nhận 404, khách chưa đăng nhập nhận 401 (SecurityConfig: mọi đường dẫn còn lại cần đăng nhập).
 * Nội dung trả thẳng từ backend (Cloudinary được đọc bằng chữ ký bí mật ở phía server), không đưa link tệp ra trình duyệt.
 */
@RestController
@RequestMapping("/api/org-docs")
@RequiredArgsConstructor
public class OrgDocumentController {
    private final OrgDocumentService documents;

    @GetMapping("/{id}/file")
    public ResponseEntity<byte[]> file(@PathVariable Long id) {
        DocFile d = documents.open(id);
        if (d.externalUrl() != null) // ảnh mẫu / dữ liệu cũ chưa chuyển sang chế độ riêng tư
            return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(d.externalUrl())).build();
        String ext = switch (d.file().contentType()) {
            case "application/pdf" -> ".pdf"; case "image/png" -> ".png"; case "image/jpeg" -> ".jpg"; case "image/webp" -> ".webp"; default -> "";
        };
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(d.file().contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + d.filename() + ext + "\"")
                .cacheControl(CacheControl.noStore()) // không để trình duyệt / proxy lưu bản sao giấy tờ
                .body(d.file().data());
    }
}
