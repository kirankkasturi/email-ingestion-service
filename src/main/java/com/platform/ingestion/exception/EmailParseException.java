package com.platform.ingestion.exception;

/**
 * Thrown when the email body cannot be parsed into a complete set of required instruction fields.
 * Results in a 422 — a missing field is a business error, not a server error.
 */
public class EmailParseException extends RuntimeException {
    private final String missingField;

    public EmailParseException(String missingField) {
        super("Email body is missing required instruction field: " + missingField);
        this.missingField = missingField;
    }

    public String getMissingField() { return missingField; }
}
