package app.skymek.commons.error;

import io.quarkus.runtime.annotations.RegisterForReflection;

import java.time.Instant;
import java.util.List;

/**
 * The error body every SkyMek service answers with. Clients read the machine code from {@code errorCode}
 * (for example {@code SUB_055}) and show {@code message}; {@code fieldErrors} is never null.
 */
@RegisterForReflection
public record ErrorResponse(
        Instant timestamp,
        int status,
        String errorCode,
        String message,
        String path,
        List<FieldError> fieldErrors
) {

    public ErrorResponse {
        fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    /** One rejected field; services leave {@code rejectedValue} null when it could be a secret. */
    @RegisterForReflection
    public record FieldError(String field, String message, Object rejectedValue) {
    }

    public static ErrorResponse of(int status, String errorCode, String message, String path) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, path, List.of());
    }

    public static ErrorResponse of(int status, String errorCode, String message, String path,
                                   List<FieldError> fieldErrors) {
        return new ErrorResponse(Instant.now(), status, errorCode, message, path, fieldErrors);
    }

    public static ErrorResponse badRequest(String errorCode, String message, String path) {
        return of(400, errorCode, message, path);
    }
}
