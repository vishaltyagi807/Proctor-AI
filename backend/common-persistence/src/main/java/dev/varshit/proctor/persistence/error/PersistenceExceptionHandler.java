package dev.varshit.proctor.persistence.error;

import dev.varshit.proctor.common.dto.ApiError;
import io.r2dbc.spi.R2dbcException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class PersistenceExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PersistenceExceptionHandler.class);

    @ExceptionHandler({DataAccessException.class, R2dbcException.class})
    public ResponseEntity<ApiError> handle(Exception ex) {
        R2dbcException cause = findR2dbcCause(ex);
        String state = cause == null || cause.getSqlState() == null ? "" : cause.getSqlState();
        String message = cause == null ? "" : String.valueOf(cause.getMessage());

        if (state.equals("42501")) {
            return respond(HttpStatus.FORBIDDEN, forbiddenMessage(message));
        }
        if (state.equals("23505")) {
            return respond(HttpStatus.CONFLICT, "A record with the same unique value already exists");
        }
        if (state.equals("23503")) {
            return respond(HttpStatus.BAD_REQUEST, "Referenced record does not exist or is still in use");
        }
        if (state.startsWith("23") || state.startsWith("22")) {
            return respond(HttpStatus.BAD_REQUEST, "Invalid data supplied");
        }
        if (state.equals("P0001")) {
            return respond(HttpStatus.BAD_REQUEST, stripCode(message));
        }
        log.error("Database failure (state={})", state, ex);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected server error");
    }

    private R2dbcException findR2dbcCause(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof R2dbcException r2dbc) {
                return r2dbc;
            }
            current = current.getCause();
        }
        return null;
    }

    private String forbiddenMessage(String message) {
        if (message.contains("row-level security") || message.contains("permission denied")) {
            return "You do not have permission to perform this action";
        }
        return stripCode(message);
    }

    private String stripCode(String message) {
        int idx = message.indexOf(": ");
        return idx > 0 && message.substring(0, idx).matches("[A-Z_]+") ? message.substring(idx + 2) : message;
    }

    private ResponseEntity<ApiError> respond(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(ApiError.of(status.value(), status.name(), message));
    }
}
