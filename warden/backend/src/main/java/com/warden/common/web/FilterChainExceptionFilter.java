package com.warden.common.web;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Sends exceptions thrown inside the servlet filter chain (for example the database is down while
 * the JWT filter checks the session) to the same {@link com.warden.common.error.GlobalExceptionHandler}
 * that handles controller errors. @RestControllerAdvice only sees exceptions raised inside Spring MVC;
 * without this filter such errors reach Spring Boot's /error page, whose JSON has no "code".
 * Runs just before the Spring Security chain so that every filter after it is covered.
 */
@Component
@Order(SecurityProperties.DEFAULT_FILTER_ORDER - 1)
public class FilterChainExceptionFilter extends OncePerRequestFilter {

    private final HandlerExceptionResolver resolver;

    public FilterChainExceptionFilter(@Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            chain.doFilter(request, response);
        } catch (ServletException | IOException | RuntimeException ex) {
            // Half a response already sent (e.g. the client went away): nothing sensible to add.
            if (response.isCommitted() || resolver.resolveException(request, response, null, unwrap(ex)) == null) {
                throw ex;
            }
        }
    }

    /** Servlet containers wrap a filter's unchecked exception in ServletException; map the real cause. */
    private static Exception unwrap(Exception ex) {
        return ex instanceof ServletException && ex.getCause() instanceof Exception cause ? cause : ex;
    }
}
