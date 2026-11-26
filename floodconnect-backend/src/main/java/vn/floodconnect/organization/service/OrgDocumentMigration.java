package vn.floodconnect.organization.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;

/**
 * Tác vụ CHẠY MỘT LẦN (bật bằng MIGRATE_ORG_DOCS=true rồi tắt đi): chuyển giấy tờ pháp lý / CCCD cũ đang lưu CÔNG KHAI
 * (Cloudinary kiểu "upload" hoặc /files/org-docs/... của backend) sang chế độ riêng tư.
 * Với từng giấy tờ: đọc nội dung từ chỗ công khai -> lưu bản riêng tư -> cập nhật mã tệp trong CSDL -> XÓA bản công khai.
 * Giấy tờ đã riêng tư thì bỏ qua (chạy lại an toàn). Đường dẫn không do hệ thống lưu (ảnh mẫu placehold.co) giữ nguyên.
 * Lỗi ở một giấy tờ không chặn các giấy tờ khác; bản công khai chỉ bị xóa sau khi CSDL đã trỏ sang bản riêng tư.
 */
@Component
@Order(3)
@ConditionalOnProperty(name = "app.storage.migrate-org-docs", havingValue = "true")
@Slf4j
public class OrgDocumentMigration implements CommandLineRunner {
    private final OrganizationDocumentRepository docs;
    private final FileStorageService storage;
    private final TransactionTemplate tx;

    public OrgDocumentMigration(OrganizationDocumentRepository docs, FileStorageService storage, PlatformTransactionManager txManager) {
        this.docs = docs;
        this.storage = storage;
        this.tx = new TransactionTemplate(txManager);
    }

    @Override
    public void run(String... args) {
        int moved = 0, skipped = 0, failed = 0;
        for (OrganizationDocument d : docs.findAll()) {
            String url = d.getStorageKey();
            if (url == null || !url.matches("(?i)^https?://.*")) continue; // đã riêng tư
            if (!storage.isOwnPublicUrl(url)) {
                skipped++;
                log.info("Chuyển giấy tờ: bỏ qua id={} (đường dẫn ngoài, không do hệ thống lưu)", d.getId());
                continue;
            }
            String newKey = null;
            try {
                byte[] data = storage.readOwnPublic(url);
                newKey = storage.storePrivateBytes(data, mimeOf(d.getFileType(), url), "org-docs");
                String k = newKey;
                tx.executeWithoutResult(s -> docs.findById(d.getId()).ifPresent(x -> x.setStorageKey(k)));
                storage.deleteAfterCommit(url); // ngoài transaction: xóa bản công khai ngay sau khi CSDL đã trỏ sang bản riêng tư
                moved++;
                log.info("Chuyển giấy tờ: id={} đã chuyển sang chế độ riêng tư và xóa bản công khai", d.getId());
            } catch (Exception e) {
                failed++;
                log.error("Chuyển giấy tờ: id={} lỗi, giữ nguyên bản cũ: {}", d.getId(), e.getMessage());
                if (newKey != null) storage.deletePrivateAfterCommit(newKey); // dọn bản riêng tư vừa tạo dở
            }
        }
        log.warn("Chuyển giấy tờ sang chế độ riêng tư xong: {} đã chuyển, {} bỏ qua (đường dẫn ngoài), {} lỗi. Hãy tắt MIGRATE_ORG_DOCS.",
                moved, skipped, failed);
    }

    /** Kiểu nội dung theo loại tệp đã ghi (PDF/PNG/JPG); không rõ thì đoán theo đuôi đường dẫn. */
    static String mimeOf(String fileType, String url) {
        if (fileType != null) switch (fileType.toUpperCase()) {
            case "PDF": return "application/pdf";
            case "PNG": return "image/png";
            case "JPG": case "JPEG": return "image/jpeg";
            default: break;
        }
        String guess = FileStorageService.contentTypeOf(url);
        return guess.startsWith("application/octet") ? "image/jpeg" : guess;
    }
}
