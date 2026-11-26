package vn.floodconnect.user.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import vn.floodconnect.user.service.AccountLockService;

/** Tác vụ định kỳ (mỗi phút): mở khóa tài khoản hết hạn khóa và gửi thông báo khôi phục. */
@Service
@RequiredArgsConstructor
@Slf4j
public class AccountScheduler {
    private final AccountLockService lockService;

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    public void unlockAccounts() {
        int n = lockService.unlockExpired();
        if (n > 0) log.info("Đã tự động mở khóa {} tài khoản hết hạn", n);
    }
}
