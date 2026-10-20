package com.prolean.common.error;

import java.util.List;
import org.springframework.http.HttpStatus;

/**
 * A business or protocol failure that maps directly onto a {@link ProblemDetails} response.
 *
 * <p>Services throw this instead of building HTTP responses themselves; the shared exception handler
 * translates it. The HTTP status always comes from the {@link ErrorCode} so a code cannot be returned
 * with two different statuses.
 */
public class ApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ErrorCode code;
    private final String detail;
    private final List<FieldErrorItem> errors;

    public ApiException(ErrorCode code) {
        this(code, code.defaultDetail(), List.of());
    }

    public ApiException(ErrorCode code, String detail) {
        this(code, detail, List.of());
    }

    public ApiException(ErrorCode code, String detail, List<FieldErrorItem> errors) {
        super(detail == null || detail.isBlank() ? code.defaultDetail() : detail);
        this.code = code;
        this.detail = detail == null || detail.isBlank() ? code.defaultDetail() : detail;
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public static ApiException of(ErrorCode code, String detail) {
        return new ApiException(code, detail);
    }

    public static ApiException validation(List<FieldErrorItem> errors) {
        return new ApiException(ErrorCode.VALIDATION_ERROR, ErrorCode.VALIDATION_ERROR.defaultDetail(), errors);
    }

    public static ApiException validation(String field, String message) {
        return validation(List.of(new FieldErrorItem(field, message)));
    }

    public ErrorCode code() {
        return code;
    }

    public HttpStatus status() {
        return code.status();
    }

    public String detail() {
        return detail;
    }

    public List<FieldErrorItem> errors() {
        return errors;
    }
}
