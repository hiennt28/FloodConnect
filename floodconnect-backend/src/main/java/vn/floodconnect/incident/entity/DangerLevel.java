package vn.floodconnect.incident.entity;

public enum DangerLevel {
    KHONG(0, "Không"), THAP(1, "Thấp"), TRUNG_BINH(2, "Trung bình"), CAO(3, "Cao"), KHAN_CAP(4, "Khẩn cấp");
    public final int weight;
    /** Nhãn tiếng Việt dùng trong lý do AI và nội dung thông báo (giống nhãn hiển thị ở giao diện). */
    public final String label;
    DangerLevel(int w, String label) { this.weight = w; this.label = label; }
    public static DangerLevel of(int w) {
        for (DangerLevel d : values()) if (d.weight == w) return d;
        return KHONG;
    }
    /** Nâng mức; KHONG không bị nâng. Không bao giờ hạ mức. */
    public DangerLevel bump(int n) { return this == KHONG ? this : of(Math.min(4, weight + n)); }
    public static DangerLevel max(DangerLevel a, DangerLevel b) { return a.weight >= b.weight ? a : b; }
}
