package com.prolean.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prolean.common.web.RequestIds;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class ProblemDetailsTest {

    private final ProblemDetailsFactory factory = new ProblemDetailsFactory();

    @Test
    void factoryBuildsEnvelopeWithDefaultsAndRequestId() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/projects");
        request.setAttribute(RequestIds.ATTRIBUTE, "req-1");

        ProblemDetails problem = factory.create(ErrorCode.PROJECT_NOT_FOUND, null, null, request);

        assertThat(problem.type()).isEqualTo("https://prolean.local/errors/project-not-found");
        assertThat(problem.title()).isEqualTo("Not Found");
        assertThat(problem.status()).isEqualTo(404);
        assertThat(problem.detail()).isEqualTo("Project does not exist.");
        assertThat(problem.instance()).isEqualTo("/api/v1/projects");
        assertThat(problem.code()).isEqualTo("PROJECT_NOT_FOUND");
        assertThat(problem.requestId()).isEqualTo("req-1");
        assertThat(problem.timestamp()).isNotNull();
        assertThat(problem.errors()).isNull();
    }

    @Test
    void factoryKeepsCustomDetailAndErrors() {
        ProblemDetails problem = factory.create(ErrorCode.VALIDATION_ERROR, " custom ",
                List.of(new FieldErrorItem("name", "required")), null);

        assertThat(problem.detail()).isEqualTo(" custom ");
        assertThat(problem.errors()).hasSize(1);
        assertThat(problem.instance()).isNull();
        assertThat(problem.requestId()).isNull();
    }

    private static ObjectMapper mapper() {
        return new ObjectMapper().findAndRegisterModules();
    }

    @Test
    void writerSerializesProblemJson() throws IOException {
        ProblemDetailsWriter writer = new ProblemDetailsWriter(mapper());
        MockHttpServletResponse response = new MockHttpServletResponse();

        writer.write(response, factory.create(ErrorCode.FORBIDDEN, null, List.of(),
                new MockHttpServletRequest("GET", "/x")));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("\"code\":\"FORBIDDEN\"");
    }

    @Test
    void writerIgnoresCommittedResponse() throws IOException {
        ProblemDetailsWriter writer = new ProblemDetailsWriter(new ObjectMapper());
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(true);

        writer.write(response, factory.create(ErrorCode.FORBIDDEN, null, List.of(), null));

        verify(response, never()).getOutputStream();
    }

    @Test
    void writerToleratesNullOutputStream() throws IOException {
        ProblemDetailsWriter writer = new ProblemDetailsWriter(mapper());
        HttpServletResponse response = mock(HttpServletResponse.class);
        when(response.isCommitted()).thenReturn(false);
        when(response.getOutputStream()).thenReturn(new jakarta.servlet.ServletOutputStream() {
            private final ByteArrayOutputStream delegate = new ByteArrayOutputStream();

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(jakarta.servlet.WriteListener writeListener) {
                // no-op
            }

            @Override
            public void write(int b) {
                delegate.write(b);
            }
        });

        writer.write(response, factory.create(ErrorCode.FORBIDDEN, null, List.of(), null));

        verify(response).flushBuffer();
    }
}
