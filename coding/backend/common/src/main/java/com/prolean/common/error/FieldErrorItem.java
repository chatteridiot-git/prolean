package com.prolean.common.error;

/**
 * One field-level validation failure inside a Problem Details response.
 *
 * @param field JSON path or field name of the offending input
 * @param message human readable, safe-to-display reason
 */
public record FieldErrorItem(String field, String message) {
}
