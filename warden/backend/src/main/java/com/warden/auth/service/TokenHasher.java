package com.warden.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** How a raw refresh token is stored: only its hash ever reaches the database. */
public final class TokenHasher {

    private TokenHasher() {
    }

    /** SHA-256, not BCrypt: the input already has 256 bits of entropy, and lookup must be an index hit. */
    public static String sha256(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }
}
