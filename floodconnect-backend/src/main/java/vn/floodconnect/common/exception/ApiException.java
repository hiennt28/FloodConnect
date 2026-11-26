package vn.floodconnect.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.util.Map;

@Getter
public class ApiException extends RuntimeException {
    private final HttpStatus status;
    private final String code;
    private final Map<String, String> errors;

    public ApiException(HttpStatus status, String code, String message, Map<String, String> errors) {
        super(message);
        this.status = status;
        this.code = code;
        this.errors = errors;
    }

    public static ApiException bad(String msg) { return new ApiException(HttpStatus.BAD_REQUEST, "BAD_REQUEST", msg, null); }
    public static ApiException bad(String code, String msg) { return new ApiException(HttpStatus.BAD_REQUEST, code, msg, null); }
    public static ApiException validation(Map<String, String> errors) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Dữ liệu không hợp lệ", errors);
    }
    public static ApiException notFound(String msg) { return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", msg, null); }
    public static ApiException forbidden(String msg) { return new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", msg, null); }
    public static ApiException forbidden(String code, String msg) { return new ApiException(HttpStatus.FORBIDDEN, code, msg, null); }
    public static ApiException conflict(String msg) { return new ApiException(HttpStatus.CONFLICT, "CONFLICT", msg, null); }
    public static ApiException conflict(String code, String msg) { return new ApiException(HttpStatus.CONFLICT, code, msg, null); }
    public static ApiException unauthorized(String msg) { return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", msg, null); }
    public static ApiException unauthorized() { return unauthorized("Chưa đăng nhập hoặc phiên đã hết hạn"); }
}
