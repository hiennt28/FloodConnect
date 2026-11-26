package vn.floodconnect.geo.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.util.Validators;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.dto.GeoDtos.WardDetail;
import vn.floodconnect.geo.repository.AdminWardRepository;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Địa giới hành chính: tọa độ thuộc phường/xã nào, thông tin một xã (điểm đại diện, đường viền).
 * Dữ liệu chạy trên CSDL của hệ thống, không gọi dịch vụ bản đồ bên ngoài (không tốn lượt, không phụ thuộc mạng ngoài lúc lũ).
 * Form SOS chỉ dùng như tiện ích: giao diện gặp lỗi GEO_UNAVAILABLE thì bỏ qua bước kiểm tra, không chặn gửi SOS.
 * Bài đăng và hồ sơ tổ chức thì cần nó: người dùng chỉ chọn Tỉnh, Phường/Xã, server lấy điểm đại diện của xã làm tọa độ (center).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeoService {
    private static final Pattern WARD_CODE = Pattern.compile("\\d{5}");
    private final AdminWardRepository wards;

    public LocateResult locate(Double lat, Double lng) {
        Validators.coords(lat, lng);
        return query(() -> wards.findAt(lat, lng).orElse(new LocateResult(null, false)));
    }

    public WardDetail ward(String code) {
        checkCode(code);
        return query(() -> wards.findDetail(code)).orElseThrow(() -> ApiException.notFound("Không tìm thấy phường/xã."));
    }

    /**
     * Tên chuẩn và điểm đại diện của xã người dùng đã chọn. Mã sai hoặc không có trong bảng là lỗi đầu vào (400 INVALID_WARD),
     * vì mã lấy từ danh sách chọn ở giao diện; bảng địa giới chưa nạp thì vẫn là 503 GEO_UNAVAILABLE.
     */
    public WardCenter center(String code) {
        checkCode(code);
        return query(() -> wards.findCenter(code))
                .orElseThrow(() -> ApiException.bad("INVALID_WARD", "Phường/Xã đã chọn không hợp lệ, vui lòng chọn lại."));
    }

    /**
     * Ghim (lat, lng) phải nằm trong xã w người dùng đã chọn. Dùng cho trụ sở tổ chức: tọa độ do người dùng đặt ghim nên chính xác,
     * nhưng địa chỉ chữ và ghim phải khớp nhau. Ghim sát ranh giới (rơi vào khe hở) thì locate đã lấy xã gần nhất trong ~110 m.
     */
    public void requireInWard(Double lat, Double lng, WardCenter w) {
        LocateResult at = locate(lat, lng);
        if (at.ward() == null)
            throw ApiException.bad("LOCATION_OUTSIDE_WARD", "Ghim trên bản đồ không nằm trong phường/xã nào, hãy kiểm tra lại.");
        if (!at.ward().code().equals(w.code()))
            throw ApiException.bad("LOCATION_OUTSIDE_WARD", "Ghim trên bản đồ đang nằm ở " + at.ward().name() + ", " + at.ward().provinceName()
                    + ", không thuộc " + w.name() + " đã chọn. Hãy kéo ghim về đúng chỗ hoặc đổi địa chỉ.");
    }

    private static void checkCode(String code) {
        if (code == null || !WARD_CODE.matcher(code).matches())
            throw ApiException.bad("INVALID_WARD_CODE", "Mã phường/xã phải gồm 5 chữ số.");
    }

    /** Bảng admin_wards chưa tạo/chưa nạp dữ liệu, hoặc CSDL chưa cài PostGIS: báo lỗi riêng để giao diện bỏ qua tính năng này. */
    private <T> T query(Supplier<T> q) {
        try {
            return q.get();
        } catch (DataAccessException e) {
            log.warn("Không truy vấn được địa giới hành chính (bảng admin_wards do Flyway V1, V2 tạo): {}", e.getMessage());
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "GEO_UNAVAILABLE",
                    "Tạm thời chưa tra cứu được phường/xã, vui lòng thử lại sau.", null);
        }
    }
}
