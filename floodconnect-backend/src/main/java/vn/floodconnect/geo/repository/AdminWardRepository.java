package vn.floodconnect.geo.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import vn.floodconnect.geo.dto.GeoDtos.LocateResult;
import vn.floodconnect.geo.dto.GeoDtos.WardCenter;
import vn.floodconnect.geo.dto.GeoDtos.WardDetail;
import vn.floodconnect.geo.dto.GeoDtos.WardRef;
import java.util.Optional;

/**
 * Đọc bảng admin_wards (ranh giới phường/xã, Flyway tạo ở V1 và nạp dữ liệu ở V2__admin_wards_data.sql).
 * Bảng không qua Hibernate (cột geometry cần thư viện Hibernate Spatial), nên truy vấn SQL thuần bằng JdbcTemplate.
 * ST_MakePoint nhận kinh độ trước, vĩ độ sau; 4326 là hệ tọa độ GPS (WGS84).
 */
@Repository
@RequiredArgsConstructor
public class AdminWardRepository {
    /** Khoảng tìm xã gần nhất khi điểm rơi vào khe hở giữa hai xã: 0.001 độ, khoảng 110 m */
    static final double NEAR_DEG = 0.001;
    /** Đơn giản hóa thêm khi trả đường viền để vẽ: 0.0005 độ, khoảng 55 m (chỉ để nhìn, không dùng để kiểm tra) */
    static final double OUTLINE_TOLERANCE_DEG = 0.0005;

    private final JdbcTemplate jdbc;

    /**
     * Phường/xã chứa điểm (lat, lng). Một câu truy vấn xử lý cả hai trường hợp: điểm nằm trong xã (khoảng cách = 0) và điểm
     * rơi vào khe hở (lấy xã gần nhất trong NEAR_DEG). ST_DWithin dùng được chỉ mục GiST nên chỉ xét vài xã quanh điểm.
     * Hai xã cùng chứa điểm (chồng lấn do đơn giản hóa) thì lấy theo mã để kết quả luôn cố định.
     */
    public Optional<LocateResult> findAt(double lat, double lng) {
        String sql = """
                with pt as (select ST_SetSRID(ST_MakePoint(?, ?), 4326) as g)
                select w.code, w.name, w.province_code, w.province_name, ST_Distance(w.geom, pt.g) as d
                from admin_wards w, pt
                where ST_DWithin(w.geom, pt.g, ?)
                order by d, w.code
                limit 1""";
        return jdbc.query(sql, (rs, i) -> new LocateResult(
                new WardRef(rs.getString("code"), rs.getString("name"), rs.getString("province_code"), rs.getString("province_name")),
                rs.getDouble("d") > 0), lng, lat, NEAR_DEG).stream().findFirst();
    }

    /** Thông tin một xã theo mã. center trống (chưa chạy câu UPDATE cuối của file dữ liệu) thì tính tại chỗ. */
    public Optional<WardDetail> findDetail(String code) {
        String sql = """
                select code, name, province_code, province_name, area_km2,
                       ST_Y(coalesce(center, ST_PointOnSurface(geom))) as lat,
                       ST_X(coalesce(center, ST_PointOnSurface(geom))) as lng,
                       ST_AsGeoJSON(ST_SimplifyPreserveTopology(geom, ?), 5) as outline
                from admin_wards where code = ?""";
        return jdbc.query(sql, (rs, i) -> new WardDetail(rs.getString("code"), rs.getString("name"), rs.getString("province_code"),
                rs.getString("province_name"), rs.getObject("area_km2", Double.class), rs.getDouble("lat"), rs.getDouble("lng"),
                rs.getString("outline")), OUTLINE_TOLERANCE_DEG, code).stream().findFirst();
    }

    /** Tên và điểm đại diện của một xã theo mã (không tính đường viền). */
    public Optional<WardCenter> findCenter(String code) {
        String sql = """
                select code, name, province_code, province_name,
                       ST_Y(coalesce(center, ST_PointOnSurface(geom))) as lat,
                       ST_X(coalesce(center, ST_PointOnSurface(geom))) as lng
                from admin_wards where code = ?""";
        return jdbc.query(sql, (rs, i) -> new WardCenter(rs.getString("code"), rs.getString("name"), rs.getString("province_code"),
                rs.getString("province_name"), rs.getDouble("lat"), rs.getDouble("lng")), code).stream().findFirst();
    }
}
