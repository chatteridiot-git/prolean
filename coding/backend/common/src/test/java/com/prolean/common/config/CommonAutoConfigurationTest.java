package com.prolean.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

class CommonAutoConfigurationTest {

    @Test
    void exposesCorrelationAndHolidayCoverageHeadersToTheSpa() {
        CorsConfiguration configuration = new CommonAutoConfiguration()
                .corsConfigurationSource("http://localhost:5173", false)
                .getCorsConfiguration(new MockHttpServletRequest("GET", "/api/v1/holidays"));

        assertThat(configuration).isNotNull();
        assertThat(configuration.getExposedHeaders())
                .contains("X-Request-Id", "Idempotent-Replay",
                        "X-Holiday-Coverage-Start", "X-Holiday-Coverage-End");
    }
}
