package com.warden.security.handler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.warden.common.error.ErrorCode;
import com.warden.common.error.Problems;
import com.warden.security.filter.JwtAuthenticationFilter;

/**
 * Writes 401/403 for requests stopped by the security filter chain, before Spring MVC
 * (so @RestControllerAdvice cannot handle them). Same ProblemDetail format as the rest of the API.
 */
@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ObjectMapper objectMapper;

    public ProblemDetailsSecurityHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** No valid authentication: 401 with the reason recorded by {@link JwtAuthenticationFilter}. */
    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex)
            throws IOException {
        ErrorCode code = request.getAttribute(JwtAuthenticationFilter.AUTH_ERROR_ATTRIBUTE) instanceof ErrorCode reason
                ? reason
                : ErrorCode.UNAUTHENTICATED;
        // RFC 6750: tell the client which scheme is expected, and whether the token itself was the problem.
        response.setHeader("WWW-Authenticate", code == ErrorCode.UNAUTHENTICATED
                ? "Bearer realm=\"warden\""
                : "Bearer realm=\"warden\", error=\"invalid_token\"");
        write(request, response, code);
    }

    /** Authenticated but the role is not allowed: 403. */
    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex)
            throws IOException {
        write(request, response, ErrorCode.ACCESS_DENIED);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(), Problems.of(code, null, request.getRequestURI()));
    }
}
