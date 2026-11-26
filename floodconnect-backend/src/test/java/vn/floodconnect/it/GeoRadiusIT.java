package vn.floodconnect.it;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.organization.entity.OperatingStatus;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.user.entity.Role;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Các câu SQL PostGIS (ST_DWithin trên cột geog) chạy trên CSDL thật. Unit test không kiểm tra được vì kho dữ liệu là đồ giả.
 * Khoảng cách theo phương bắc: 1 độ vĩ ≈ 111.195 m.
 */
class GeoRadiusIT extends AbstractIT {
    private static final double LAT = 16.0544, LNG = 108.2022; // Đà Nẵng
    private static final double M_PER_DEG = 111_195;

    @Autowired IncidentRepository incidents;

    private int n = 0;

    private Incident sos(double metersNorth, IncidentStatus st) {
        Incident i = new Incident();
        i.setCode("IT-" + (++n)); i.setCitizen(user(Role.CITIZEN)); i.setDescription("Nước ngập");
        i.setLatitude(LAT + metersNorth / M_PER_DEG); i.setLongitude(LNG); i.setStatus(st); i.setDangerLevel(DangerLevel.CAO);
        return incidents.save(i);
    }

    @Test void timCaTrongBanKinh500m() {
        Incident here = sos(0, IncidentStatus.PENDING);
        Incident at400 = sos(400, IncidentStatus.ASSIGNED);
        sos(600, IncidentStatus.PENDING);              // ngoài bán kính
        sos(100, IncidentStatus.COMPLETED);            // trong bán kính nhưng không còn hoạt động
        Set<String> found = incidents.findWithinRadius(SosService.ACTIVE, Instant.EPOCH, LAT, LNG, 500).stream()
                .map(Incident::getCode).collect(Collectors.toSet());
        assertEquals(Set.of(here.getCode(), at400.getCode()), found);
    }

    /** Truy vấn theo một bán kính chung (nhóm tổ chức so sánh gửi cho dịch vụ AI dùng 50 km). */
    @Test void timToChucSanSangTrongBanKinhChungGanNhatTruoc() {
        RescueOrganization near = activeOrg(user(Role.RESCUE_ORG), LAT + 10_000 / M_PER_DEG, LNG);
        RescueOrganization nearer = activeOrg(user(Role.RESCUE_ORG), LAT + 2_000 / M_PER_DEG, LNG);
        activeOrg(user(Role.RESCUE_ORG), LAT + 60_000 / M_PER_DEG, LNG);           // quá 50 km
        RescueOrganization busy = activeOrg(user(Role.RESCUE_ORG), LAT + 1_000 / M_PER_DEG, LNG);
        busy.setOperatingStatus(OperatingStatus.BUSY); orgs.save(busy);              // không Sẵn sàng
        List<Long> ids = orgs.findAvailableWithin(LAT, LNG, 50_000).stream().map(RescueOrganization::getId).toList();
        assertEquals(List.of(nearer.getId(), near.getId()), ids);
    }

    /** Tổ chức của tài khoản u, trụ sở cách điểm (LAT, LNG) metersNorth mét về phía bắc, bán kính hoạt động tự khai radiusKm. */
    private RescueOrganization org(vn.floodconnect.user.entity.User u, double metersNorth, double radiusKm) {
        RescueOrganization o = activeOrg(u, LAT + metersNorth / M_PER_DEG, LNG);
        o.setServiceRadiusKm(radiusKm);
        return orgs.save(o);
    }

    private RescueOrganization org(double metersNorth, double radiusKm) { return org(user(Role.RESCUE_ORG), metersNorth, radiusKm); }

    /** REVIEW-006: chỉ tổ chức mà ca nằm trong bán kính CHÍNH HỌ tự khai, không còn 50 km chung cho mọi tổ chức. */
    @Test void timToChucTheoBanKinhTuKhaiCuaTungToChuc() {
        org(30_000, 10);                                         // cách 30 km, chỉ phục vụ 10 km: không
        RescueOrganization covers = org(30_000, 40);             // cách 30 km, phục vụ 40 km: có
        RescueOrganization far = org(60_000, 80);                // cách 60 km (quá 50 km cũ), phục vụ 80 km: có
        RescueOrganization small = org(3_000, 5);                // cách 3 km, phục vụ 5 km: có, gần nhất
        RescueOrganization busy = org(1_000, 20);
        busy.setOperatingStatus(OperatingStatus.BUSY); orgs.save(busy); // trong bán kính nhưng không Sẵn sàng: không
        List<Long> ids = orgs.findAvailableCovering(LAT, LNG).stream().map(RescueOrganization::getId).toList();
        assertEquals(List.of(small.getId(), covers.getId(), far.getId()), ids);
    }



    /** Gửi SOS qua API: chỉ tổ chức có ca trong bán kính tự khai nhận thông báo "Ca SOS mới trong khu vực". */
    @Test void thongBaoCaMoiChiGuiToChucCoCaTrongBanKinh() throws Exception {
        RescueOrganization out = org(30_000, 10);
        RescueOrganization in = org(30_000, 40);
        var citizen = user(Role.CITIZEN);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/sos")
                        .header("Authorization", bearer(citizen)).contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .content("{\"description\":\"Nhà ngập sâu 1m, có người già mắc kẹt trên gác cần cứu gấp\",\"latitude\":" + LAT
                                + ",\"longitude\":" + LNG + ",\"forceCreate\":true}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk());
        String q = "select count(*) from notifications where user_id = ? and type = 'SOS_NEW'";
        assertEquals(1, jdbc.queryForObject(q, Integer.class, in.getUser().getId()));
        assertEquals(0, jdbc.queryForObject(q, Integer.class, out.getUser().getId()));
    }

    /** Danh sách ca đề xuất của tổ chức: chỉ ca trong bán kính tự khai. */
    @Test void caDeXuatChiTrongBanKinhCuaToChuc() throws Exception {
        var orgUser = user(Role.RESCUE_ORG);
        org(orgUser, 0, 10);
        Incident inside = sos(5_000, IncidentStatus.PENDING);
        sos(20_000, IncidentStatus.PENDING);                     // ngoài 10 km
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/org/sos/suggested")
                        .header("Authorization", bearer(orgUser)))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].code").value(inside.getCode()));
    }
}
