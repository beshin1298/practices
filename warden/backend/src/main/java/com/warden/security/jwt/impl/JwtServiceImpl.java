package com.warden.security.jwt.impl;

import java.time.Instant;
import java.util.Date;
import java.util.UUID;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;

import org.springframework.stereotype.Service;

import com.warden.role.entity.Role;
import com.warden.security.jwt.JwtProperties;
import com.warden.security.jwt.JwtService;
import com.warden.user.entity.User;

/** Default {@link JwtService}. */
@Service
public class JwtServiceImpl implements JwtService {

    static final String CLAIM_USERNAME = "username";
    /** Informational only (UI); authorization always reloads roles from the DB. */
    static final String CLAIM_ROLES = "roles";
    static final String CLAIM_TOKEN_VERSION = "ver";
    /** Session id = refresh token family; the filter rejects the token once that family is revoked. */
    static final String CLAIM_SESSION_ID = "sid";

    private final SecretKey key;
    private final JwtProperties properties;
    /** Immutable and thread-safe: built once, not on every request. */
    private final JwtParser parser;

    public JwtServiceImpl(JwtProperties properties) {
        byte[] secret = Decoders.BASE64.decode(properties.secret());
        if (secret.length < 32) {
            throw new IllegalStateException("app.jwt.secret must be at least 32 bytes (256 bits) for HS256");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.properties = properties;
        this.parser = Jwts.parser().verifyWith(key).requireIssuer(properties.issuer()).build();
    }

    @Override
    public IssuedToken issue(User user, UUID sessionId) {
        Instant now = Instant.now();
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLES, user.getRoles().stream().map(Role::getCode).toList())
                .claim(CLAIM_TOKEN_VERSION, user.getTokenVersion())
                .claim(CLAIM_SESSION_ID, sessionId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(properties.expiration())))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
        return new IssuedToken(token, properties.expiration().toSeconds());
    }

    @Override
    public TokenClaims parse(String token) {
        Claims claims = parser.parseSignedClaims(token).getPayload();

        Integer version = claims.get(CLAIM_TOKEN_VERSION, Integer.class);
        String sessionId = claims.get(CLAIM_SESSION_ID, String.class);
        if (claims.getSubject() == null || version == null || sessionId == null) {
            throw new MalformedJwtException("Token is missing sub, ver or sid");
        }
        return new TokenClaims(Long.parseLong(claims.getSubject()), version, parseSessionId(sessionId));
    }

    private static UUID parseSessionId(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ex) {
            throw new MalformedJwtException("Token has a malformed sid");
        }
    }

}
