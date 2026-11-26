package vn.floodconnect.common.storage;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.floodconnect.common.storage.FileStorageService.CloudTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Xóa tệp đã lưu: chỉ xóa tệp do hệ thống lưu, không thoát ra ngoài thư mục lưu, và chỉ xóa sau khi giao dịch commit. */
class FileStorageServiceTest {
    @TempDir Path root;
    private final FileStorageService storage = new FileStorageService();
    private Path avatar;

    @BeforeEach void setUp() throws Exception {
        ReflectionTestUtils.setField(storage, "dir", root.toString());
        ReflectionTestUtils.setField(storage, "publicUrl", "http://localhost:8080/files");
        ReflectionTestUtils.setField(storage, "cloudName", "");
        ReflectionTestUtils.setField(storage, "cloudFolder", "floodconnect");
        Files.createDirectories(root.resolve("avatars"));
        avatar = Files.writeString(root.resolve("avatars/a.jpg"), "x");
    }

    @AfterEach void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) TransactionSynchronizationManager.clearSynchronization();
    }

    @Test void ngoaiGiaoDichThiXoaNgayTepCuaHeThong() {
        storage.deleteAfterCommit("http://localhost:8080/files/avatars/a.jpg");
        assertFalse(Files.exists(avatar));
    }

    @Test void trongGiaoDichChiXoaSauKhiCommit() {
        TransactionSynchronizationManager.initSynchronization();
        storage.deleteAfterCommit("http://localhost:8080/files/avatars/a.jpg");
        assertTrue(Files.exists(avatar), "chưa commit thì tệp phải còn (giao dịch có thể bị hủy)");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        assertFalse(Files.exists(avatar));
    }

    @Test void giaoDichBiHuyThiKhongXoa() {
        TransactionSynchronizationManager.initSynchronization();
        storage.deleteAfterCommit("http://localhost:8080/files/avatars/a.jpg");
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertTrue(Files.exists(avatar));
    }

    @Test void khongThoatRaNgoaiThuMucLuu() throws Exception {
        Path outside = Files.writeString(root.getParent().resolve("ngoai-" + root.getFileName() + ".txt"), "bí mật");
        try {
            storage.deleteAfterCommit("http://localhost:8080/files/../" + outside.getFileName());
            assertTrue(Files.exists(outside));
            assertNull(FileStorageService.localPath(root.toString(), "../../etc/passwd"));
            assertNull(FileStorageService.localPath(root.toString(), "avatars/../.."));
            assertEquals(avatar.toAbsolutePath().normalize(), FileStorageService.localPath(root.toString(), "avatars/a.jpg"));
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test void urlNgoaiHoacRongThiBoQuaKhongLoi() {
        assertDoesNotThrow(() -> storage.deleteAfterCommit("https://example.com/avatar.jpg"));
        assertDoesNotThrow(() -> storage.deleteAfterCommit(null));
        assertDoesNotThrow(() -> storage.deleteAfterCommit("http://localhost:8080/files/avatars/khong-co.jpg"));
        assertTrue(Files.exists(avatar));
    }

    @Test void tachPublicIdTuUrlCloudinary() {
        String img = "https://res.cloudinary.com/demo/image/upload/v1712345678/floodconnect/avatars/abc-123.jpg";
        assertEquals(new CloudTarget("image", "floodconnect/avatars/abc-123"), FileStorageService.cloudinaryTarget(img, "demo", "floodconnect"));
        String pdf = "https://res.cloudinary.com/demo/raw/upload/v1/floodconnect/org-docs/x.pdf";
        assertEquals(new CloudTarget("raw", "floodconnect/org-docs/x.pdf"), FileStorageService.cloudinaryTarget(pdf, "demo", "floodconnect"));
        // tài khoản khác, thư mục khác, hoặc có ".." → không xóa
        assertNull(FileStorageService.cloudinaryTarget(img, "khac", "floodconnect"));
        assertNull(FileStorageService.cloudinaryTarget("https://res.cloudinary.com/demo/image/upload/v1/khac/a.jpg", "demo", "floodconnect"));
        assertNull(FileStorageService.cloudinaryTarget("https://res.cloudinary.com/demo/image/upload/floodconnect/../a.jpg", "demo", "floodconnect"));
        assertNull(FileStorageService.cloudinaryTarget(img, "", "floodconnect"));
    }

    // ---------------- Lưu riêng tư (#6: giấy tờ pháp lý / CCCD) ----------------
    @TempDir Path privateRoot;

    @Test void taiRiengTuLuuNgoaiThuMucCongKhaiVaDocLaiDuoc() throws Exception {
        ReflectionTestUtils.setField(storage, "privateDir", privateRoot.toString());
        var st = storage.storePrivate(new org.springframework.mock.web.MockMultipartFile("f", "cccd.png", "image/png", PNG),
                "org-docs", FileStorageService.DOCS);
        assertTrue(st.url().startsWith("local:org-docs/"), "mã nội bộ, không phải đường dẫn công khai: " + st.url());
        assertFalse(Files.exists(root.resolve("org-docs")), "không được ghi vào thư mục /files công khai");
        FileStorageService.PrivateFile f = storage.openPrivate(st.url());
        assertArrayEquals(PNG, f.data());
        assertEquals("image/png", f.contentType());
        storage.deletePrivateAfterCommit(st.url());
        assertThrows(vn.floodconnect.common.exception.ApiException.class, () -> storage.openPrivate(st.url()));
    }

    @Test void maTepRiengTuKhongThoatRaNgoaiThuMuc() {
        ReflectionTestUtils.setField(storage, "privateDir", privateRoot.toString());
        assertThrows(vn.floodconnect.common.exception.ApiException.class, () -> storage.openPrivate("local:../../etc/passwd"));
        assertNull(storage.openPrivate("https://placehold.co/600x400"), "đường dẫn ngoài: nơi gọi tự xử lý");
    }

    @Test void chuKyCloudinaryNhieuThamSoXepTheoTen() {
        // SHA-1("public_id=a&timestamp=1&type=authenticated" + "secret"), thứ tự tham số không ảnh hưởng
        String sig = FileStorageService.signParams(new java.util.LinkedHashMap<>(java.util.Map.of("type", "authenticated", "timestamp", "1", "public_id", "a")), "secret");
        assertEquals(FileStorageService.sha1Hex("public_id=a&timestamp=1&type=authenticated" + "secret"), sig);
    }

    @Test void nhanDienTepCongKhaiCuaHeThong() {
        ReflectionTestUtils.setField(storage, "cloudName", "demo");
        assertTrue(storage.isOwnPublicUrl("http://localhost:8080/files/org-docs/a.png"));
        assertTrue(storage.isOwnPublicUrl("https://res.cloudinary.com/demo/image/upload/v1/floodconnect/org-docs/a.png"));
        assertFalse(storage.isOwnPublicUrl("https://placehold.co/600x400"));
        assertFalse(storage.isOwnPublicUrl("https://res.cloudinary.com/khac/image/upload/v1/floodconnect/org-docs/a.png"));
    }

    // ---------------- #22: kiểm tra nội dung tệp, không để tệp mồ côi khi giao dịch bị hủy ----------------
    /** Ảnh PNG 1×1 thật, và chữ ký đầu tệp của các định dạng khác (đủ để nhận diện). */
    static final byte[] PNG = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    static final byte[] JPG = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F', 0, 1 };
    static final byte[] WEBP = { 'R', 'I', 'F', 'F', 0x24, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' ' };
    static final byte[] PDF = "%PDF-1.7\n%âãÏÓ\n".getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    /** Đầu tệp HEIC (ảnh iPhone): hộp "ftyp" + "heic", không phải JPG dù đặt tên .jpg. */
    static final byte[] HEIC = { 0, 0, 0, 0x18, 'f', 't', 'y', 'p', 'h', 'e', 'i', 'c', 0, 0, 0, 0 };

    private static org.springframework.mock.web.MockMultipartFile file(String name, String type, byte[] data) {
        return new org.springframework.mock.web.MockMultipartFile("f", name, type, data);
    }

    @Test void nhanDienDinhDangTuNoiDung() {
        assertEquals("image/png", FileStorageService.sniff(PNG));
        assertEquals("image/jpeg", FileStorageService.sniff(JPG));
        assertEquals("image/webp", FileStorageService.sniff(WEBP));
        assertEquals("application/pdf", FileStorageService.sniff(PDF));
        assertNull(FileStorageService.sniff(HEIC));
        assertNull(FileStorageService.sniff("xin chào".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        assertNull(FileStorageService.sniff(new byte[] { (byte) 0xFF }));
    }

    @Test void tepDoiDuoiGiaBiTuChoi() {
        var e = assertThrows(vn.floodconnect.common.exception.ApiException.class,
                () -> storage.store(file("IMG_0001.jpg", "image/jpeg", HEIC), "posts", FileStorageService.IMAGES));
        assertEquals("INVALID_FILE_CONTENT", e.getCode());
        assertTrue(e.getMessage().contains("IMG_0001.jpg") && e.getMessage().contains("HEIC"), e.getMessage());
        var pdf = assertThrows(vn.floodconnect.common.exception.ApiException.class,
                () -> storage.store(file("giay-phep.pdf", "application/pdf", "không phải pdf".getBytes(java.nio.charset.StandardCharsets.UTF_8)), "x", FileStorageService.DOCS));
        assertEquals("INVALID_FILE_CONTENT", pdf.getCode());
        assertFalse(Files.exists(root.resolve("posts")), "tệp bị từ chối thì không được ghi");
    }

    @Test void dinhDangThatKhongDuocPhepThiBaoRo() {
        var e = assertThrows(vn.floodconnect.common.exception.ApiException.class,
                () -> storage.store(file("a.webp", "image/webp", WEBP), "avatars", FileStorageService.AVATAR));
        assertEquals("INVALID_FILE_TYPE", e.getCode());
        assertTrue(e.getMessage().contains("WEBP"), e.getMessage());
    }

    @Test void kieuLuuTheoNoiDungKhongTheoTenTep() {
        var st = storage.store(file("anh.jpg", "image/jpeg", PNG), "posts", FileStorageService.IMAGES); // PNG thật nhưng đặt tên .jpg
        assertEquals("image/png", st.mime());
        assertTrue(st.url().endsWith(".png"), st.url());
    }

    @Test void giaoDichBiHuyThiXoaTepVuaTaiLen() throws Exception {
        TransactionSynchronizationManager.initSynchronization();
        var st = storage.store(file("a.png", "image/png", PNG), "posts", FileStorageService.IMAGES);
        Path saved = root.resolve("posts").resolve(st.url().substring(st.url().lastIndexOf('/') + 1));
        assertTrue(Files.exists(saved));
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertFalse(Files.exists(saved), "giao dịch bị hủy: không còn dòng nào trỏ tới tệp nên phải xóa");
    }

    @Test void giaoDichCommitThiGiuTepVuaTaiLen() {
        TransactionSynchronizationManager.initSynchronization();
        var st = storage.store(file("a.png", "image/png", PNG), "posts", FileStorageService.IMAGES);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> { s.afterCommit(); s.afterCompletion(TransactionSynchronization.STATUS_COMMITTED); });
        assertTrue(Files.exists(root.resolve("posts").resolve(st.url().substring(st.url().lastIndexOf('/') + 1))));
    }

    @Test void tepRiengTuCungXoaKhiGiaoDichBiHuy() {
        ReflectionTestUtils.setField(storage, "privateDir", privateRoot.toString());
        TransactionSynchronizationManager.initSynchronization();
        var st = storage.storePrivate(file("cccd.pdf", "application/pdf", PDF), "org-docs", FileStorageService.DOCS);
        TransactionSynchronizationManager.getSynchronizations().forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        assertThrows(vn.floodconnect.common.exception.ApiException.class, () -> storage.openPrivate(st.url()));
    }
}
