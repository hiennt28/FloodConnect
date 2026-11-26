package vn.floodconnect.rescue.service;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.incident.entity.DangerLevel;
import vn.floodconnect.incident.entity.Incident;
import vn.floodconnect.incident.entity.IncidentStatus;
import vn.floodconnect.incident.repository.IncidentRepository;
import vn.floodconnect.incident.service.SosService;
import vn.floodconnect.rescue.dto.RescueDtos.AdminSosMap;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.IntStream;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Bản đồ điều phối của Admin: chia trần giữa ca đang hoạt động và ca đã kết thúc, đọc tham số lọc. Câu SQL thật: AdminSosMapIT. */
class AdminSosMapServiceTest {
    private final IncidentRepository incidents = mock(IncidentRepository.class);

    private static List<Incident> cases(int n) {
        return IntStream.range(0, n).mapToObj(k -> {
            Incident i = new Incident();
            i.setId((long) k + 1); i.setCode("SOS-" + k); i.setStatus(IncidentStatus.PENDING); i.setDangerLevel(DangerLevel.CAO);
            return i;
        }).toList();
    }

    @SuppressWarnings("unchecked")
    private void pages(List<Incident> active, long activeTotal, List<Incident> ended, long endedTotal) {
        when(incidents.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(a -> new PageImpl<>(active, a.getArgument(1), activeTotal))
                .thenAnswer(a -> new PageImpl<>(ended, a.getArgument(1), endedTotal));
    }

    @Test @SuppressWarnings("unchecked")
    void caKetThucChiDuocPhanConLaiCuaTran() {
        AdminSosMapService s = new AdminSosMapService(incidents, 5);
        pages(cases(2), 2, cases(3), 40);
        AdminSosMap r = s.cases("ALL", null, null);
        ArgumentCaptor<Pageable> pg = ArgumentCaptor.forClass(Pageable.class);
        verify(incidents, times(2)).findAll(any(Specification.class), pg.capture());
        assertEquals(5, pg.getAllValues().get(0).getPageSize()); // ca đang hoạt động: tối đa cả trần
        assertEquals(3, pg.getAllValues().get(1).getPageSize()); // ca đã kết thúc: phần còn lại
        assertEquals(5, r.cases().size());
        assertEquals(42, r.total());
        assertEquals(5, r.limit());
    }

    @Test @SuppressWarnings("unchecked")
    void caDangHoatDongDayTranThiCaKetThucChiDuocDem() {
        AdminSosMapService s = new AdminSosMapService(incidents, 3);
        pages(cases(3), 10, List.of(), 0);
        when(incidents.count(any(Specification.class))).thenReturn(7L);
        AdminSosMap r = s.cases("ALL", null, 30);
        verify(incidents, times(1)).findAll(any(Specification.class), any(Pageable.class));
        assertEquals(3, r.cases().size());
        assertEquals(17, r.total());
    }

    @Test @SuppressWarnings("unchecked")
    void locDangHoatDongKhongTruyVanCaKetThuc() {
        AdminSosMapService s = new AdminSosMapService(incidents, 1000);
        pages(cases(4), 4, List.of(), 0);
        AdminSosMap r = s.cases("ACTIVE", DangerLevel.CAO, null);
        verify(incidents, times(1)).findAll(any(Specification.class), any(Pageable.class));
        verify(incidents, never()).count(any(Specification.class));
        assertEquals(4, r.total());
    }

    @Test void soNgayMacDinhVaGioiHan() {
        AdminSosMapService s = new AdminSosMapService(incidents, 1000);
        pages(List.of(), 0, List.of(), 0);
        assertEquals(AdminSosMapService.DEFAULT_DAYS, s.cases("COMPLETED", null, null).days());
        pages(List.of(), 0, List.of(), 0);
        assertEquals(AdminSosMapService.MAX_DAYS, s.cases("COMPLETED", null, 500).days());
        pages(List.of(), 0, List.of(), 0);
        assertEquals(1, s.cases("COMPLETED", null, 0).days());
    }

    @Test void docThamSoTrangThai() {
        assertEquals(EnumSet.allOf(IncidentStatus.class), AdminSosMapService.statuses(null));
        assertEquals(EnumSet.allOf(IncidentStatus.class), AdminSosMapService.statuses(""));
        assertEquals(EnumSet.allOf(IncidentStatus.class), AdminSosMapService.statuses("all"));
        assertEquals(SosService.ACTIVE, AdminSosMapService.statuses("ACTIVE"));
        assertEquals(EnumSet.of(IncidentStatus.COMPLETED), AdminSosMapService.statuses("completed"));
        ApiException e = assertThrows(ApiException.class, () -> AdminSosMapService.statuses("DONE"));
        assertEquals("INVALID_STATUS", e.getCode());
    }

    @Test void tranKhongNhoHonMot() {
        AdminSosMapService s = new AdminSosMapService(incidents, 0);
        pages(List.of(), 0, List.of(), 0);
        assertEquals(1, s.cases("ACTIVE", null, null).limit());
    }
}
