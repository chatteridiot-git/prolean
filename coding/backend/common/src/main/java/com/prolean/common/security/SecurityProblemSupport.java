package com.prolean.common.security;

import com.prolean.common.error.ErrorCode;
import com.prolean.common.error.ProblemDetailsFactory;
import com.prolean.common.error.ProblemDetailsWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/** Writes RFC 7807 bodies for Spring Security authentication and authorization failures. */
public class SecurityProblemSupport implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemDetailsFactory factory;
    private final ProblemDetailsWriter writer;

    public SecurityProblemSupport(ProblemDetailsFactory factory, ProblemDetailsWriter writer) {
        this.factory = factory;
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {
        writer.write(response, factory.create(ErrorCode.UNAUTHORIZED, null, List.of(), request));
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException {
        writer.write(response, factory.create(ErrorCode.FORBIDDEN, null, List.of(), request));
    }
}
