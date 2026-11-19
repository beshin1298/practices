package com.warden.common.error;

/** A business error with a stable {@link ErrorCode}; turned into a ProblemDetail by {@link GlobalExceptionHandler}. */
public class AppException extends RuntimeException {

    private final ErrorCode code;

    public AppException(ErrorCode code) {
        this(code, code.defaultMessage());
    }

    public AppException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }
}
