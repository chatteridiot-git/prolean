package com.prolean.common.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.MockHttpInputMessage;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.http.HttpMethod;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler(new ProblemDetailsFactory());
    private final MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/test");

    @Test
    void mapsBusinessException() {
        ResponseEntity<ProblemDetails> response = handler.handleApi(
                new ApiException(ErrorCode.PROJECT_ARCHIVED), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().code()).isEqualTo("PROJECT_ARCHIVED");
        assertThat(response.getHeaders().getContentType().toString())
                .isEqualTo("application/problem+json");
    }

    @Test
    void mapsBeanValidationErrors() {
        BeanPropertyBindingResult result = new BeanPropertyBindingResult(new Object(), "request");
        result.addError(new FieldError("request", "name", "must not be blank"));
        MethodArgumentNotValidException exception = mock(MethodArgumentNotValidException.class);
        when(exception.getBindingResult()).thenReturn(result);

        ResponseEntity<ProblemDetails> response = handler.handleValidation(exception, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().getFirst().field()).isEqualTo("name");
    }

    @Test
    void mapsConstraintViolations() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("size");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must be valid");

        ResponseEntity<ProblemDetails> response = handler.handleConstraint(
                new ConstraintViolationException(Set.of(violation)), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().errors()).hasSize(1);
        assertThat(response.getBody().errors().getFirst().field()).isEqualTo("size");
    }

    @Test
    void mapsMalformedJsonAndParameterErrors() {
        var malformed = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad", new MockHttpInputMessage(new byte[0])), request);
        assertThat(malformed.getBody().detail()).isEqualTo("Malformed JSON request.");

        var missing = handler.handleParameter(
                new MissingServletRequestParameterException("page", "int"), request);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        var mismatch = handler.handleParameter(
                new MethodArgumentTypeMismatchException("x", Integer.class, "x", null, null), request);
        assertThat(mismatch.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void mapsMissingPathAndDatabaseConflicts() {
        var missing = handler.handleMissingPath(
                new NoResourceFoundException(HttpMethod.GET, "/missing"), request);
        assertThat(missing.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        var conflict = handler.handleConflict(
                new DataIntegrityViolationException("dup", new RuntimeException("detail")), request);
        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().code()).isEqualTo("DATA_CONFLICT");
    }

    @Test
    void mapsSecurityAndUnexpectedErrors() {
        assertThat(handler.handleDenied(new AccessDeniedException("no"), request).getStatusCode())
                .isEqualTo(HttpStatus.FORBIDDEN);
        assertThat(handler.handleUnexpected(new IllegalStateException("boom"), request).getStatusCode())
                .isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
