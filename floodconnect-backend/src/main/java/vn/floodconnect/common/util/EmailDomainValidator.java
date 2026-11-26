package vn.floodconnect.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.time.Instant;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Kiểm tra tên miền của một email có khai báo máy chủ nhận thư hay không (bản ghi DNS MX, hoặc A nếu không có MX),
 * để báo lỗi sớm khi đăng ký thay vì để người dùng chờ một email không bao giờ tới.
 *
 * Chỉ kiểm tra khi app.mail.enabled=true (gửi mail thật). Ở chế độ giả lập (MAIL_ENABLED=false), nội dung email chỉ
 * được ghi ra log nên không cần tên miền nhận được thư thật — việc bật kiểm tra ở chế độ đó sẽ cản trở việc test/demo
 * bằng các địa chỉ email không có hộp thư thật (ví dụ email trường học chưa cấu hình DNS).
 *
 * Giới hạn: chỉ xác nhận được tên miền có khai báo nhận thư, không xác nhận hộp thư cụ thể có tồn tại hay không.
 * Lỗi mạng/DNS không chặn đăng ký (coi như hợp lệ), để sự cố hạ tầng không làm người dùng không đăng ký được.
 */
@Component
@Slf4j
public class EmailDomainValidator {
    @Value("${app.mail.enabled}") private boolean mailEnabled;
    private static final long CACHE_TTL_MS = 60 * 60 * 1000; // 1 giờ
    private static final int DNS_TIMEOUT_MS = 2000;
    private record CacheEntry(boolean receivesMail, long at) {}
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void check(String email, Map<String, String> errors) {
        if (!mailEnabled) return;
        int at = email.indexOf('@');
        if (at < 0) return; // định dạng email đã được Validators/@Email kiểm tra riêng
        String domain = email.substring(at + 1).trim().toLowerCase();
        if (!receivesMail(domain))
            errors.put("email", "Tên miền " + domain + " hiện không nhận được email (DNS không khai báo máy chủ nhận thư). "
                    + "Vui lòng kiểm tra lại địa chỉ hoặc dùng email khác.");
    }

    private boolean receivesMail(String domain) {
        CacheEntry cached = cache.get(domain);
        if (cached != null && Instant.now().toEpochMilli() - cached.at() < CACHE_TTL_MS) return cached.receivesMail();
        boolean ok = lookup(domain);
        cache.put(domain, new CacheEntry(ok, Instant.now().toEpochMilli()));
        return ok;
    }

    private boolean lookup(String domain) {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", String.valueOf(DNS_TIMEOUT_MS));
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        try {
            DirContext ctx = new InitialDirContext(env);
            try {
                if (hasRecord(ctx, domain, "MX")) return true;
                return hasRecord(ctx, domain, "A"); // không có MX: một số tên miền nhỏ nhận thư thẳng qua bản ghi A
            } finally {
                ctx.close();
            }
        } catch (NamingException e) {
            log.warn("Không tra được DNS cho tên miền {}, bỏ qua kiểm tra: {}", domain, e.getMessage());
            return true; // lỗi DNS/mạng: không chặn đăng ký
        }
    }

    private boolean hasRecord(DirContext ctx, String domain, String type) throws NamingException {
        Attributes attrs = ctx.getAttributes(domain, new String[] { type });
        Attribute a = attrs.get(type);
        return a != null && a.size() > 0;
    }
}
