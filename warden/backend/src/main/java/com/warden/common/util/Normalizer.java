package com.warden.common.util;

import java.util.Locale;

/**
 * Usernames and emails are stored trimmed and lower-cased so that UNIQUE constraints are
 * case-insensitive. The DB enforces the lower-case part with CHECK constraints (V1 migration).
 */
public final class Normalizer {

    private Normalizer() {
    }

    public static String key(String value) {
        return value == null ? null : value.trim().toLowerCase(Locale.ROOT);
    }
}
