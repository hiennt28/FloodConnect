package vn.floodconnect.rescue.entity;

/**
 * CANCELLED: phân công bị hủy hoặc thu hồi, không phải tổ chức từ chối (người dân hủy ca; tổ chức bị khóa hoặc chuyển
 * Không hoạt động nên ca được trả về). Không tính vào lịch sử từ chối/không phản hồi của AI Matching.
 */
public enum AssignmentStatus { WAITING_CONFIRM, ACCEPTED, REJECTED, TIMEOUT, CANCELLED }
