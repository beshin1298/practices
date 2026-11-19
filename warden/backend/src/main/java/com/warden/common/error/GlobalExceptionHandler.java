package com.warden.common.error;

import java.util.LinkedHashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every exception thrown inside Spring MVC into an RFC 9457 ProblemDetail with a "code".
 * Errors raised before MVC (no token, wrong role at URL level) are written by
 * {@link com.warden.security.handler.ProblemDetailsSecurityHandler} in the same format.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AppException.class)
    ResponseEntity<ProblemDetail> handleApp(AppException ex, HttpServletRequest request) {
        return respond(ex.code(), ex.getMessage(), request);
    }

    /**
     * Thrown by @PreAuthorize when the role is wrong. Without this handler the catch-all below
     * would turn a 403 into a 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return respond(ErrorCode.ACCESS_DENIED, null, request);
    }

    @ExceptionHandler(AuthenticationException.class)
    ResponseEntity<ProblemDetail> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return respond(ErrorCode.UNAUTHENTICATED, null, request);
    }

    /** Last line of defence for unique constraints hit by a race between check and insert. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return respond(ErrorCode.DATA_CONFLICT, null, request);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ErrorCode.INTERNAL_ERROR, null, request);
    }

    /** Bean Validation failures on @Valid @RequestBody: 400 with a field -> message map. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors()
                .forEach(error -> errors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));

        ProblemDetail problem = Problems.of(ErrorCode.VALIDATION_FAILED, null, path(request));
        problem.setProperty("errors", errors);
        return ResponseEntity.status(ErrorCode.VALIDATION_FAILED.status()).body(problem);
    }

    /** Standard Spring MVC errors (malformed JSON, 404, 405...) get a "code" too. */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response != null && response.getBody() instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey("code"))) {
            problem.setProperty("code", switch (statusCode.value()) {
                case 400 -> ErrorCode.BAD_REQUEST.name();
                case 404 -> ErrorCode.NOT_FOUND.name();
                default -> statusCode.is4xxClientError() ? "HTTP_" + statusCode.value() : ErrorCode.INTERNAL_ERROR.name();
            });
            if (statusCode.value() == 404) {
                problem.setDetail(ErrorCode.NOT_FOUND.defaultMessage()); // do not echo internal resource names
            }
        }
        return response;
    }

    private static ResponseEntity<ProblemDetail> respond(ErrorCode code, String detail, HttpServletRequest request) {
        return ResponseEntity.status(code.status()).body(Problems.of(code, detail, request.getRequestURI()));
    }

    private static String path(WebRequest request) {
        return request instanceof ServletWebRequest servlet ? servlet.getRequest().getRequestURI() : null;
    }
}
