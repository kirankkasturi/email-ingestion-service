package com.platform.ingestion.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.Instant;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Maps domain exceptions to descriptive, structured HTTP error responses.
 * The spec explicitly requires descriptive errors — not 500s — for business rejections.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DomainNotWhitelistedException.class)
    public ResponseEntity<Map<String, Object>> handleDomainNotWhitelisted(DomainNotWhitelistedException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorBody("DOMAIN_NOT_WHITELISTED", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateIngestionException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicate(DuplicateIngestionException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(errorBody("DUPLICATE_INGESTION", ex.getMessage()));
    }

    @ExceptionHandler(EmailParseException.class)
    public ResponseEntity<Map<String, Object>> handleParseFailed(EmailParseException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(errorBody("EMAIL_PARSE_FAILURE", ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        String details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(errorBody("VALIDATION_ERROR", details));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneric(Exception ex) {
        // Log in production; here we surface the message for easier local debugging
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(errorBody("INTERNAL_ERROR", "An unexpected error occurred."));
    }

    private Map<String, Object> errorBody(String code, String message) {
        return Map.of(
                "error", code,
                "message", message,
                "timestamp", Instant.now().toString()
        );
    }
}
