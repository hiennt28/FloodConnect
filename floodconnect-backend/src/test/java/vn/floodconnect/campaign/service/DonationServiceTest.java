package vn.floodconnect.campaign.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import vn.floodconnect.campaign.dto.CampaignDtos.DonateRequest;
import vn.floodconnect.campaign.dto.CampaignDtos.DonationInit;
import vn.floodconnect.campaign.entity.CampaignStatus;
import vn.floodconnect.campaign.entity.Donation;
import vn.floodconnect.campaign.entity.DonationStatus;
import vn.floodconnect.campaign.entity.EndReason;
import vn.floodconnect.campaign.entity.FundraisingCampaign;
import vn.floodconnect.campaign.entity.PaymentMethod;
import vn.floodconnect.campaign.payment.PaymentGateway;
import vn.floodconnect.campaign.repository.DonationRepository;
import vn.floodconnect.campaign.repository.FundraisingCampaignRepository;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.notification.event.NotificationPublisher;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.User;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Khởi tạo khoản ủng hộ, ghi nhận kết quả từ cổng thanh toán và hết hạn giao dịch bỏ dở. */
class DonationServiceTest {
    private final FundraisingCampaignRepository campaigns = mock(FundraisingCampaignRepository.class);
    private final DonationRepository donations = mock(DonationRepository.class);
    private final PaymentGateway gateway = mock(PaymentGateway.class);
    private final CurrentUser current = mock(CurrentUser.class);
    private final PlatformTransactionManager txManager = mock(PlatformTransactionManager.class);
    private final DonationService service = new DonationService(campaigns, donations, gateway, current,
            mock(NotificationPublisher.class), mock(CampaignMapper.class), txManager);
    private final FundraisingCampaign campaign = new FundraisingCampaign();
    private final Donation donation = new Donation();

    @BeforeEach void setUp() {
        RescueOrganization org = new RescueOrganization();
        org.setUser(new User());
        campaign.setId(2L); campaign.setOrg(org); campaign.setName("Cứu trợ vùng lũ"); campaign.setStatus(CampaignStatus.ACTIVE);
        campaign.setTargetAmount(1_000_000);
        donation.setCampaign(campaign); donation.setDonor(new User()); donation.setAmount(100_000);
        donation.setTransactionCode("VNP1"); donation.setStatus(DonationStatus.PENDING);
        when(donations.findByTransactionCodeForUpdate("VNP1")).thenReturn(Optional.of(donation));
        when(campaigns.findByIdForUpdate(2L)).thenReturn(Optional.of(campaign));
        // khởi tạo ủng hộ
        when(current.get()).thenReturn(new User());
        when(campaigns.findById(2L)).thenReturn(Optional.of(campaign));
        when(donations.save(any(Donation.class))).thenAnswer(inv -> { Donation d = inv.getArgument(0); d.setId(9L); return d; });
    }

    private static DonateRequest request(PaymentMethod m) { return new DonateRequest(100_000L, "Chung tay", m, false); }

    // ---------- Khởi tạo ----------
    @Test void donationGoesThroughVnpay() {
        when(gateway.createPaymentUrl(any())).thenReturn("https://sandbox.vnpayment.vn/pay");

        DonationInit r = service.donate(2L, request(null)); // không gửi phương thức: mặc định VNPay

        assertEquals("https://sandbox.vnpayment.vn/pay", r.paymentUrl());
        assertTrue(r.transactionCode().startsWith("VNP"));
        verify(donations).save(argThat(d -> d.getStatus() == DonationStatus.PENDING && d.getPaymentMethod() == PaymentMethod.VNPAY));
        verify(txManager).commit(any()); // giao dịch PENDING được lưu và commit TRƯỚC khi gọi cổng
    }

    /** Ngoài khoảng 5.000đ – dưới 1 tỷ: chặn ngay, không tạo giao dịch PENDING treo, không gọi cổng thanh toán. */
    @Test void amountOutsideGatewayLimitsIsRejected() {
        for (long amount : new long[] { 1_000, 4_999, 1_000_000_000 }) {
            ApiException e = assertThrows(ApiException.class, () -> service.donate(2L, new DonateRequest(amount, null, null, false)));
            assertEquals("INVALID_AMOUNT", e.getCode());
        }
        verify(donations, never()).save(any());
        verifyNoInteractions(gateway);
    }

    @Test void minimumAmountIsAccepted() {
        when(gateway.createPaymentUrl(any())).thenReturn("https://sandbox.vnpayment.vn/pay");
        assertDoesNotThrow(() -> service.donate(2L, new DonateRequest(5_000L, null, null, false)));
        verify(donations).save(argThat(d -> d.getAmount() == 5_000));
    }

    @Test void momoIsNoLongerAccepted() {
        ApiException e = assertThrows(ApiException.class, () -> service.donate(2L, request(PaymentMethod.MOMO)));
        assertEquals("PAYMENT_METHOD_NOT_SUPPORTED", e.getCode());
        verify(donations, never()).save(any());
        verifyNoInteractions(gateway);
    }

    @Test void gatewayErrorMarksTheNewDonationFailedInsteadOfLeavingItPending() {
        Donation saved = new Donation();
        saved.setStatus(DonationStatus.PENDING);
        when(donations.findByIdForUpdate(9L)).thenReturn(Optional.of(saved));
        when(gateway.createPaymentUrl(any())).thenThrow(new ApiException(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR", "Không kết nối được cổng thanh toán", null));

        ApiException e = assertThrows(ApiException.class, () -> service.donate(2L, request(PaymentMethod.VNPAY)));

        assertEquals("GATEWAY_ERROR", e.getCode()); // người dùng vẫn nhận đúng lỗi của cổng
        assertEquals(DonationStatus.FAILED, saved.getStatus());
        assertNotNull(saved.getCompletedAt());
    }

    // ---------- Hết hạn ----------
    @Test void abandonedPendingDonationExpires() {
        Instant before = Instant.now().minus(Duration.ofMinutes(30));
        donation.setCreatedAt(before.minus(Duration.ofMinutes(5)));
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(donation));

        assertTrue(service.expireIfStale(1L, before));
        assertEquals(DonationStatus.EXPIRED, donation.getStatus());
        assertEquals(0, campaign.getRaisedAmount());
    }

    @Test void donationSettledMeanwhileIsNotExpired() {
        Instant before = Instant.now().minus(Duration.ofMinutes(30));
        donation.setCreatedAt(before.minus(Duration.ofMinutes(5)));
        donation.setStatus(DonationStatus.SUCCESS); // cổng vừa báo thành công trước khi tác vụ khóa được giao dịch
        when(donations.findByIdForUpdate(1L)).thenReturn(Optional.of(donation));

        assertFalse(service.expireIfStale(1L, before));
        assertEquals(DonationStatus.SUCCESS, donation.getStatus());
    }

    @Test void lateSuccessAfterExpiryIsStillCounted() {
        donation.setStatus(DonationStatus.EXPIRED);
        service.processResult("VNP1", true, "G1", 100_000L); // IPN trễ: người ủng hộ đã bị trừ tiền
        assertEquals(DonationStatus.SUCCESS, donation.getStatus());
        assertEquals(100_000, campaign.getRaisedAmount());
    }

    @Test void lateFailureOrWrongAmountAfterExpiryKeepsItExpired() {
        donation.setStatus(DonationStatus.EXPIRED);
        service.processResult("VNP1", false, "G1", 100_000L);
        service.processResult("VNP1", true, "G1", 50_000L);
        assertEquals(DonationStatus.EXPIRED, donation.getStatus());
        assertEquals(0, campaign.getRaisedAmount());
    }

    @Test void sameResultReportedTwiceAddsMoneyOnce() {
        service.processResult("VNP1", true, "G1", 100_000L);
        service.processResult("VNP1", true, "G1", 100_000L); // cổng báo kết quả qua cả return URL lẫn IPN
        assertEquals(DonationStatus.SUCCESS, donation.getStatus());
        assertEquals(100_000, campaign.getRaisedAmount());
    }

    @Test void reachingTargetEndsCampaign() {
        campaign.setRaisedAmount(950_000);
        service.processResult("VNP1", true, "G1", 100_000L);
        assertEquals(CampaignStatus.ENDED, campaign.getStatus());
        assertEquals(EndReason.TARGET_REACHED, campaign.getEndReason());
    }

    @Test void amountDifferentFromInitiatedIsFailed() {
        service.processResult("VNP1", true, "G1", 50_000L);
        assertEquals(DonationStatus.FAILED, donation.getStatus());
        assertEquals(0, campaign.getRaisedAmount());
    }
}
