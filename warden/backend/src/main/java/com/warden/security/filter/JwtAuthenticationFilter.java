package com.warden.security.filter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import com.warden.security.AuthUser;
import com.warden.security.handler.ProblemDetailsSecurityHandler;
import com.warden.security.service.TokenAuthenticationService;
import com.warden.security.service.TokenCheck;

/**
 * Authenticates requests that carry "Authorization: Bearer <jwt>". HTTP only: reading the token,
 * the user lookup and the checks live in {@link TokenAuthenticationService}.
 *
 * <p>Not a @Component on purpose: Spring Boot registers every Filter bean in the servlet
 * container as well, where it would run before Spring Security and have its SecurityContext
 * overwritten. It is created in {@link com.warden.config.SecurityConfig} instead.
 *
 * <p>This filter never rejects a request itself. On a bad token it records the reason and
 * continues unauthenticated; if the endpoint needs a user, Spring Security calls
 * {@link ProblemDetailsSecurityHandler}, which reads the reason and returns 401.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    public static final String AUTH_ERROR_ATTRIBUTE = JwtAuthenticationFilter.class.getName() + ".error";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TokenAuthenticationService tokenAuthentication;

    public JwtAuthenticationFilter(TokenAuthenticationService tokenAuthentication) {
        this.tokenAuthentication = tokenAuthentication;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            switch (tokenAuthentication.authenticate(header.substring(BEARER_PREFIX.length()))) {
                case TokenCheck.Accepted accepted -> authenticate(request, accepted.principal());
                case TokenCheck.Rejected rejected -> request.setAttribute(AUTH_ERROR_ATTRIBUTE, rejected.reason());
            }
        }
        chain.doFilter(request, response);
    }

    /** Authorities = the permissions of all the user's roles plus ROLE_<code> for each role. */
    private static void authenticate(HttpServletRequest request, AuthUser principal) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        principal.permissions().forEach(permission -> authorities.add(new SimpleGrantedAuthority(permission)));
        principal.roles().forEach(role -> authorities.add(new SimpleGrantedAuthority("ROLE_" + role)));
        UsernamePasswordAuthenticationToken authentication =
                UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities);
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
