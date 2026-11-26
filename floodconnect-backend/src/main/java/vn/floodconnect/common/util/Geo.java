package vn.floodconnect.common.util;

public final class Geo {
    private Geo() {}
    private static final double R = 6371000;

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1), dLon = Math.toRadians(lon2 - lon1);
        double h = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * R * Math.asin(Math.sqrt(h));
    }

    /** Làm tròn tọa độ còn 3 chữ số thập phân (sai lệch khoảng 100 m): đủ biết khu vực, không chỉ ra được căn nhà. */
    public static double coarse(double deg) {
        return Math.round(deg * 1000) / 1000.0;
    }
}
