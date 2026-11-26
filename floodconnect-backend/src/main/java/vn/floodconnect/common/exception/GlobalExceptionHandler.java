package vn.floodconnect.common.exception;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import jakarta.validation.ConstraintViolationException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    public record ErrorBody(int status, String code, String message, Map<String, String> errors, Instant timestamp) {}

    private ResponseEntity<ErrorBody> body(HttpStatus s, String code, String msg, Map<String, String> errors) {
        return ResponseEntity.status(s).body(new ErrorBody(s.value(), code, msg, errors, Instant.now()));
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorBody> api(ApiException e) { return body(e.getStatus(), e.getCode(), e.getMessage(), e.getErrors()); }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorBody> invalid(MethodArgumentNotValidException e) {
        Map<String, String> errs = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> errs.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errs);
    }

    /* ------------------------------------------------------------------------------------------------------------------
     * Các lỗi dưới đây sinh ra từ thư viện (Jackson, Spring) và mang thông điệp kỹ thuật tiếng Anh, có tên lớp Java
     * (vd. "Cannot deserialize value of type `java.lang.String`...", "No enum constant vn.floodconnect..."). Không trả
     * nguyên văn cho người dùng: trả câu tiếng Việt, nêu tên trường/tham số nếu biết; chi tiết gốc chỉ ghi vào log.
     * ------------------------------------------------------------------------------------------------------------------ */

    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorBody> constraint(ConstraintViolationException e) {
        // getMessage() có dạng "tenHam.thamSo: thông báo": chỉ giữ phần thông báo
        String msg = e.getConstraintViolations().stream().map(v -> v.getMessage()).distinct().reduce((a, b) -> a + " " + b)
                .orElse("Dữ liệu không hợp lệ");
        return body(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", msg, null);
    }

    /** JSON hỏng, sai kiểu dữ liệu, giá trị không thuộc danh sách cho phép (enum). */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
        log.warn("Không đọc được nội dung yêu cầu: {}", e.getMostSpecificCause().getMessage());
        String msg = "Dữ liệu gửi lên không đúng định dạng.";
        Map<String, String> errors = null;
        if (e.getCause() instanceof MismatchedInputException m && !m.getPath().isEmpty()) {
            String field = fieldOf(m);
            if (field != null) {
                msg = m instanceof InvalidFormatException ? "Giá trị của trường '" + field + "' không hợp lệ."
                        : "Trường '" + field + "' có kiểu dữ liệu không hợp lệ.";
                errors = Map.of(field, msg);
            }
        }
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg, errors);
    }

    /** Tên trường theo đường dẫn JSON, vd. "account.email" (bỏ chỉ số phần tử mảng). */
    private static String fieldOf(MismatchedInputException m) {
        String path = m.getPath().stream().map(r -> r.getFieldName()).filter(n -> n != null).reduce((a, b) -> a + "." + b).orElse(null);
        return path == null || path.isBlank() ? null : path;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ResponseEntity<ErrorBody> missingParam(MissingServletRequestParameterException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Thiếu tham số '" + e.getParameterName() + "'.", null);
    }

    /** Thiếu phần bắt buộc của yêu cầu multipart (vd. phần "data" khi đăng bài/đăng ký tổ chức). Trước đây rơi xuống lỗi 500. */
    @ExceptionHandler(MissingServletRequestPartException.class)
    ResponseEntity<ErrorBody> missingPart(MissingServletRequestPartException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Thiếu phần '" + e.getRequestPartName() + "' trong yêu cầu.", null);
    }

    /** Gửi sai kiểu nội dung (vd. JSON tới API chỉ nhận multipart). Trước đây rơi xuống lỗi 500. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErrorBody> mediaType(HttpMediaTypeNotSupportedException e) {
        return body(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Định dạng nội dung gửi lên không được hỗ trợ.", null);
    }

    /** Vd. giá trị bộ lọc không thuộc danh sách (IncidentStatus.valueOf("ABC")): thông điệp gốc có tên lớp Java. */
    @ExceptionHandler(IllegalArgumentException.class)
    ResponseEntity<ErrorBody> illegalArgument(IllegalArgumentException e) {
        log.warn("Tham số không hợp lệ: {}", e.getMessage());
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Yêu cầu không hợp lệ: có tham số mang giá trị không được hỗ trợ.", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooBig(MaxUploadSizeExceededException e) {
        return body(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE", "Tệp vượt quá dung lượng cho phép (tối đa 5 MB/tệp)", null);
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ErrorBody> denied(AccessDeniedException e) {
        return body(HttpStatus.FORBIDDEN, "FORBIDDEN", "Bạn không có quyền thực hiện thao tác này", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> method(HttpRequestMethodNotSupportedException e) {
        return body(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Phương thức " + e.getMethod() + " không được hỗ trợ cho đường dẫn này.", null);
    }

    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<ErrorBody> noResource(Exception e) {
        return body(HttpStatus.NOT_FOUND, "NOT_FOUND", "Không tìm thấy tài nguyên yêu cầu", null);
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    ResponseEntity<ErrorBody> typeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException e) {
        return body(HttpStatus.BAD_REQUEST, "BAD_REQUEST", "Giá trị tham số '" + e.getName() + "' không hợp lệ", null);
    }

    /** Vi phạm ràng buộc DB (trùng khóa duy nhất, CHECK...), thường do hai yêu cầu trùng nhau gửi cùng lúc. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ErrorBody> integrity(DataIntegrityViolationException e) {
        log.warn("Vi phạm ràng buộc dữ liệu: {}", e.getMostSpecificCause().getMessage());
        return body(HttpStatus.CONFLICT, "DATA_CONFLICT", "Dữ liệu bị trùng hoặc vừa thay đổi, vui lòng tải lại và thử lại.", null);
    }

    /** Hai thao tác cùng sửa một bản ghi có @Version (vd. chiến dịch đang được cộng tiền ủng hộ): thao tác sau phải tải lại rồi làm lại. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ErrorBody> concurrent(OptimisticLockingFailureException e) {
        return body(HttpStatus.CONFLICT, "DATA_CHANGED", "Dữ liệu vừa được cập nhật bởi thao tác khác, vui lòng tải lại và thử lại.", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> other(Exception e) {
        log.error("Lỗi không mong muốn", e);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Lỗi hệ thống, vui lòng thử lại sau", null);
    }
}
