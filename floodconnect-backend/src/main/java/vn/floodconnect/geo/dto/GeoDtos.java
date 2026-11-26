package vn.floodconnect.geo.dto;

import com.fasterxml.jackson.annotation.JsonRawValue;

public final class GeoDtos {
    private GeoDtos() {}

    /** Một phường/xã: mã theo Tổng cục Thống kê (trùng vn-admin-2025.json ở frontend), tên đầy đủ, tỉnh chứa nó. */
    public record WardRef(String code, String name, String provinceCode, String provinceName) {}

    /**
     * Kết quả "điểm này thuộc phường/xã nào".
     * ward = null: điểm không thuộc phường/xã nào (VD ngoài biển).
     * approximate = true: điểm không nằm trong ranh giới xã nào mà rơi vào khe hở giữa hai xã (ranh giới đã đơn giản hóa,
     * sai số khoảng 33 m), hệ thống lấy xã gần nhất trong khoảng 110 m; giao diện chỉ nên gợi ý, không khẳng định.
     */
    public record LocateResult(WardRef ward, boolean approximate) {}

    /**
     * Thông tin một phường/xã để bản đồ nhảy tới và vẽ đường viền.
     * center: điểm đại diện luôn nằm trong xã (không phải trọng tâm hình học, vì xã hình lưỡi liềm có trọng tâm nằm ngoài).
     * outline: đường viền dạng GeoJSON MultiPolygon (đã đơn giản hóa thêm cho nhẹ), trả nguyên chuỗi JSON.
     */
    public record WardDetail(String code, String name, String provinceCode, String provinceName, Double areaKm2,
                             double centerLat, double centerLng, @JsonRawValue String outline) {}

    /**
     * Phường/xã kèm điểm đại diện, không kèm đường viền (nhẹ hơn WardDetail). Dùng khi người dùng chỉ nhập địa chỉ chữ
     * (Tỉnh, Phường/Xã, số nhà): server tự lấy điểm đại diện của xã làm tọa độ, không bắt người dùng nhập vĩ độ, kinh độ.
     */
    public record WardCenter(String code, String name, String provinceCode, String provinceName, double lat, double lng) {}
}
