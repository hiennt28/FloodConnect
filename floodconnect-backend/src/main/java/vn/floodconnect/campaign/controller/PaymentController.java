package vn.floodconnect.campaign.controller;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationDto;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.payment.PaymentGateway;
import vn.floodconnect.campaign.payment.VnPayGateway;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.service.DonationService;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Kết quả thanh toán.
 * - /callback: chỉ dùng khi app.payment.mock=true (frontend giả lập cổng).
 * - /vnpay/return: trình duyệt được cổng chuyển về sau khi thanh toán. Có xác minh chữ ký rồi mới ghi nhận,
 *   sau đó chuyển hướng về frontend (/payment/result). Nhờ vậy chạy được cả trên localhost, không cần IPN.
 * - /vnpay/ipn: cổng gọi server-to-server (cần URL public, ví dụ ngrok). processResult idempotent nên
 *   return và IPN có thể cùng xử lý mà không bị cộng tiền hai lần.
 */
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Slf4j
public class PaymentController {
    private final DonationService donations;
    private final DonationRepository donationRepo;
    private final PaymentGateway gateway;
    private final VnPayGateway vnpay;
    private final CurrentUser current;
    @Value("${app.frontend-url}") private String frontendUrl;

    public record Callback(@NotBlank String transactionCode, boolean success, String gatewayRef, @NotNull Long amount) {}

    /** Chỉ dùng khi app.payment.mock=true; chỉ chính người đã tạo giao dịch ủng hộ mới được gọi. */
    @PostMapping("/callback")
    public DonationDto callback(@Valid @RequestBody Callback c) {
        if (!gateway.isMock())
            throw new ApiException(HttpStatus.FORBIDDEN, "MOCK_DISABLED", "Cổng giả lập đã tắt (app.payment.mock=false).", null);
        Donation d = donationRepo.findByTransactionCode(c.transactionCode())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy giao dịch."));
        if (!d.getDonor().getId().equals(current.id()))
            throw ApiException.forbidden("Bạn chỉ xác nhận được giao dịch ủng hộ của chính mình.");
        return donations.processResult(c.transactionCode(), c.success(), c.gatewayRef(), c.amount());
    }

    /* ---------------- VNPay ---------------- */
    @GetMapping("/vnpay/return")
    public void vnpayReturn(@RequestParam Map<String, String> q, HttpServletResponse res) throws IOException {
        String tx = q.get("vnp_TxnRef");
        if (!vnpay.verify(q)) { log.warn("VNPay return sai chữ ký (tx={})", tx); redirect(res, tx, null, "INVALID"); return; }
        try {
            DonationDto d = donations.processResult(tx, vnpaySuccess(q), q.get("vnp_TransactionNo"), vnpayAmount(q));
            redirect(res, tx, d.campaignId(), d.status().name());
        } catch (ApiException e) {
            log.warn("VNPay return không xử lý được (tx={}): {}", tx, e.getMessage());
            redirect(res, tx, null, "INVALID");
        }
    }

    @GetMapping("/vnpay/ipn")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> q) {
        try {
            if (!vnpay.verify(q)) return vnpRsp("97", "Invalid Checksum");
            Donation d = donationRepo.findByTransactionCode(q.get("vnp_TxnRef")).orElse(null);
            if (d == null) return vnpRsp("01", "Order not Found");
            if (d.getAmount() != vnpayAmount(q)) return vnpRsp("04", "Invalid Amount");
            // EXPIRED vẫn xử lý: IPN thành công về muộn (sau khi hết hạn) phải được ghi nhận vì tiền đã bị trừ
            if (d.getStatus() != DonationStatus.PENDING && d.getStatus() != DonationStatus.EXPIRED) return vnpRsp("02", "Order already confirmed");
            donations.processResult(d.getTransactionCode(), vnpaySuccess(q), q.get("vnp_TransactionNo"), vnpayAmount(q));
            return vnpRsp("00", "Confirm Success");
        } catch (Exception e) {
            log.error("VNPay IPN lỗi", e);
            return vnpRsp("99", "Unknown error");
        }
    }

    private static boolean vnpaySuccess(Map<String, String> q) {
        String ts = q.get("vnp_TransactionStatus");
        return "00".equals(q.get("vnp_ResponseCode")) && (ts == null || "00".equals(ts));
    }
    private static long vnpayAmount(Map<String, String> q) {
        try { return Long.parseLong(q.getOrDefault("vnp_Amount", "0")) / 100; } catch (NumberFormatException e) { return -1; }
    }
    private static Map<String, String> vnpRsp(String code, String msg) {
        Map<String, String> m = new LinkedHashMap<>(); m.put("RspCode", code); m.put("Message", msg); return m;
    }

    private void redirect(HttpServletResponse res, String tx, Long campaignId, String status) throws IOException {
        String url = frontendUrl + "/payment/result?status=" + status
                + "&transactionCode=" + URLEncoder.encode(tx == null ? "" : tx, StandardCharsets.UTF_8)
                + (campaignId != null ? "&campaignId=" + campaignId : "");
        res.sendRedirect(url);
    }
}