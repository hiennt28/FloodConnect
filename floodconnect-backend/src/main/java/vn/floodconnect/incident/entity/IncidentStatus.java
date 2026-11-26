package vn.floodconnect.incident.entity;

/** INVALID: Admin đã xác minh và kết luận ca không phải yêu cầu cứu hộ hợp lệ (trạng thái kết thúc, như COMPLETED và CANCELLED). */
public enum IncidentStatus { PENDING, ASSIGNED, ACCEPTED, COMPLETED, CANCELLED, INVALID }
