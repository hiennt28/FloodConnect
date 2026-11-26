package vn.floodconnect.architecture;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Giữ cấu trúc module không bị xuống cấp khi thêm chức năng: đọc mã nguồn trong src/main/java và kiểm tra các quy tắc import.
 * Chạy bằng `mvn test` từ thư mục gốc project.
 */
class ArchitectureTest {
    private static final Path ROOT = Paths.get("src/main/java/vn/floodconnect");
    private static final List<String> FEATURES = List.of("admin", "ai", "auth", "campaign", "geo", "incident", "notification",
            "organization", "post", "rescue", "stats", "user");
    /** Ngoại lệ đã biết: callback cổng thanh toán đọc thẳng repository. Thêm vào đây phải có lý do. */
    private static final Set<String> CONTROLLER_REPO_ALLOWED = Set.of("PaymentController.java");

    private static Map<Path, List<String>> imports() throws IOException {
        Map<Path, List<String>> out = new TreeMap<>();
        try (Stream<Path> s = Files.walk(ROOT)) {
            for (Path p : s.filter(f -> f.toString().endsWith(".java")).toList())
                out.put(ROOT.relativize(p), Files.readAllLines(p).stream().filter(l -> l.startsWith("import ")).toList());
        }
        return out;
    }

    @Test void commonDoesNotDependOnFeatureModules() throws IOException {
        List<String> bad = new ArrayList<>();
        imports().forEach((file, imps) -> {
            if (!file.startsWith("common")) return;
            for (String i : imps) for (String f : FEATURES)
                if (i.startsWith("import vn.floodconnect." + f + ".")) bad.add(file + " -> " + i);
        });
        assertTrue(bad.isEmpty(), "common/ không được phụ thuộc module nghiệp vụ:\n" + String.join("\n", bad));
    }

    @Test void nobodyImportsControllers() throws IOException {
        List<String> bad = new ArrayList<>();
        imports().forEach((file, imps) -> imps.stream().filter(i -> i.contains(".controller.")).forEach(i -> bad.add(file + " -> " + i)));
        assertTrue(bad.isEmpty(), "Controller chỉ là lớp vào HTTP, không được class khác import:\n" + String.join("\n", bad));
    }

    @Test void controllersGoThroughServices() throws IOException {
        List<String> bad = new ArrayList<>();
        imports().forEach((file, imps) -> {
            if (!file.toString().contains("controller") || CONTROLLER_REPO_ALLOWED.contains(file.getFileName().toString())) return;
            imps.stream().filter(i -> i.contains(".repository.")).forEach(i -> bad.add(file + " -> " + i));
        });
        assertTrue(bad.isEmpty(), "Controller phải gọi service, không gọi repository trực tiếp:\n" + String.join("\n", bad));
    }

    @Test void modulesNotifyThroughEvents() throws IOException {
        List<String> bad = new ArrayList<>();
        imports().forEach((file, imps) -> {
            if (file.startsWith("notification")) return;
            imps.stream().filter(i -> i.equals("import vn.floodconnect.notification.service.NotificationService;"))
                    .forEach(i -> bad.add(file.toString()));
        });
        assertTrue(bad.isEmpty(), "Module khác gửi thông báo qua NotificationPublisher (sự kiện), không gọi thẳng NotificationService:\n"
                + String.join("\n", bad));
    }
}
