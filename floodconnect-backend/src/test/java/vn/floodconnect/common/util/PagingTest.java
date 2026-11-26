package vn.floodconnect.common.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import static org.junit.jupiter.api.Assertions.*;

class PagingTest {
    @Test void giaTriHopLeGiuNguyen() {
        PageRequest p = Paging.of(2, 20);
        assertEquals(2, p.getPageNumber());
        assertEquals(20, p.getPageSize());
    }

    @Test void sizeQuaLonBiKepVeToiDa() {
        assertEquals(Paging.MAX_SIZE, Paging.of(0, 1_000_000).getPageSize());
        assertEquals(100, Paging.of(0, 100).getPageSize());
        assertEquals(100, Paging.of(0, 101).getPageSize());
    }

    @Test void sizeKhongHoacAmThanhMot() {
        assertEquals(1, Paging.of(0, 0).getPageSize());
        assertEquals(1, Paging.of(0, -5).getPageSize());
    }

    @Test void pageAmThanhTrangDau() {
        assertEquals(0, Paging.of(-1, 10).getPageNumber());
        assertEquals(0, Paging.of(Integer.MIN_VALUE, 10).getPageNumber());
    }

    @Test void viTriBatDauKhongVuotInt() {
        for (int size : new int[] {1, 7, 20, 100, 1_000_000}) {
            PageRequest p = Paging.of(Integer.MAX_VALUE, size);
            assertTrue(p.getOffset() <= Integer.MAX_VALUE, "offset " + p.getOffset() + " với size " + size);
        }
    }

    @Test void giuThuTuSapXep() {
        Sort sort = Sort.by("createdAt").descending();
        assertEquals(sort, Paging.of(0, 10, sort).getSort());
        assertTrue(Paging.of(0, 10).getSort().isUnsorted());
    }
}
