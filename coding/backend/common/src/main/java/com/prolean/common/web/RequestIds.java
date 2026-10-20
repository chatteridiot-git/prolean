package com.prolean.common.web;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Access to the request correlation id for the current request.
 *
 * <p>The {@link RequestIdFilter} stores the id as a request attribute and in the SLF4J MDC; the
 * attribute survives after the MDC is cleared.
 */
public final class RequestIds {

    /** SLF4J MDC key; logging patterns include {@code %X{requestId}}. */
    public static final String MDC_KEY = "requestId";

    /** Servlet request attribute holding the resolved id. */
    public static final String ATTRIBUTE = RequestIds.class.getName() + ".requestId";

    private RequestIds() {
    }

    /**
     * Resolves the request id for the given request.
     *
     * @return the request id, or {@code null} when the filter has not run (for example in a scheduled job)
     */
    public static String current(HttpServletRequest request) {
        if (request == null) {
            return org.slf4j.MDC.get(MDC_KEY);
        }
        Object attribute = request.getAttribute(ATTRIBUTE);
        if (attribute instanceof String value && !value.isBlank()) {
            return value;
        }
        return org.slf4j.MDC.get(MDC_KEY);
    }
}
