package vn.floodconnect.campaign.entity;

/**
 * EXPIRED: người ủng hộ bỏ dở, quá hạn (app.payment.pending-expire-minutes) cổng vẫn chưa báo kết quả. Khác FAILED (cổng báo
 * thất bại): nếu cổng báo THÀNH CÔNG về muộn (IPN trễ/gửi lại) thì vẫn ghi nhận, vì tiền đã bị trừ của người ủng hộ.
 */
public enum DonationStatus { PENDING, SUCCESS, FAILED, EXPIRED }
