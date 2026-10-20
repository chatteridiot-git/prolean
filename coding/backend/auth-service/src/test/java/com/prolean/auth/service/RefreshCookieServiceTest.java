package com.prolean.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.prolean.auth.config.AuthProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class RefreshCookieServiceTest {

    @Test
    void writesHardenedRefreshCookie() {
        AuthProperties properties = new AuthProperties();
        properties.setCookieSecure(true);
        properties.setRefreshTokenTtlDays(7);
        RefreshCookieService service = new RefreshCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.set(response, "raw-token");

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("refresh_token=raw-token")
                .contains("HttpOnly")
                .contains("Secure")
                .contains("SameSite=Lax")
                .contains("Path=/api/v1/auth")
                .contains("Max-Age=604800");
    }

    @Test
    void clearsCookie() {
        AuthProperties properties = new AuthProperties();
        RefreshCookieService service = new RefreshCookieService(properties);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.clear(response);

        String header = response.getHeader("Set-Cookie");
        assertThat(header).contains("refresh_token=")
                .contains("Max-Age=0")
                .doesNotContain("Secure");
    }
}
