package com.prolean.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** Translates exceptions into the shared RFC 7807 response shape. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON = MediaType.APPLICATION_PROBLEM_JSON;

    private final ProblemDetailsFactory factory;

    public GlobalExceptionHandler(ProblemDetailsFactory factory) {
        this.factory = factory;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetails> handleApi(ApiException exception, HttpServletRequest request) {
        return response(factory.create(exception.code(), exception.detail(), exception.errors(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetails> handleValidation(MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<FieldErrorItem> errors = exception.getBindingResult().getFieldErrors().stream()
                .map(this::fieldError)
                .toList();
        return response(factory.create(ErrorCode.VALIDATION_ERROR, null, errors, request));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetails> handleConstraint(ConstraintViolationException exception,
            HttpServletRequest request) {
        List<FieldErrorItem> errors = exception.getConstraintViolations().stream()
                .map(this::violation)
                .toList();
        return response(factory.create(ErrorCode.VALIDATION_ERROR, null, errors, request));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetails> handleUnreadable(HttpMessageNotReadableException exception,
            HttpServletRequest request) {
        return response(factory.create(ErrorCode.VALIDATION_ERROR, "Malformed JSON request.", List.of(), request));
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ProblemDetails> handleParameter(Exception exception, HttpServletRequest request) {
        return response(factory.create(ErrorCode.VALIDATION_ERROR, exception.getMessage(), List.of(), request));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetails> handleMissingPath(NoResourceFoundException exception,
            HttpServletRequest request) {
        return response(factory.create(ErrorCode.NOT_FOUND, "No endpoint matches the request path.", List.of(), request));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetails> handleConflict(DataIntegrityViolationException exception,
            HttpServletRequest request) {
        log.warn("Database constraint violation on {}: {}", request.getRequestURI(), exception.getMostSpecificCause().getMessage());
        return response(factory.create(ErrorCode.DATA_CONFLICT, null, List.of(), request));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetails> handleDenied(AccessDeniedException exception, HttpServletRequest request) {
        return response(factory.create(ErrorCode.FORBIDDEN, null, List.of(), request));
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetails> handleAuthentication(AuthenticationException exception,
            HttpServletRequest request) {
        return response(factory.create(ErrorCode.UNAUTHORIZED, null, List.of(), request));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetails> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled error on {}", request.getRequestURI(), exception);
        return response(factory.create(ErrorCode.INTERNAL_ERROR, null, List.of(), request));
    }

    private FieldErrorItem fieldError(FieldError error) {
        return new FieldErrorItem(error.getField(), error.getDefaultMessage());
    }

    private FieldErrorItem violation(ConstraintViolation<?> violation) {
        return new FieldErrorItem(violation.getPropertyPath().toString(), violation.getMessage());
    }

    private ResponseEntity<ProblemDetails> response(ProblemDetails problem) {
        return ResponseEntity.status(problem.status()).contentType(PROBLEM_JSON).body(problem);
    }
}
