package com.prolean.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;

/**
 * RFC 7807 / RFC 9457 Problem Details body used by every service.
 *
 * <p>Delivered with {@code Content-Type: application/problem+json}. {@code code} is the stable value
 * the frontend branches on; {@code requestId} matches the {@code X-Request-Id} response header.
 *
 * @param type RFC 9457 problem type URI
 * @param title short, stable summary
 * @param status HTTP status code
 * @param detail human readable, safe-to-display explanation
 * @param instance request path that produced the problem
 * @param code stable ProLean error code
 * @param requestId request correlation id
 * @param timestamp instant the problem was produced, in UTC
 * @param errors field-level validation failures, omitted when there are none
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetails(
        String type,
        String title,
        int status,
        String detail,
        String instance,
        String code,
        String requestId,
        Instant timestamp,
        List<FieldErrorItem> errors) {
}
