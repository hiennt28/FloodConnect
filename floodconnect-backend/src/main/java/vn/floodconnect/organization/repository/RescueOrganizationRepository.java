package vn.floodconnect.organization.repository;

import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.OrgStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import java.time.Instant;
import java.util.*;

public interface RescueOrganizationRepository extends JpaRepository<RescueOrganization, Long> {
    Optional<RescueOrganization> findByUserId(Long userId);
    List<RescueOrganization> findByUserIdIn(Collection<Long> userIds);
    List<RescueOrganization> findByStatusOrderByCreatedAtAsc(OrgStatus status);
    List<RescueOrganization> findByStatusOrderByLockedAtAsc(OrgStatus status);
    List<RescueOrganization> findByStatus(OrgStatus status);
    long countByStatus(OrgStatus status);
    long countByStatusAndOperatingStatus(OrgStatus status, OperatingStatus op);

    /**
     * Tổ chức đã duyệt (ACTIVE) và đang Sẵn sàng (AVAILABLE), cách điểm (lat, lng) không quá radiusM mét, gần nhất trước.
     * Lọc bằng PostGIS như IncidentRepository.findWithinRadiusNative; toán tử <-> là khoảng cách để sắp xếp.
     * Tổ chức chưa có tọa độ có geog = NULL nên tự bị loại.
     */
    @Query(value = "select * from rescue_organizations o where o.status = 'ACTIVE' and o.operating_status = 'AVAILABLE' "
            + "and ST_DWithin(o.geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), :radius) "
            + "order by o.geog <-> CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)", nativeQuery = true)
    List<RescueOrganization> findAvailableWithin(@Param("lat") double lat, @Param("lng") double lng, @Param("radius") double radiusM);

    /**
     * Tổ chức đã duyệt và đang Sẵn sàng mà điểm (lat, lng) nằm trong BÁN KÍNH HOẠT ĐỘNG CHÍNH TỔ CHỨC TỰ KHAI (service_radius_km,
     * bắt buộc từ Flyway V10), gần nhất trước. Dùng khi báo ca SOS mới / ca bị trả về cho tổ chức trong khu vực: tổ chức chỉ nhận
     * ca trong vùng họ phục vụ được.
     */
    default List<RescueOrganization> findAvailableCovering(double lat, double lng) {
        return findAvailableCoveringNative(lat, lng, RescueOrganization.MAX_SERVICE_RADIUS_KM * 1000);
    }

    /**
     * Hai điều kiện ST_DWithin: điều kiện đầu dùng bán kính lớn nhất được khai (hằng số) nên tận dụng được chỉ mục GiST trên geog để
     * lọc nhanh; điều kiện sau so đúng bán kính của từng tổ chức (bán kính lấy từ cột thì chỉ mục không dùng được nếu đứng một mình).
     */
    @Query(value = "select * from rescue_organizations o where o.status = 'ACTIVE' and o.operating_status = 'AVAILABLE' "
            + "and ST_DWithin(o.geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), :maxRadius) "
            + "and ST_DWithin(o.geog, CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography), o.service_radius_km * 1000) "
            + "order by o.geog <-> CAST(ST_SetSRID(ST_MakePoint(:lng, :lat), 4326) AS geography)", nativeQuery = true)
    List<RescueOrganization> findAvailableCoveringNative(@Param("lat") double lat, @Param("lng") double lng, @Param("maxRadius") double maxRadiusM);

    /**
     * Tìm tổ chức theo tên, KHÔNG phân biệt dấu và hoa/thường ("yen" ra "Đội cứu hộ phường Yên Hòa"). Chỉ tổ chức có tài khoản đã
     * xác thực email (Admin có thể duyệt hồ sơ trước khi người đại diện xác thực).
     * @param q từ khóa đã chuẩn hóa và thoát ký tự đặc biệt của LIKE (UserService.searchKeyword)
     */
    default Page<RescueOrganization> searchByName(OrgStatus st, String q, Pageable p) { return searchByNameNative(st.name(), q, p); }

    /** unaccent (PostgreSQL, Flyway V5) bỏ dấu cả hai phía rồi mới so sánh. */
    @Query(value = "select o.* from rescue_organizations o join users u on u.id = o.user_id where o.status = :st and u.email_verified = true "
            + "and unaccent(lower(o.name)) like '%' || unaccent(lower(:q)) || '%' escape '\\' order by o.name",
            countQuery = "select count(*) from rescue_organizations o join users u on u.id = o.user_id where o.status = :st "
            + "and u.email_verified = true and unaccent(lower(o.name)) like '%' || unaccent(lower(:q)) || '%' escape '\\'",
            nativeQuery = true)
    Page<RescueOrganization> searchByNameNative(@Param("st") String st, @Param("q") String q, Pageable p);
}
