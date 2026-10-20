package com.prolean.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prolean.common.error.ProblemDetailsFactory;
import com.prolean.common.error.ProblemDetailsWriter;
import com.prolean.common.web.RequestHeaders;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

class InternalKeyFilterTest {

    private final InternalKeyProperties properties = new InternalKeyProperties();
    private final InternalKeyFilter filter = new InternalKeyFilter(properties,
            new ProblemDetailsFactory(),
            new ProblemDetailsWriter(new ObjectMapper().findAndRegisterModules()));

    InternalKeyFilterTest() {
        InternalKeyProperties.Caller caller = new InternalKeyProperties.Caller();
        caller.setSecret("secret");
        caller.setScopes(List.of("GET /internal/users/*", "POST /internal/users/bootstrap"));
        properties.setCallers(Map.of("user-service", caller));
    }

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private MockHttpServletResponse invoke(String method, String uri, String keyId, String secret)
            throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
        if (keyId != null) {
            request.addHeader(RequestHeaders.INTERNAL_KEY_ID, keyId);
        }
        if (secret != null) {
            request.addHeader(RequestHeaders.INTERNAL_API_KEY, secret);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void requiresBothHeaders() throws Exception {
        assertThat(invoke("GET", "/internal/users/1", null, null).getStatus()).isEqualTo(401);
        assertThat(invoke("GET", "/internal/users/1", "user-service", null).getStatus()).isEqualTo(401);
        assertThat(invoke("GET", "/internal/users/1", null, "secret").getStatus()).isEqualTo(401);
        assertThat(invoke("GET", "/internal/users/1", "unknown", "secret").getStatus()).isEqualTo(401);
        assertThat(invoke("GET", "/internal/users/1", "user-service", "wrong").getStatus()).isEqualTo(401);
    }

    @Test
    void rejectsCallerOutsideScope() throws Exception {
        MockHttpServletResponse response = invoke("DELETE", "/internal/users/1", "user-service", "secret");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("INTERNAL_FORBIDDEN");
    }

    @Test
    void authenticatesScopedCaller() throws Exception {
        MockHttpServletResponse response = invoke("GET", "/internal/users/1", "user-service", "secret");
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication().getAuthorities())
                .extracting("authority").containsExactly("ROLE_INTERNAL");
    }

    @Test
    void ignoresPublicPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test
    void callerWithNullSecretCannotAuthenticate() throws Exception {
        InternalKeyProperties.Caller caller = new InternalKeyProperties.Caller();
        caller.setScopes(List.of("GET /internal/users/*"));
        properties.setCallers(Map.of("bad", caller));

        assertThat(invoke("GET", "/internal/users/1", "bad", "anything").getStatus()).isEqualTo(401);
    }
}
