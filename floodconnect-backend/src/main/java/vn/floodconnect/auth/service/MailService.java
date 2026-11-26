package vn.floodconnect.auth.service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Gửi email qua SMTP.
 * - MAIL_ENABLED=false: chỉ in nội dung (kèm link) ra log, dùng khi phát triển.
 * - MAIL_ENABLED=true : gửi thật.
 *
 * Email được gửi ở luồng riêng (không làm chậm request) và chỉ sau khi giao dịch DB commit xong,
 * để người dùng bấm link thì token chắc chắn đã có trong database.
 */
@Service
@Slf4j
public class MailService {
    private final ObjectProvider<JavaMailSender> sender;
    private final ExecutorService pool = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "mail-sender");
        t.setDaemon(true);
        return t;
    });

    @Value("${app.mail.enabled}") private boolean enabled;
    @Value("${app.mail.from}") private String from;
    @Value("${app.frontend-url:http://localhost:5173}") private String frontendUrl;
    @Value("${spring.mail.host:}") private String smtpHost;
    @Value("${spring.mail.username:}") private String smtpUser;
    @Value("${spring.mail.password:}") private String smtpPassword;

    public MailService(ObjectProvider<JavaMailSender> sender) { this.sender = sender; }

    @PostConstruct
    void logMode() {
        if (!enabled) {
            log.info("Email: chế độ GIẢ LẬP (MAIL_ENABLED=false) - nội dung email chỉ được in ra log.");
        } else if (smtpUser.isBlank() || smtpPassword.isBlank()) {
            log.warn("Email: MAIL_ENABLED=true nhưng thiếu MAIL_USER / MAIL_PASSWORD - email sẽ KHÔNG gửi được.");
        } else {
            log.info("Email: gửi THẬT qua {} bằng tài khoản {}, người gửi hiển thị: {}", smtpHost, smtpUser, from);
        }
    }

    @PreDestroy
    void shutdown() { pool.shutdown(); }

    public void send(String to, String subject, String body) {
        if (!enabled || sender.getIfAvailable() == null) {
            log.info("[MAIL giả lập] to={} | {} | {}", to, subject, body);
            return;
        }
        Runnable job = () -> deliver(to, subject, body);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // Đang trong @Transactional: chờ commit xong mới gửi; rollback thì không gửi gì cả.
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { pool.execute(job); }
            });
        } else {
            pool.execute(job);
        }
    }

    private void deliver(String to, String subject, String body) {
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from); m.setTo(to); m.setSubject(subject); m.setText(body);
            sender.getObject().send(m);
            log.info("Đã gửi email tới {} ({})", to, subject);
        } catch (Exception e) {
            // Không in nội dung email (chứa token) khi gửi thật
            log.error("Gửi email thất bại tới {} ({}): {}", to, subject, e.getMessage());
        }
    }

    public void sendVerify(String to, String code) {
        send(to, "FloodConnect - Xác thực email",
                "Chào bạn,\n\nMã xác thực tài khoản FloodConnect của bạn là: " + code
                        + "\n\nMã có hiệu lực 10 phút. Nếu không phải bạn yêu cầu, hãy bỏ qua email này.\n\nFloodConnect");
    }

    /** Tài khoản Admin mới do Admin gốc tạo: mã đặt mật khẩu lần đầu (dùng trang Đặt lại mật khẩu). */
    public void sendAdminInvite(String to, String code) {
        send(to, "FloodConnect - Bạn được cấp tài khoản Quản trị viên",
                "Chào bạn,\n\nAdmin gốc của FloodConnect đã tạo cho bạn một tài khoản Quản trị viên với email này.\n"
                        + "Mã đặt mật khẩu của bạn là: " + code + " (hiệu lực 10 phút, dùng một lần).\n\n"
                        + "Mở trang " + frontendUrl + "/reset-password?email=" + java.net.URLEncoder.encode(to, java.nio.charset.StandardCharsets.UTF_8)
                        + ", nhập mã và đặt mật khẩu, sau đó đăng nhập bằng email này.\n"
                        + "Nếu mã đã hết hạn, bấm \"Quên mật khẩu\" ở trang đăng nhập để nhận mã mới.\n\nFloodConnect");
    }

    public void sendReset(String to, String code) {
        send(to, "FloodConnect - Đặt lại mật khẩu",
                "Bạn vừa yêu cầu đặt lại mật khẩu. Mã xác thực của bạn là: " + code
                        + "\n\nMã có hiệu lực 10 phút và chỉ dùng được một lần. Nếu không phải bạn, hãy bỏ qua email này.");
    }
}