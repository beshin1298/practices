package com.warden.config;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.warden.security.filter.JwtAuthenticationFilter;
import com.warden.security.handler.ProblemDetailsSecurityHandler;
import com.warden.security.service.TokenAuthenticationService;

/**
 * Three layers of authorization (defense in depth):
 * <ol>
 *   <li>URL rules below (permission per endpoint), checked in the filter chain before Spring MVC runs.</li>
 *   <li>The same permission on the controller method (@PreAuthorize / @PublicEndpoint / @Authenticated).</li>
 *   <li>Row scope (own / department / all, rank) in UserAccessPolicy.</li>
 * </ol>
 * Layer 1 matters because @Valid runs before @PreAuthorize: without it an EMPLOYEE posting an
 * invalid body to POST /api/users would get 400 instead of 403. rbac_audit.py checks that layers
 * 1 and 2 match docs/rbac-matrix.md.
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, TokenAuthenticationService tokenAuthentication,
            ProblemDetailsSecurityHandler problemHandler) throws Exception {
        http
                // Token travels in the Authorization header, never in a cookie: no CSRF surface.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // refresh/logout authenticate with the HttpOnly refresh cookie, not the access token
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/refresh", "/api/auth/logout").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Container healthcheck; nginx does not proxy /actuator, so only the Docker network reaches it.
                        .requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
                        // Mirrors the API table of docs/rbac-matrix.md; first match wins, so /me comes before /*.
                        .requestMatchers(HttpMethod.GET, "/api/users/me").hasAuthority("USER_READ_OWN")
                        .requestMatchers(HttpMethod.PUT, "/api/users/me", "/api/users/me/password").hasAuthority("USER_UPDATE_OWN")
                        .requestMatchers(HttpMethod.GET, "/api/users", "/api/users/*").hasAnyAuthority("USER_READ_DEPARTMENT", "USER_READ_ALL")
                        .requestMatchers(HttpMethod.POST, "/api/users").hasAuthority("USER_CREATE")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*/departments").hasAuthority("USER_UPDATE_ALL")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*/roles", "/api/users/*/permissions").hasAuthority("USER_ASSIGN_ROLE")
                        .requestMatchers(HttpMethod.PUT, "/api/users/*").hasAnyAuthority("USER_UPDATE_DEPARTMENT", "USER_UPDATE_ALL")
                        .requestMatchers(HttpMethod.PATCH, "/api/users/*/status").hasAuthority("USER_DISABLE")
                        .requestMatchers(HttpMethod.GET, "/api/roles").hasAuthority("RBAC_READ")
                        .requestMatchers(HttpMethod.PUT, "/api/roles/*/permissions").hasAuthority("RBAC_UPDATE")
                        .anyRequest().authenticated())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemHandler)
                        .accessDeniedHandler(problemHandler))
                .addFilterBefore(new JwtAuthenticationFilter(tokenAuthentication),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /** BCrypt, cost factor 10 (about 70-100 ms per hash on a laptop). */
    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /** Only needed when the SPA is served from another origin; the Vite dev server proxies /api. */
    @Bean
    CorsConfigurationSource corsConfigurationSource(@Value("${app.cors.allowed-origins}") List<String> allowedOrigins) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        config.setMaxAge(3600L);
        // A SPA on another origin sends the refresh cookie only with credentials allowed (origins are explicit).
        config.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
