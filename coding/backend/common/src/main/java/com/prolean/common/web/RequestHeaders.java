package com.prolean.common.web;

/** Header and header-value constants shared by every ProLean service. */
public final class RequestHeaders {

    /** Client supplied request correlation id. Echoed back on every response. */
    public static final String REQUEST_ID = "X-Request-Id";

    /** Caller identity used by internal service APIs during key rotation. */
    public static final String INTERNAL_KEY_ID = "X-Internal-Key-Id";

    /** Per-caller internal secret. Never present on browser traffic. */
    public static final String INTERNAL_API_KEY = "X-Internal-Api-Key";

    /** Set to {@code true} when an idempotent replay returned the original resource. */
    public static final String IDEMPOTENT_REPLAY = "Idempotent-Replay";

    public static final String AUTHORIZATION = "Authorization";
    public static final String BEARER_PREFIX = "Bearer ";

    private RequestHeaders() {
    }
}
