package com.warden.security.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.security.access.prepost.PreAuthorize;

/**
 * Access level PUBLIC in docs/rbac-matrix.md. The URL must also be permitAll() in
 * {@link com.warden.config.SecurityConfig}, otherwise the filter chain answers 401 first.
 * Being explicit makes "public" a reviewed decision instead of a forgotten annotation.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@PreAuthorize("permitAll()")
public @interface PublicEndpoint {
}
