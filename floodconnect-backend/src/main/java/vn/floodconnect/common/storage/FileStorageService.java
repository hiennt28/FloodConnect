package vn.floodconnect.common.storage;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.exception.ApiException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.Set;
import java.util.UUID;

/**
 * Lưu tệp tải lên và trả về URL công khai. Chọn nơi lưu bằng app.storage.provider (biến STORAGE_PROVIDER):
 * - local      : ghi vào thư mục app.storage.dir, phục vụ qua /files/** (mặc định, dùng khi phát triển)
 * - cloudinary : tải lên Cloudinary (REST API có chữ ký), URL lưu vào DB là https://res.cloudinary.com/...
 * Các nơi gọi store() không phải sửa gì.
 * deleteAfterCommit(url) xóa tệp không còn dùng (ảnh đại diện cũ / đã xóa), cũng theo đúng nơi lưu của URL đó.
 */
@Service
@Slf4j
public class FileStorageService {
    public static final Set<String> IMAGES = Set.of("image/jpeg", "image/png", "image/webp");
    public static final Set<String> AVATAR = Set.of("image/jpeg", "image/png");
    public static final Set<String> DOCS = Set.of("application/pdf", "image/jpeg", "image/png");
    public static final long MAX_BYTES = 5L * 1024 * 1024;

    @Value("${app.storage.provider:local}") private String provider;
    @Value("${app.storage.dir}") private String dir;
    @Value("${app.storage.public-url}") private String publicUrl;
    @Value("${app.cloudinary.cloud-name:}") private String cloudName;
    @Value("${app.cloudinary.api-key:}") private String apiKey;
    @Value("${app.cloudinary.api-secret:}") private String apiSecret;
    @Value("${app.cloudinary.folder:floodconnect}") private String cloudFolder;

    private boolean cloud;
    private RestClient http;

    public record Stored(String url, String mime) {}

    @PostConstruct
    void init() {
        cloud = "cloudinary".equalsIgnoreCase(provider.trim());
        if (!cloud) {
            log.info("Lưu tệp: LOCAL ({})", Path.of(dir).toAbsolutePath());
            return;
        }
        cloudName = cloudName.trim(); apiKey = apiKey.trim(); apiSecret = apiSecret.trim();
        if (cloudName.isEmpty() || apiKey.isEmpty() || apiSecret.isEmpty())
            throw new IllegalStateException("STORAGE_PROVIDER=cloudinary nhưng thiếu CLOUDINARY_CLOUD_NAME / CLOUDINARY_API_KEY / CLOUDINARY_API_SECRET");
        SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
        f.setConnectTimeout(5_000);
        f.setReadTimeout(30_000);
        http = RestClient.builder().requestFactory(f).build();
        log.info("Lưu tệp: CLOUDINARY (cloud={}, thư mục gốc={})", cloudName, cloudFolder);
    }

    public Stored store(MultipartFile f, String folder, Set<String> allowed) {
        String mime = checkedType(f, allowed);
        String ext = extOf(mime);
        String id = UUID.randomUUID().toString();
        String url = cloud ? uploadToCloudinary(f, folder, id, ext, mime) : saveLocal(f, folder, id + ext);
        deleteIfRolledBack(() -> deleteQuietly(url));
        return new Stored(url, mime);
    }

    // ===================================================== kiểm tra nội dung tệp
    private static final Map<String, String> TYPE_LABEL = Map.of("image/jpeg", "JPG", "image/png", "PNG", "image/webp", "WEBP", "application/pdf", "PDF");

    /**
     * Kiểu tệp xác định từ NỘI DUNG (chữ ký ở vài byte đầu), không tin kiểu do trình duyệt gửi: trình duyệt chỉ suy ra kiểu từ đuôi
     * tên tệp, nên tệp đổi đuôi (vd. ảnh HEIC của iPhone đổi tên thành .jpg, hoặc tệp bất kỳ đổi thành .pdf) vẫn qua được.
     * Kiểu nhận ra được dùng để đặt đuôi khi lưu. Trả về kiểu MIME đã kiểm tra.
     */
    String checkedType(MultipartFile f, Set<String> allowed) {
        if (f == null || f.isEmpty()) throw ApiException.bad("Tệp tải lên bị rỗng.");
        if (f.getSize() > MAX_BYTES) throw ApiException.bad("FILE_TOO_LARGE", "Mỗi tệp tối đa 5 MB.");
        String accepted = String.join(", ", allowed.stream().map(TYPE_LABEL::get).sorted().toList());
        String sniffed;
        try (InputStream in = f.getInputStream()) { sniffed = sniff(in.readNBytes(12)); }
        catch (IOException e) { throw uploadFailed(); }
        if (sniffed == null)
            throw ApiException.bad("INVALID_FILE_CONTENT", "Tệp \"" + nameOf(f) + "\" không phải tệp " + accepted
                    + " thật (có thể chỉ đổi đuôi tên tệp, ví dụ ảnh HEIC của iPhone). Hãy chuyển sang " + accepted + " rồi tải lại.");
        if (!allowed.contains(sniffed))
            throw ApiException.bad("INVALID_FILE_TYPE", "Tệp \"" + nameOf(f) + "\" là " + TYPE_LABEL.get(sniffed) + ", ở đây chỉ nhận " + accepted + ".");
        return sniffed;
    }

    /** Chữ ký đầu tệp: JPEG FF D8 FF, PNG 89 50 4E 47 0D 0A 1A 0A, WEBP "RIFF" ???? "WEBP", PDF "%PDF-". Không nhận ra: null. */
    static String sniff(byte[] h) {
        if (h.length >= 3 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xFF) == 0xD8 && (h[2] & 0xFF) == 0xFF) return "image/jpeg";
        if (h.length >= 8 && (h[0] & 0xFF) == 0x89 && h[1] == 'P' && h[2] == 'N' && h[3] == 'G' && h[4] == 0x0D && h[5] == 0x0A && h[6] == 0x1A && h[7] == 0x0A)
            return "image/png";
        if (h.length >= 12 && h[0] == 'R' && h[1] == 'I' && h[2] == 'F' && h[3] == 'F' && h[8] == 'W' && h[9] == 'E' && h[10] == 'B' && h[11] == 'P')
            return "image/webp";
        if (h.length >= 5 && h[0] == '%' && h[1] == 'P' && h[2] == 'D' && h[3] == 'F' && h[4] == '-') return "application/pdf";
        return null;
    }

    private static String nameOf(MultipartFile f) {
        String n = f.getOriginalFilename();
        return n == null || n.isBlank() ? "đã chọn" : n.length() > 80 ? n.substring(0, 80) + "…" : n;
    }

    private static String extOf(String mime) {
        return switch (mime) { case "image/jpeg" -> ".jpg"; case "image/png" -> ".png"; case "image/webp" -> ".webp"; default -> ".pdf"; };
    }

    /**
     * Tệp vừa tải lên mà giao dịch CSDL sau đó bị hủy (vd. lưu bài lỗi, hồ sơ đăng ký không hợp lệ ở bước sau): không còn dòng nào
     * trỏ tới tệp, nên xóa để không thành tệp mồ côi. Ngoài giao dịch thì không biết kết quả, không làm gì.
     */
    private void deleteIfRolledBack(Runnable delete) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) { if (status == STATUS_ROLLED_BACK) delete.run(); }
        });
    }

    // ===================================================== lưu RIÊNG TƯ (giấy tờ pháp lý / CCCD của tổ chức)
    /*
     * Tệp riêng tư không có đường dẫn công khai: store trả về MÃ TỆP NỘI BỘ, chỉ đọc lại được qua openPrivate (backend kiểm tra
     * quyền trước khi gọi). Cloudinary: kiểu "authenticated" (link không chữ ký bị chặn 401; backend tải bằng private download API
     * có chữ ký bí mật, hết hạn sau 5 phút, không đưa link ra trình duyệt). Lưu trên máy: thư mục app.storage.private-dir, KHÔNG
     * nằm dưới /files nên không mở được qua web. Mã: "local:<thư mục>/<tên>" hoặc "cloudinary:<image|raw>:<định dạng>:<public_id>".
     */
    @Value("${app.storage.private-dir:./private-uploads}") private String privateDir;
    private static final long PRIVATE_LINK_SECONDS = 300;

    /** Nội dung tệp riêng tư đọc lại để trả cho người có quyền. */
    public record PrivateFile(byte[] data, String contentType) {}

    public Stored storePrivate(MultipartFile f, String folder, Set<String> allowed) {
        String mime = checkedType(f, allowed);
        String key;
        try { key = storePrivateBytes(f.getBytes(), mime, folder); }
        catch (IOException e) { throw uploadFailed(); }
        deleteIfRolledBack(() -> deletePrivateQuietly(key));
        return new Stored(key, mime);
    }

    /** Lưu riêng tư từ mảng byte (dùng cả khi chuyển tệp cũ từ chỗ công khai sang). Trả mã tệp nội bộ. */
    public String storePrivateBytes(byte[] data, String mime, String folder) {
        String ext = extOf(mime);
        String id = UUID.randomUUID().toString();
        return cloud ? uploadPrivateToCloudinary(data, folder, id, ext, mime) : savePrivateLocal(data, folder, id + ext);
    }

    private String savePrivateLocal(byte[] data, String folder, String name) {
        try {
            Path target = Path.of(privateDir, folder).toAbsolutePath();
            Files.createDirectories(target);
            Files.write(target.resolve(name), data);
        } catch (IOException e) {
            throw uploadFailed();
        }
        return "local:" + folder + "/" + name;
    }

    private String uploadPrivateToCloudinary(byte[] data, String folder, String id, String ext, String mime) {
        boolean pdf = "application/pdf".equals(mime);
        String resource = pdf ? "raw" : "image";
        String publicId = cloudFolder + "/" + folder + "/" + id + (pdf ? ext : "");
        Map<String, String> p = new TreeMap<>(Map.of("public_id", publicId, "timestamp", String.valueOf(Instant.now().getEpochSecond()),
                "type", "authenticated"));
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", new ByteArrayResource(data) { @Override public String getFilename() { return id + ext; } });
            p.forEach(form::add);
            form.add("api_key", apiKey);
            form.add("signature", signParams(p, apiSecret));
            JsonNode res = cloudClient().post().uri("https://api.cloudinary.com/v1_1/" + cloudName + "/" + resource + "/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().body(JsonNode.class);
            if (res == null || !"authenticated".equals(res.path("type").asText()))
                throw new IllegalStateException("Cloudinary không lưu tệp ở chế độ riêng tư (authenticated)");
            String format = pdf ? "" : res.path("format").asText(ext.substring(1));
            return "cloudinary:" + resource + ":" + format + ":" + publicId;
        } catch (RestClientResponseException e) {
            log.error("Cloudinary từ chối tải lên tệp riêng tư (HTTP {}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw uploadFailed();
        } catch (Exception e) {
            log.error("Tải tệp riêng tư lên Cloudinary thất bại: {}", e.getMessage());
            throw uploadFailed();
        }
    }

    /**
     * Đọc lại tệp riêng tư theo mã. NƠI GỌI PHẢI KIỂM TRA QUYỀN TRƯỚC. Trả null nếu mã không phải tệp riêng tư của hệ thống
     * (vd. đường dẫn http của dữ liệu cũ / ảnh mẫu): nơi gọi tự xử lý.
     */
    public PrivateFile openPrivate(String key) {
        if (key == null) return null;
        try {
            if (key.startsWith("local:")) {
                Path p = localPath(privateDir, key.substring("local:".length()));
                if (p == null || !Files.isRegularFile(p)) throw ApiException.notFound("Không tìm thấy tệp.");
                return new PrivateFile(Files.readAllBytes(p), contentTypeOf(p.getFileName().toString()));
            }
            if (key.startsWith("cloudinary:")) {
                String[] k = key.split(":", 4); // cloudinary, resource, format, publicId
                if (k.length != 4) throw ApiException.notFound("Không tìm thấy tệp.");
                long ts = Instant.now().getEpochSecond();
                Map<String, String> p = new TreeMap<>(Map.of("public_id", k[3], "type", "authenticated", "timestamp", String.valueOf(ts),
                        "expires_at", String.valueOf(ts + PRIVATE_LINK_SECONDS)));
                if (!k[2].isEmpty()) p.put("format", k[2]);
                StringBuilder q = new StringBuilder();
                p.forEach((name, v) -> q.append(q.isEmpty() ? "" : "&").append(name).append('=').append(java.net.URLEncoder.encode(v, StandardCharsets.UTF_8)));
                q.append("&api_key=").append(apiKey).append("&signature=").append(signParams(p, apiSecret));
                byte[] data = cloudClient().get().uri(java.net.URI.create("https://api.cloudinary.com/v1_1/" + cloudName + "/" + k[1] + "/download?" + q))
                        .retrieve().body(byte[].class);
                return new PrivateFile(data, contentTypeOf(k[2].isEmpty() ? k[3] : "x." + k[2]));
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            log.error("Không đọc được tệp riêng tư: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "FILE_UNAVAILABLE", "Tạm thời không mở được tệp, vui lòng thử lại.", null);
        }
        return null;
    }

    /** Xóa tệp riêng tư sau khi giao dịch commit (giống deleteAfterCommit). Mã là đường dẫn http (dữ liệu cũ) thì xóa như tệp công khai. */
    public void deletePrivateAfterCommit(String key) {
        if (key == null || key.isBlank()) return;
        Runnable job = () -> deletePrivateQuietly(key);
        if (TransactionSynchronizationManager.isSynchronizationActive())
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() { @Override public void afterCommit() { job.run(); } });
        else job.run();
    }

    void deletePrivateQuietly(String key) {
        try {
            if (key.startsWith("local:")) {
                Path p = localPath(privateDir, key.substring("local:".length()));
                if (p != null) Files.deleteIfExists(p);
            } else if (key.startsWith("cloudinary:")) {
                String[] k = key.split(":", 4);
                Map<String, String> p = new TreeMap<>(Map.of("public_id", k[3], "type", "authenticated", "invalidate", "true",
                        "timestamp", String.valueOf(Instant.now().getEpochSecond())));
                MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
                p.forEach(form::add);
                form.add("api_key", apiKey);
                form.add("signature", signParams(p, apiSecret));
                cloudClient().post().uri("https://api.cloudinary.com/v1_1/" + cloudName + "/" + k[1] + "/destroy")
                        .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
            } else {
                deleteQuietly(key);
            }
        } catch (Exception e) {
            log.warn("Không xóa được tệp riêng tư: {}", e.getMessage());
        }
    }

    /** Đường dẫn công khai do CHÍNH hệ thống lưu (thư mục /files hoặc Cloudinary kiểu công khai của cloud + thư mục này). */
    public boolean isOwnPublicUrl(String url) {
        return url != null && (url.startsWith(publicUrl + "/") || cloudinaryTarget(url, cloudName, cloudFolder) != null);
    }

    /** Đọc nội dung một tệp công khai của chính hệ thống (dùng khi chuyển tệp cũ sang chế độ riêng tư). */
    public byte[] readOwnPublic(String url) throws IOException {
        if (url.startsWith(publicUrl + "/")) {
            Path p = localPath(dir, url.substring(publicUrl.length() + 1));
            if (p == null) throw new IOException("Đường dẫn nằm ngoài thư mục lưu");
            return Files.readAllBytes(p);
        }
        if (cloudinaryTarget(url, cloudName, cloudFolder) == null) throw new IOException("Không phải tệp của hệ thống");
        return cloudClient().get().uri(java.net.URI.create(url)).retrieve().body(byte[].class);
    }

    /** Client gọi Cloudinary: có sẵn khi STORAGE_PROVIDER=cloudinary; nếu đang lưu cục bộ mà gặp mã Cloudinary cũ thì tạo khi cần. */
    private RestClient cloudClient() {
        if (http == null) {
            if (cloudName == null || cloudName.isBlank() || apiKey == null || apiKey.isBlank() || apiSecret == null || apiSecret.isBlank())
                throw new IllegalStateException("Thiếu cấu hình Cloudinary để đọc tệp đã lưu trên Cloudinary");
            SimpleClientHttpRequestFactory f = new SimpleClientHttpRequestFactory();
            f.setConnectTimeout(5_000);
            f.setReadTimeout(30_000);
            http = RestClient.builder().requestFactory(f).build();
        }
        return http;
    }

    public static String contentTypeOf(String name) {
        String n = name.toLowerCase();
        if (n.endsWith(".pdf")) return "application/pdf";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".webp")) return "image/webp";
        return "application/octet-stream";
    }

    /** Chữ ký Cloudinary cho nhiều tham số: SHA-1( "a=1&b=2..." (xếp theo tên) + api_secret ). */
    static String signParams(Map<String, String> sortedParams, String secret) {
        StringBuilder s = new StringBuilder();
        new TreeMap<>(sortedParams).forEach((k, v) -> s.append(s.isEmpty() ? "" : "&").append(k).append('=').append(v));
        return sha1Hex(s + secret);
    }

    // ---------------------------------------------------------------- local
    private String saveLocal(MultipartFile f, String folder, String name) {
        try {
            Path target = Path.of(dir, folder).toAbsolutePath();
            Files.createDirectories(target);
            f.transferTo(target.resolve(name));
        } catch (IOException e) {
            throw uploadFailed();
        }
        return publicUrl + "/" + folder + "/" + name;
    }

    // ----------------------------------------------------------- cloudinary
    /**
     * Ảnh tải lên dạng image; PDF tải lên dạng raw (tránh bị chặn "PDF delivery" mặc định của Cloudinary).
     * Chữ ký = SHA-1( "public_id=...&timestamp=..." + api_secret ), các tham số xếp theo thứ tự chữ cái.
     */
    private String uploadToCloudinary(MultipartFile f, String folder, String id, String ext, String mime) {
        boolean pdf = "application/pdf".equals(mime);
        String publicId = cloudFolder + "/" + folder + "/" + id + (pdf ? ext : "");
        long ts = Instant.now().getEpochSecond();
        try {
            MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
            form.add("file", new ByteArrayResource(f.getBytes()) {
                @Override public String getFilename() { return id + ext; }
            });
            form.add("api_key", apiKey);
            form.add("timestamp", String.valueOf(ts));
            form.add("public_id", publicId);
            form.add("signature", sign(publicId, ts, apiSecret));
            JsonNode res = http.post()
                    .uri("https://api.cloudinary.com/v1_1/" + cloudName + "/" + (pdf ? "raw" : "image") + "/upload")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
            String url = res == null ? null : res.path("secure_url").asText(null);
            if (url == null || url.isBlank()) throw new IllegalStateException("Cloudinary không trả về secure_url");
            return url;
        } catch (RestClientResponseException e) {
            // Cloudinary trả JSON lỗi có lý do cụ thể (sai khóa, sai cloud name...). Không ghi api_secret.
            log.error("Cloudinary từ chối tải lên (HTTP {}): {}", e.getStatusCode().value(), e.getResponseBodyAsString());
            throw uploadFailed();
        } catch (Exception e) {
            log.error("Tải tệp lên Cloudinary thất bại: {}", e.getMessage());
            throw uploadFailed();
        }
    }

    // ------------------------------------------------------------------ xóa
    /**
     * Xóa tệp đã lưu theo URL, chạy SAU KHI giao dịch hiện tại commit thành công.
     * Lý do: nếu xóa tệp trước rồi giao dịch bị hủy (rollback), cơ sở dữ liệu vẫn trỏ tới tệp đã mất.
     * Ngoài giao dịch thì xóa ngay. Không bao giờ ném lỗi: xóa hỏng chỉ để lại một tệp thừa, không được làm hỏng thao tác của người dùng.
     */
    public void deleteAfterCommit(String url) {
        if (url == null || url.isBlank()) return;
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { deleteQuietly(url); }
            });
        } else {
            deleteQuietly(url);
        }
    }

    void deleteQuietly(String url) {
        try {
            if (url.startsWith(publicUrl + "/")) {
                Path p = localPath(dir, url.substring(publicUrl.length() + 1));
                if (p == null) { log.warn("Bỏ qua xóa tệp: đường dẫn nằm ngoài thư mục lưu ({})", url); return; }
                Files.deleteIfExists(p);
                return;
            }
            CloudTarget t = cloudinaryTarget(url, cloudName, cloudFolder);
            if (t != null && cloud) { destroyOnCloudinary(t); return; }
            // URL không do hệ thống này lưu (link ngoài, dữ liệu mẫu...) hoặc thiếu khóa Cloudinary: không đụng tới
            log.debug("Bỏ qua xóa tệp không do hệ thống lưu: {}", url);
        } catch (Exception e) {
            log.warn("Không xóa được tệp {}: {}", url, e.getMessage());
        }
    }

    /** "folder/ten-tep" → đường dẫn thật trong thư mục lưu; null nếu đường dẫn thoát ra ngoài (vd. "../../etc/passwd"). */
    static Path localPath(String root, String relative) {
        Path base = Path.of(root).toAbsolutePath().normalize();
        Path p = base.resolve(relative).normalize();
        return p.startsWith(base) && !p.equals(base) ? p : null;
    }

    record CloudTarget(String resourceType, String publicId) {}

    /**
     * Tách loại tài nguyên và public_id từ URL Cloudinary của chính tài khoản/thư mục này, vd.
     * https://res.cloudinary.com/{cloud}/image/upload/v1712345678/floodconnect/avatars/{uuid}.jpg → (image, floodconnect/avatars/{uuid}).
     * Ảnh: public_id không có đuôi; raw (PDF): public_id giữ đuôi (khớp cách uploadToCloudinary đặt tên). URL khác → null.
     */
    static CloudTarget cloudinaryTarget(String url, String cloudName, String folder) {
        if (cloudName == null || cloudName.isBlank()) return null;
        String prefix = "https://res.cloudinary.com/" + cloudName + "/";
        if (!url.startsWith(prefix)) return null;
        String[] parts = url.substring(prefix.length()).split("/upload/", 2); // [image|raw, v123/floodconnect/...]
        if (parts.length != 2 || !Set.of("image", "raw").contains(parts[0])) return null;
        String path = parts[1].replaceFirst("^v\\d+/", "");
        if (!path.startsWith(folder + "/") || path.contains("..")) return null;
        if ("image".equals(parts[0])) {
            int dot = path.lastIndexOf('.');
            if (dot > path.lastIndexOf('/')) path = path.substring(0, dot);
        }
        return new CloudTarget(parts[0], path);
    }

    /** invalidate=true: xóa cả bản lưu đệm trên CDN, để link công khai cũ hết xem được ngay thay vì còn phục vụ thêm một thời gian. */
    private void destroyOnCloudinary(CloudTarget t) {
        Map<String, String> p = new TreeMap<>(Map.of("public_id", t.publicId(), "invalidate", "true",
                "timestamp", String.valueOf(Instant.now().getEpochSecond())));
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        p.forEach(form::add);
        form.add("api_key", apiKey);
        form.add("signature", signParams(p, apiSecret));
        cloudClient().post().uri("https://api.cloudinary.com/v1_1/" + cloudName + "/" + t.resourceType() + "/destroy")
                .contentType(MediaType.MULTIPART_FORM_DATA).body(form).retrieve().toBodilessEntity();
    }

    static String sign(String publicId, long timestamp, String secret) {
        return sha1Hex("public_id=" + publicId + "&timestamp=" + timestamp + secret);
    }

    static String sha1Hex(String s) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(s.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static ApiException uploadFailed() {
        return new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Tải tệp thất bại, vui lòng thử lại.", null);
    }

    // ------------------------------------------------------------- tiện ích
    public static String fileType(String mime) {
        return switch (mime) { case "application/pdf" -> "PDF"; case "image/png" -> "PNG"; default -> "JPG"; };
    }

    /** Lọc bỏ tệp rỗng (trình duyệt đôi khi gửi phần tệp rỗng). */
    public static List<MultipartFile> nonEmpty(List<MultipartFile> files) {
        if (files == null) return List.of();
        return files.stream().filter(x -> x != null && !x.isEmpty()).toList();
    }
}