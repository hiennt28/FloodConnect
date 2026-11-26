package vn.floodconnect.campaign.entity;

/**
 * Phương thức thanh toán của khoản ủng hộ. Hệ thống chỉ nhận ủng hộ qua VNPay (trên trang VNPay người ủng hộ chọn ngân hàng,
 * quét QR hoặc nhập thẻ). MOMO chỉ còn để đọc các khoản ủng hộ cũ đã lưu trước khi bỏ cổng MoMo.
 */
public enum PaymentMethod { MOMO, VNPAY }
