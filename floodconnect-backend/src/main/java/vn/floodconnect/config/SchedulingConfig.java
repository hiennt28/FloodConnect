package vn.floodconnect.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các tác vụ định kỳ (@Scheduled: nhắc ca quá hạn, hết hạn giao dịch, cập nhật trạng thái chiến dịch, mở khóa tài khoản...).
 * Mặc định bật. Kiểm thử tích hợp đặt app.scheduling.enabled=false để tác vụ chạy ngầm không sửa dữ liệu giữa chừng một bài test.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
