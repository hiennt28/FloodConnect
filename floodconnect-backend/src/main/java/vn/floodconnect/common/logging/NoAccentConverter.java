package vn.floodconnect.common.logging;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * Từ khóa %noAccentMsg trong mẫu log: in nội dung log như %m nhưng chuyển sang ASCII (xem {@link ConsoleAscii}).
 * Chỉ nội dung log có tiếng Việt; giờ, mức log, luồng, tên lớp vốn đã là ASCII nên giữ nguyên.
 */
public class NoAccentConverter extends MessageConverter {
    @Override
    public String convert(ILoggingEvent event) {
        return ConsoleAscii.of(super.convert(event));
    }
}
