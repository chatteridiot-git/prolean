package com.prolean.common.error;

import java.util.Locale;
import org.springframework.http.HttpStatus;

/**
 * Stable error code catalogue shared by every ProLean service.
 *
 * <p>The enum constant name is the value returned in the Problem Details {@code code} field; the
 * frontend branches on it. Codes are additive only: renaming or removing one is a breaking API
 * change.
 *
 * <p>{@link #NOT_FOUND} is the transport-level fallback used when no handler exists for a request
 * path. Business resources use their own specific codes (for example {@code PROJECT_NOT_FOUND}),
 * which map to the same HTTP status.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "One or more fields are invalid."),
    INVALID_DATE_RANGE(HttpStatus.BAD_REQUEST, "endDate must be on or after startDate."),
    NO_WORKING_DAYS(HttpStatus.BAD_REQUEST, "The date range contains no working days."),

    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Username or password is incorrect."),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "The account is deactivated."),
    AUTH_ACCOUNT_LOCKED(HttpStatus.LOCKED, "The account is locked after repeated failed logins."),
    AUTH_REFRESH_INVALID(HttpStatus.UNAUTHORIZED, "The refresh token is missing, expired, revoked or unknown."),
    AUTH_REFRESH_REUSED(HttpStatus.UNAUTHORIZED, "A revoked refresh token was reused; all sessions were revoked."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication is required."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You are not allowed to perform this action."),
    INTERNAL_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "A valid internal API key is required."),
    INTERNAL_FORBIDDEN(HttpStatus.FORBIDDEN, "The internal caller is not allowed to use this endpoint."),

    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User does not exist."),
    USERNAME_TAKEN(HttpStatus.CONFLICT, "Username already exists."),
    EMAIL_TAKEN(HttpStatus.CONFLICT, "Email already exists."),
    LAST_ADMIN(HttpStatus.CONFLICT, "Operation would remove the last active ADMIN."),
    SELF_DEACTIVATION(HttpStatus.CONFLICT, "An admin cannot deactivate their own account."),
    ROLE_NOT_FOUND(HttpStatus.NOT_FOUND, "Requested role does not exist."),

    PROJECT_NOT_FOUND(HttpStatus.NOT_FOUND, "Project does not exist."),
    PROJECT_CODE_TAKEN(HttpStatus.CONFLICT, "Project code already exists."),
    PROJECT_ARCHIVED(HttpStatus.CONFLICT, "Project is archived and read-only."),
    MEMBER_NOT_FOUND(HttpStatus.NOT_FOUND, "User is not a project member."),

    TASK_NOT_FOUND(HttpStatus.NOT_FOUND, "Task does not exist."),
    PIC_NOT_MEMBER(HttpStatus.BAD_REQUEST, "PIC is not an active member of the project."),
    INVALID_COMPLETION_FOR_CLOSED(HttpStatus.BAD_REQUEST, "A closed task or timeline must be at 100 percent."),
    TASK_CLOSED(HttpStatus.CONFLICT, "Progress cannot be edited while the task is CLOSED; reopen it first."),

    TIMELINE_EXISTS(HttpStatus.CONFLICT, "Project already has a timeline."),
    TIMELINE_NOT_FOUND(HttpStatus.NOT_FOUND, "Project has no timeline."),
    DRAFT_EXISTS(HttpStatus.CONFLICT, "A draft already exists for this timeline."),
    DRAFT_NOT_FOUND(HttpStatus.NOT_FOUND, "No editable draft exists."),
    NO_CURRENT_VERSION(HttpStatus.CONFLICT, "A revision was requested but no published version exists."),
    CURRENT_VERSION_CHANGED(HttpStatus.CONFLICT, "The current published version changed; reload before publishing."),
    DRAFT_MODIFIED(HttpStatus.CONFLICT, "The draft changed since it was loaded; reload before saving or publishing."),
    TIMELINE_CLOSED(HttpStatus.CONFLICT, "Progress cannot be edited while the current timeline is CLOSED."),
    INVALID_TIMELINE_STATE(HttpStatus.CONFLICT, "The operation is not allowed in the current timeline state."),
    INVALID_STATUS_TRANSITION(HttpStatus.CONFLICT, "The requested status transition is not allowed."),

    HOLIDAY_DATE_TAKEN(HttpStatus.CONFLICT, "A holiday already exists on that date."),
    HOLIDAY_COVERAGE_REQUIRED(HttpStatus.CONFLICT, "Timeline dates fall outside the covered holiday horizon."),
    HOLIDAY_NOT_FOUND(HttpStatus.NOT_FOUND, "Holiday does not exist."),

    DATA_CONFLICT(HttpStatus.CONFLICT, "The request conflicts with existing data."),
    INTERNAL_DEPENDENCY_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "A required internal service is unavailable."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error.");

    private static final String TYPE_PREFIX = "https://prolean.local/errors/";

    private final HttpStatus status;
    private final String defaultDetail;

    ErrorCode(HttpStatus status, String defaultDetail) {
        this.status = status;
        this.defaultDetail = defaultDetail;
    }

    /** HTTP status recommended for this code. Services may override it only with a documented reason. */
    public HttpStatus status() {
        return status;
    }

    /** Human readable, safe-to-display default detail. */
    public String defaultDetail() {
        return defaultDetail;
    }

    /** Short, stable title derived from the HTTP reason phrase. */
    public String title() {
        return status.getReasonPhrase();
    }

    /** RFC 9457 type URI, for example {@code https://prolean.local/errors/validation-error}. */
    public String type() {
        return TYPE_PREFIX + name().toLowerCase(Locale.ROOT).replace('_', '-');
    }
}
