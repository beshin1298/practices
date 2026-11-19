package com.warden.common.error;

import java.net.URI;

import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 ProblemDetail bodies with our extra "code" field. */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(ErrorCode code, String detail, String path) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(),
                detail != null ? detail : code.defaultMessage());
        problem.setTitle(code.status().getReasonPhrase());
        problem.setProperty("code", code.name());
        if (path != null) {
            try {
                problem.setInstance(URI.create(path));
            } catch (IllegalArgumentException ignored) {
                // a request URI java.net.URI cannot parse: leave "instance" out rather than fail here
            }
        }
        return problem;
    }
}
