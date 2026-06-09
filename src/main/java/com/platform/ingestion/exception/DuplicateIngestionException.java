package com.platform.ingestion.exception;

/**
 * Thrown when an identical email (same from_email + body_text) has already been
 * ingested within the 60-minute deduplication window.
 */
public class DuplicateIngestionException extends RuntimeException {
    public DuplicateIngestionException(String fromEmail) {
        super("Duplicate ingestion detected from: " + fromEmail + " within the 60-minute window.");
    }
}
