package vn.floodconnect.campaign.entity;

public enum CampaignReportReason {
    PROFITEERING("Có dấu hiệu trục lợi"), MISINFORMATION("Có dấu hiệu thông tin sai lệch"), OTHER("Lý do khác");

    public final String label;
    CampaignReportReason(String label) { this.label = label; }
}
