package com.prolean.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RequestIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void acceptsSafeRequestIdAndEchoesIt() throws Exception {
        RequestIdFilter filter = new RequestIdFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestHeaders.REQUEST_ID, "abc-123.valid:value");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertThat(response.getHeader(RequestHeaders.REQUEST_ID)).isEqualTo("abc-123.valid:value");
        assertThat(request.getAttribute(RequestIds.ATTRIBUTE)).isEqualTo("abc-123.valid:value");
        assertThat(RequestIds.current(request)).isEqualTo("abc-123.valid:value");
        assertThat(MDC.get(RequestIds.MDC_KEY)).isNull();
    }

    @Test
    void replacesUnsafeRequestIdWithUuid() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestHeaders.REQUEST_ID, "bad\nvalue");

        new RequestIdFilter().doFilter(request, response, new MockFilterChain());

        String id = response.getHeader(RequestHeaders.REQUEST_ID);
        assertThat(id).hasSize(36).doesNotContain("bad");
        assertThat(RequestIdFilter.resolve(null)).hasSize(36);
        assertThat(RequestIdFilter.resolve("x".repeat(129))).hasSize(36);
        assertThat(RequestIdFilter.resolve("x".repeat(128))).hasSize(128);
    }

    @Test
    void requestIdsFallBackToMdcWhenAttributeOrRequestIsMissing() {
        MDC.put(RequestIds.MDC_KEY, "mdc-id");
        assertThat(RequestIds.current(null)).isEqualTo("mdc-id");
        assertThat(RequestIds.current(new MockHttpServletRequest())).isEqualTo("mdc-id");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIds.ATTRIBUTE, "attribute-id");
        assertThat(RequestIds.current(request)).isEqualTo("attribute-id");

        request.setAttribute(RequestIds.ATTRIBUTE, " ");
        assertThat(RequestIds.current(request)).isEqualTo("mdc-id");
    }
}
