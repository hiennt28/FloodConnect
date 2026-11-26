package vn.floodconnect.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

/**
 * Tham số phân trang từ client (page, size) đi qua đây trước khi truy vấn CSDL: máy chủ không tin giá trị client gửi lên.
 * Không có giới hạn này, một request {@code ?size=1000000} sẽ tải cả bảng (và chuyển từng dòng sang DTO) trong một lần.
 * Giá trị ngoài phạm vi được kẹp lại (không báo lỗi); trường size trong kết quả trả về là số đã kẹp nên client biết số thật.
 */
public final class Paging {
    /** Số bản ghi tối đa mỗi trang. Frontend xin nhiều nhất 100 (danh sách Admin, danh bạ tổ chức). */
    public static final int MAX_SIZE = 100;

    private Paging() {}

    public static PageRequest of(int page, int size) { return of(page, size, Sort.unsorted()); }

    public static PageRequest of(int page, int size, Sort sort) {
        int s = size(size);
        return PageRequest.of(page(page, s), s, sort);
    }

    /** size < 1 thành 1 (PageRequest không nhận 0), lớn hơn MAX_SIZE thành MAX_SIZE. */
    static int size(int size) { return Math.max(1, Math.min(size, MAX_SIZE)); }

    /**
     * page < 0 thành 0. Chặn trên để vị trí bắt đầu (page × size) không vượt Integer.MAX_VALUE: Spring Data JPA chuyển vị trí này
     * sang int và báo lỗi nếu tràn; trang xa như vậy vốn không có dữ liệu nên kết quả vẫn là trang rỗng.
     */
    static int page(int page, int size) { return Math.min(Math.max(0, page), Integer.MAX_VALUE / size); }
}
