package vn.floodconnect.common.logging;

import ch.qos.logback.classic.spi.IThrowableProxy;
import org.springframework.boot.logging.logback.ExtendedWhitespaceThrowableProxyConverter;

/**
 * Từ khóa %noAccentEx: in stack trace như mặc định của Spring Boot (%wEx) nhưng chuyển sang ASCII,
 * vì thông báo lỗi của hệ thống viết tiếng Việt (xem {@link ConsoleAscii}).
 */
public class NoAccentThrowableConverter extends ExtendedWhitespaceThrowableProxyConverter {
    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        return ConsoleAscii.of(super.throwableProxyToString(tp));
    }
}
