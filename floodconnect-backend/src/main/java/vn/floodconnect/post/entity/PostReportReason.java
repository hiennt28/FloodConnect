package vn.floodconnect.post.entity;

public enum PostReportReason {
    FAKE_NEWS("Tin giả"), INAPPROPRIATE("Nội dung không phù hợp"), SPAM("Spam"), HARASSMENT("Quấy rối"), OTHER("Khác");

    public final String label;
    PostReportReason(String label) { this.label = label; }
}
