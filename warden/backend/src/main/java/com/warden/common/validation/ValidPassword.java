package com.warden.common.validation;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

/**
 * Password policy: at least 8 characters, at most 72 bytes in UTF-8 (BCrypt ignores the rest),
 * contains at least one letter and one digit. The frontend Zod schema mirrors these rules.
 */
@Documented
@Constraint(validatedBy = PasswordPolicyValidator.class)
@Target({FIELD, PARAMETER, ANNOTATION_TYPE})
@Retention(RUNTIME)
public @interface ValidPassword {

    String message() default "Mật khẩu không hợp lệ";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
