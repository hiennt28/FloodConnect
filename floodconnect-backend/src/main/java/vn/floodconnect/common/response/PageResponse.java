package vn.floodconnect.common.response;

import org.springframework.data.domain.Page;
import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> items, int page, int size, long total, boolean last) {
    public static <E, T> PageResponse<T> of(Page<E> p, Function<E, T> map) {
        return new PageResponse<>(p.getContent().stream().map(map).toList(), p.getNumber(), p.getSize(), p.getTotalElements(), p.isLast());
    }
    /** Chuyển cả trang trong một lần gọi (bộ chuyển tải dữ liệu đi kèm cho cả trang thay vì cho từng phần tử). */
    public static <E, T> PageResponse<T> ofAll(Page<E> p, Function<List<E>, List<T>> mapAll) {
        return new PageResponse<>(mapAll.apply(p.getContent()), p.getNumber(), p.getSize(), p.getTotalElements(), p.isLast());
    }
    public static <T> PageResponse<T> of(List<T> all, int page, int size) {
        int from = Math.min(all.size(), page * size), to = Math.min(all.size(), from + size);
        return new PageResponse<>(all.subList(from, to), page, size, all.size(), to >= all.size());
    }
}
