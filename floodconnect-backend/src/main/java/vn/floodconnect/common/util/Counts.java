package vn.floodconnect.common.util;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Kết quả truy vấn đếm theo nhóm ([id, số lượng] mỗi dòng) thành Map id → số lượng; id không có dòng nghĩa là 0. */
public final class Counts {
    private Counts() {}

    public static Map<Long, Long> byId(List<Object[]> rows) {
        Map<Long, Long> out = new HashMap<>();
        for (Object[] r : rows) out.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        return out;
    }
}
