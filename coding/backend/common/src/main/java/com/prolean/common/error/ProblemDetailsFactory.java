package com.prolean.common.error;

import com.prolean.common.web.RequestIds;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;

/** Builds RFC 7807 problem bodies with a consistent shape and request correlation id. */
@Component
public class ProblemDetailsFactory {

    public ProblemDetails create(ErrorCode code, String detail, List<FieldErrorItem> errors,
            HttpServletRequest request) {
        String safeDetail = detail == null || detail.isBlank() ? code.defaultDetail() : detail;
        List<FieldErrorItem> safeErrors = errors == null || errors.isEmpty() ? null : List.copyOf(errors);
        return new ProblemDetails(
                code.type(),
                code.title(),
                code.status().value(),
                safeDetail,
                request == null ? null : request.getRequestURI(),
                code.name(),
                request == null ? null : RequestIds.current(request),
                Instant.now(),
                safeErrors);
    }
}
