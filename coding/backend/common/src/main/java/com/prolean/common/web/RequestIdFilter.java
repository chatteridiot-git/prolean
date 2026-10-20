package com.prolean.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the request correlation id, exposes it to the logging MDC, and returns it to the caller.
 *
 * <p>An incoming {@code X-Request-Id} is honoured only when it is a safe token (1-128 characters of
 * {@code [A-Za-z0-9._:-]}); anything else is replaced with a generated UUID so client input cannot
 * forge log lines or inject response headers. The id is set on the response before the chain runs so
 * it is present even on filter-level failures such as authentication errors.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._:-]{1,128}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requestId = resolve(request.getHeader(RequestHeaders.REQUEST_ID));
        request.setAttribute(RequestIds.ATTRIBUTE, requestId);
        response.setHeader(RequestHeaders.REQUEST_ID, requestId);
        MDC.put(RequestIds.MDC_KEY, requestId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(RequestIds.MDC_KEY);
        }
    }

    /**
     * Resolves a client supplied request id.
     *
     * @param candidate raw header value, possibly {@code null}
     * @return the candidate when safe, otherwise a generated UUID
     */
    public static String resolve(String candidate) {
        if (candidate != null && SAFE_REQUEST_ID.matcher(candidate).matches()) {
            return candidate;
        }
        return UUID.randomUUID().toString();
    }
}
