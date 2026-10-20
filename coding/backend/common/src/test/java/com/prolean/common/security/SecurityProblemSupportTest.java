package com.prolean.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prolean.common.error.ProblemDetailsFactory;
import com.prolean.common.error.ProblemDetailsWriter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

class SecurityProblemSupportTest {

    private final SecurityProblemSupport support = new SecurityProblemSupport(
            new ProblemDetailsFactory(),
            new ProblemDetailsWriter(new ObjectMapper().findAndRegisterModules()));

    @Test
    void writesUnauthorizedProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        support.commence(new MockHttpServletRequest("GET", "/api/v1/me"),
                response, new BadCredentialsException("bad"));

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    @Test
    void writesForbiddenProblem() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        support.handle(new MockHttpServletRequest("GET", "/api/v1/me"),
                response, new AccessDeniedException("no"));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("FORBIDDEN");
    }
}
