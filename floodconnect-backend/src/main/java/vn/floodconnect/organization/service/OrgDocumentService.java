package vn.floodconnect.organization.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import vn.floodconnect.common.exception.ApiException;
import vn.floodconnect.common.storage.FileStorageService;
import vn.floodconnect.organization.entity.OrgDocSlot;
import vn.floodconnect.organization.entity.OrgType;
import vn.floodconnect.organization.entity.OrganizationDocument;
import vn.floodconnect.organization.entity.RescueOrganization;
import vn.floodconnect.organization.repository.OrganizationDocumentRepository;
import vn.floodconnect.security.CurrentUser;
import vn.floodconnect.user.entity.Role;
import vn.floodconnect.user.entity.User;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Hồ sơ xác minh của tổ chức theo từng ô: loại tổ chức nào nộp ô nào, kiểm tra và lưu tệp.
 * Nhà nước, Xã hội, Hậu cần: bắt buộc giấy tờ chính. Thiện nguyện: bắt buộc CCCD mặt trước và mặt sau (mỗi mặt một tệp).
 * Minh chứng hoạt động không bắt buộc với mọi loại.
 */
@Service
@RequiredArgsConstructor
public class OrgDocumentService {
    private final OrganizationDocumentRepository docs;
    private final FileStorageService storage;
    private final CurrentUser current;

    /** Tệp tải lên theo từng ô; ô không có tệp là danh sách rỗng. */
    public record Uploads(List<MultipartFile> main, List<MultipartFile> idFront, List<MultipartFile> idBack, List<MultipartFile> evidence) {
        public static Uploads of(List<MultipartFile> main, List<MultipartFile> idFront, List<MultipartFile> idBack, List<MultipartFile> evidence) {
            return new Uploads(FileStorageService.nonEmpty(main), FileStorageService.nonEmpty(idFront),
                    FileStorageService.nonEmpty(idBack), FileStorageService.nonEmpty(evidence));
        }
        public List<MultipartFile> get(OrgDocSlot s) {
            return switch (s) { case MAIN -> main; case ID_FRONT -> idFront; case ID_BACK -> idBack; case EVIDENCE -> evidence; };
        }
    }

    private static final Map<OrgType, String> MAIN_LABEL = Map.of(
            OrgType.STATE, "Văn bản / Quyết định thành lập",
            OrgType.SOCIAL, "Quyết định thành lập / Điều lệ / Giấy phép hoạt động",
            OrgType.LOGISTICS, "Giấy chứng nhận Đăng ký kinh doanh");

    public static List<OrgDocSlot> requiredSlots(OrgType t) {
        return t == OrgType.VOLUNTEER ? List.of(OrgDocSlot.ID_FRONT, OrgDocSlot.ID_BACK) : List.of(OrgDocSlot.MAIN);
    }

    private static boolean allowed(OrgType t, OrgDocSlot s) { return s == OrgDocSlot.EVIDENCE || requiredSlots(t).contains(s); }

    public static String label(OrgType t, OrgDocSlot s) {
        return switch (s) {
            case MAIN -> MAIN_LABEL.getOrDefault(t, "Giấy tờ pháp lý");
            case ID_FRONT -> "CCCD/CMND mặt trước của trưởng nhóm";
            case ID_BACK -> "CCCD/CMND mặt sau của trưởng nhóm";
            case EVIDENCE -> "Minh chứng hoạt động";
        };
    }

    /** Tên phần multipart của ô, cũng là khóa lỗi theo trường trả về cho giao diện. */
    public static String field(OrgDocSlot s) {
        return switch (s) { case MAIN -> "documents"; case ID_FRONT -> "idFront"; case ID_BACK -> "idBack"; case EVIDENCE -> "evidence"; };
    }

    /** Ô của tệp. Tệp nộp trước khi tách ô (slot rỗng) được coi là giấy tờ chính, trừ nhóm thiện nguyện (không thuộc ô nào). */
    static OrgDocSlot slotOf(OrganizationDocument d, OrgType t) {
        if (d.getSlot() != null) return d.getSlot();
        return t == OrgType.VOLUNTEER ? null : OrgDocSlot.MAIN;
    }

    /**
     * Kiểm tra tệp theo từng ô và ghi lỗi vào errors.
     * @param kept các ô đã có tệp từ trước và vẫn được giữ (rỗng khi đăng ký mới)
     */
    public void check(OrgType t, Uploads u, Set<OrgDocSlot> kept, Map<String, String> errors) {
        for (OrgDocSlot s : OrgDocSlot.values()) {
            List<MultipartFile> files = u.get(s);
            if (!files.isEmpty() && !allowed(t, s))
                errors.put(field(s), "Loại tổ chức này không nộp mục hồ sơ \"" + label(t, s) + "\".");
            else if (files.size() > 1 && (s == OrgDocSlot.ID_FRONT || s == OrgDocSlot.ID_BACK))
                errors.put(field(s), "Chỉ tải 1 tệp cho \"" + label(t, s) + "\".");
            else if (files.isEmpty() && requiredSlots(t).contains(s) && !kept.contains(s))
                errors.put(field(s), "Vui lòng tải lên \"" + label(t, s) + "\".");
        }
    }

    /**
     * Lưu tệp của mọi ô (kiểm tra định dạng PDF/JPG/PNG và dung lượng 5 MB khi lưu), nhãn theo loại tổ chức và ô.
     * Giấy tờ pháp lý / CCCD lưu RIÊNG TƯ (không có đường dẫn công khai): chỉ mở được qua {@link #open} (Admin, chính tổ chức đó).
     */
    @Transactional
    public void saveAll(RescueOrganization o, Uploads u) {
        for (OrgDocSlot s : OrgDocSlot.values()) {
            for (MultipartFile f : u.get(s)) {
                var st = storage.storePrivate(f, "org-docs", FileStorageService.DOCS);
                OrganizationDocument d = new OrganizationDocument();
                d.setOrg(o); d.setSlot(s); d.setDocLabel(label(o.getOrgType(), s));
                d.setStorageKey(st.url()); d.setFileType(FileStorageService.fileType(st.mime()));
                docs.save(d);
            }
        }
    }

    /** Xóa mọi giấy tờ của tổ chức (dòng dữ liệu và tệp, tệp xóa sau khi commit). Dùng khi xóa đăng ký bỏ dở. */
    @Transactional
    public void deleteAllOf(RescueOrganization o) {
        List<OrganizationDocument> all = docs.findByOrgIdOrderByIdAsc(o.getId());
        all.forEach(d -> storage.deletePrivateAfterCommit(d.getStorageKey()));
        docs.deleteAll(all);
    }

    /** Nội dung một giấy tờ để trả về: tệp riêng tư đọc được, hoặc đường dẫn ngoài (ảnh mẫu, dữ liệu cũ) để chuyển hướng. */
    public record DocFile(FileStorageService.PrivateFile file, String externalUrl, String filename) {}

    /**
     * Mở một giấy tờ: CHỈ Admin hoặc tài khoản của chính tổ chức sở hữu giấy tờ. Người khác nhận 404 như thể giấy tờ không tồn tại
     * (không cho dò mã giấy tờ của tổ chức khác).
     */
    @Transactional(readOnly = true)
    public DocFile open(Long docId) {
        User u = current.get();
        OrganizationDocument d = docs.findById(docId).orElseThrow(() -> ApiException.notFound("Không tìm thấy giấy tờ."));
        boolean allowed = u.getRole() == Role.ADMIN || d.getOrg().getUser().getId().equals(u.getId());
        if (!allowed) throw ApiException.notFound("Không tìm thấy giấy tờ.");
        String key = d.getStorageKey();
        String name = "giay-to-" + d.getId();
        FileStorageService.PrivateFile f = storage.openPrivate(key);
        if (f != null) return new DocFile(f, null, name);
        if (key != null && key.matches("(?i)^https?://\\S+$")) return new DocFile(null, key, name); // ảnh mẫu / dữ liệu cũ chưa chuyển
        throw ApiException.notFound("Không tìm thấy giấy tờ.");
    }

    /**
     * Gửi lại hồ sơ bị từ chối: ô nào có tệp mới thì thay toàn bộ tệp cũ của ô đó, các ô khác giữ nguyên.
     * Đổi loại tổ chức thì giấy tờ cũ (mọi ô trừ minh chứng) bị bỏ vì không còn đúng loại, phải nộp lại theo loại mới.
     * Kiểm tra trước khi xóa hay lưu tệp nào, để lỗi thì hồ sơ cũ còn nguyên và không sinh tệp mồ côi.
     */
    @Transactional
    public void resubmit(RescueOrganization o, boolean typeChanged, Uploads u) {
        OrgType t = o.getOrgType();
        List<OrganizationDocument> current = docs.findByOrgIdOrderByIdAsc(o.getId());
        Predicate<OrganizationDocument> keep = d -> {
            OrgDocSlot s = slotOf(d, t);
            if (typeChanged) return s == OrgDocSlot.EVIDENCE;
            return s == null || u.get(s).isEmpty();
        };
        Set<OrgDocSlot> kept = current.stream().filter(keep).map(d -> slotOf(d, t)).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, String> e = new LinkedHashMap<>();
        check(t, u, kept, e);
        if (!e.isEmpty()) throw ApiException.validation(e);
        List<OrganizationDocument> replaced = current.stream().filter(keep.negate()).toList();
        replaced.forEach(d -> storage.deletePrivateAfterCommit(d.getStorageKey())); // tệp bị thay: xóa luôn, không để CCCD cũ tồn đọng
        docs.deleteAll(replaced);
        saveAll(o, u);
    }
}
