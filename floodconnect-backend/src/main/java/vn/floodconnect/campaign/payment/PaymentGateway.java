package vn.floodconnect.campaign.payment;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import vn.floodconnect.campaign.entity.Donation;

/**
 * Cổng thanh toán (chỉ VNPay). app.payment.mock=true: giả lập, không có trang thanh toán (createPaymentUrl trả null, frontend
 * tự mở hộp thoại giả lập rồi gọi /api/payments/callback). false: gọi sandbox VNPay thật (xem VnPayGateway); kết quả được
 * xác minh chữ ký ở PaymentController trước khi ghi nhận.
 */
public interface PaymentGateway {
    String createPaymentUrl(Donation d);
    boolean isMock();

    /** Bean chính: giả lập hoặc VNPay theo cấu hình app.payment.mock. */
    @Component
    @Primary
    @Slf4j
    class Router implements PaymentGateway {
        private final VnPayGateway vnpay;
        @Value("${app.payment.mock}") private boolean mock;

        Router(VnPayGateway vnpay) { this.vnpay = vnpay; }

        @PostConstruct
        void warnIfMock() {
            if (mock) log.warn("THANH TOÁN ĐANG Ở CHẾ ĐỘ GIẢ LẬP (app.payment.mock=true / PAYMENT_MOCK=true). "
                    + "Mọi khoản ủng hộ có thể được xác nhận \"thành công\" qua /api/payments/callback mà không cần thanh toán thật. "
                    + "Đặt PAYMENT_MOCK=false trước khi triển khai thật.");
        }

        @Override
        public String createPaymentUrl(Donation d) {
            if (mock) return null; // giả lập: không có trang thanh toán (đường dẫn /payment/mock cũ không có trang nào ở frontend)
            return vnpay.createPaymentUrl(d);
        }
        @Override public boolean isMock() { return mock; }
    }
}