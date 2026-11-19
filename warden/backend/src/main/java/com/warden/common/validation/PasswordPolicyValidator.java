package com.warden.common.validation;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class PasswordPolicyValidator implements ConstraintValidator<ValidPassword, String> {

    public static final int MIN_LENGTH = 8;
    /** BCrypt only looks at the first 72 bytes; longer input is rejected rather than silently truncated. */
    public static final int MAX_BYTES = 72;

    private static final Pattern LETTER = Pattern.compile("\\p{L}");
    private static final Pattern DIGIT = Pattern.compile("\\p{Nd}");

    public static boolean fitsBcrypt(String raw) {
        return raw != null && raw.getBytes(StandardCharsets.UTF_8).length <= MAX_BYTES;
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return fail(context, "Mật khẩu không được để trống");
        }
        if (value.length() < MIN_LENGTH) {
            return fail(context, "Mật khẩu phải có ít nhất " + MIN_LENGTH + " ký tự");
        }
        if (!fitsBcrypt(value)) {
            return fail(context, "Mật khẩu tối đa " + MAX_BYTES + " byte");
        }
        if (!LETTER.matcher(value).find() || !DIGIT.matcher(value).find()) {
            return fail(context, "Mật khẩu phải có cả chữ và số");
        }
        return true;
    }

    private static boolean fail(ConstraintValidatorContext context, String message) {
        context.disableDefaultConstraintViolation();
        context.buildConstraintViolationWithTemplate(message).addConstraintViolation();
        return false;
    }
}
