package vn.floodconnect.campaign.payment;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.common.exception.ApiException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

/** VNPay (môi trường sandbox). Chữ ký HMAC-SHA512, API phiên bản 2.1.0. */
@Component
public class VnPayGateway {
    @Value("${app.payment.vnpay.tmn-code:}") private String tmnCode;
    @Value("${app.payment.vnpay.hash-secret:}") private String hashSecret;
    @Value("${app.payment.vnpay.pay-url}") private String payUrl;
    @Value("${app.backend-url}") private String backendUrl;

    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    public String createPaymentUrl(Donation d) {
        if (tmnCode.isBlank() || hashSecret.isBlank())
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENT_NOT_CONFIGURED",
                    "Chưa cấu hình VNPay sandbox (VNPAY_TMN_CODE / VNPAY_HASH_SECRET).", null);
        ZonedDateTime now = ZonedDateTime.now(VN);
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", String.valueOf(d.getAmount() * 100));   // VNPay yêu cầu nhân 100
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", d.getTransactionCode());
        p.put("vnp_OrderInfo", "Ung ho chien dich " + d.getTransactionCode());  // không dấu, không ký tự đặc biệt
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", backendUrl + "/api/payments/vnpay/return");
        p.put("vnp_IpAddr", clientIp());
        p.put("vnp_CreateDate", now.format(FMT));
        p.put("vnp_ExpireDate", now.plusMinutes(15).format(FMT));
        String hashData = encode(p);
        return payUrl + "?" + hashData + "&vnp_SecureHash=" + PayUtil.hmacHex("HmacSHA512", hashSecret, hashData);
    }

    /** Xác minh chữ ký của tham số VNPay trả về (return URL hoặc IPN). */
    public boolean verify(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (received == null || hashSecret.isBlank()) return false;
        Map<String, String> p = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) p.put(k, v);
        });
        return PayUtil.sameSignature(received, PayUtil.hmacHex("HmacSHA512", hashSecret, encode(p)));
    }

    /** key=value nối bằng &, giá trị mã hóa URL (US_ASCII), bỏ tham số rỗng; thứ tự theo bảng chữ cái. */
    private static String encode(Map<String, String> sorted) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : sorted.entrySet()) {
            if (e.getValue() == null || e.getValue().isEmpty()) continue;
            if (sb.length() > 0) sb.append('&');
            sb.append(URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII)).append('=')
              .append(URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII));
        }
        return sb.toString();
    }

    private static String clientIp() {
        try {
            HttpServletRequest r = ((ServletRequestAttributes) RequestContextHolder.currentRequestAttributes()).getRequest();
            String ip = r.getHeader("X-Forwarded-For");
            ip = (ip != null && !ip.isBlank()) ? ip.split(",")[0].trim() : r.getRemoteAddr();
            return (ip == null || ip.contains(":")) ? "127.0.0.1" : ip;   // IPv6 (::1) -> 127.0.0.1
        } catch (Exception e) { return "127.0.0.1"; }
    }
}