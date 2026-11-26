package vn.floodconnect.incident.entity;

/**
 * Ai đưa ca SOS sang Hoàn thành.
 * ORG: tổ chức phụ trách bấm Hoàn thành sau khi cứu hộ xong (chỉ những ca này được tính là thành tích cứu hộ của tổ chức).
 * CITIZEN: người dân báo "Tôi đã an toàn" khi ca đang Đã tiếp nhận (được đơn vị khác cứu, nước rút...).
 * ADMIN: Admin xác nhận hoàn thành sau khi liên hệ (người dân đã an toàn nhưng không tự báo được, hoặc tổ chức quên bấm).
 * Ca hoàn thành trước khi có cột này để trống, hiểu là ORG.
 */
public enum CompletedBy { ORG, CITIZEN, ADMIN }
