package com.platform.ingestion.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable audit record for every email ingestion attempt — successful or not.
 * Written atomically alongside the Transaction. Never updated after creation.
 *
 * In production this would go to an append-only audit log store, not the same
 * DB table as mutable transaction state.
 */
public class IngestionAuditLog {

    public enum Outcome {
        SUCCESS,
        REJECTED_DOMAIN_NOT_WHITELISTED,
        REJECTED_DUPLICATE,
        REJECTED_PARSE_FAILURE
    }

    private final String id;
    private final String transactionId;   // null if no transaction was created
    private final String fromEmail;
    private final String fromDomain;
    private final Outcome outcome;
    private final String detail;          // human-readable reason for rejection or success note
    private final Instant timestamp;

    public IngestionAuditLog(
            String transactionId,
            String fromEmail,
            String fromDomain,
            Outcome outcome,
            String detail
    ) {
        this.id = UUID.randomUUID().toString();
        this.transactionId = transactionId;
        this.fromEmail = fromEmail;
        this.fromDomain = fromDomain;
        this.outcome = outcome;
        this.detail = detail;
        this.timestamp = Instant.now();
    }

    public String getId() { return id; }
    public String getTransactionId() { return transactionId; }
    public String getFromEmail() { return fromEmail; }
    public String getFromDomain() { return fromDomain; }
    public Outcome getOutcome() { return outcome; }
    public String getDetail() { return detail; }
    public Instant getTimestamp() { return timestamp; }
}
