package com.prolean.common.security;

import com.prolean.common.error.ErrorCode;
import com.prolean.common.error.ProblemDetails;
import com.prolean.common.error.ProblemDetailsFactory;
import com.prolean.common.error.ProblemDetailsWriter;
import com.prolean.common.web.RequestHeaders;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests to {@code /internal/**} with a per-caller key and enforces endpoint scopes.
 *
 * <p>This filter is installed inside the dedicated internal security chain, so a browser JWT can
 * never authenticate an internal call. Missing or invalid credentials produce
 * {@code INTERNAL_UNAUTHORIZED}; a valid key outside its scope produces {@code INTERNAL_FORBIDDEN}.
 */
public class InternalKeyFilter extends OncePerRequestFilter {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final InternalKeyProperties properties;
    private final ProblemDetailsFactory problemFactory;
    private final ProblemDetailsWriter problemWriter;

    public InternalKeyFilter(InternalKeyProperties properties, ProblemDetailsFactory problemFactory,
            ProblemDetailsWriter problemWriter) {
        this.properties = properties;
        this.problemFactory = problemFactory;
        this.problemWriter = problemWriter;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !(path.equals("/internal") || path.startsWith("/internal/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String keyId = request.getHeader(RequestHeaders.INTERNAL_KEY_ID);
        String secret = request.getHeader(RequestHeaders.INTERNAL_API_KEY);

        if (keyId == null || keyId.isBlank() || secret == null || secret.isBlank()) {
            write(response, request, ErrorCode.INTERNAL_UNAUTHORIZED);
            return;
        }

        InternalKeyProperties.Caller caller = properties.getCallers().get(keyId);
        if (caller == null || caller.getSecret() == null
                || !MessageDigest.isEqual(caller.getSecret().getBytes(StandardCharsets.UTF_8),
                        secret.getBytes(StandardCharsets.UTF_8))) {
            write(response, request, ErrorCode.INTERNAL_UNAUTHORIZED);
            return;
        }

        String requestedScope = request.getMethod() + " " + request.getRequestURI();
        boolean allowed = caller.getScopes().stream().anyMatch(scope -> MATCHER.match(scope, requestedScope));
        if (!allowed) {
            write(response, request, ErrorCode.INTERNAL_FORBIDDEN);
            return;
        }

        UsernamePasswordAuthenticationToken authentication = UsernamePasswordAuthenticationToken.authenticated(
                new InternalCaller(keyId), null, List.of(new SimpleGrantedAuthority("ROLE_INTERNAL")));
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);
        filterChain.doFilter(request, response);
    }

    private void write(HttpServletResponse response, HttpServletRequest request, ErrorCode code) throws IOException {
        ProblemDetails problem = problemFactory.create(code, null, List.of(), request);
        problemWriter.write(response, problem);
    }
}
