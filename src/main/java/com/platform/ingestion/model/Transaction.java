package com.platform.ingestion.model;

import java.time.Instant;
import java.util.UUID;

/**
 * Represents a financial transaction created from an ingested counterparty email.
 * Status lifecycle: INSTRUCTION_RECEIVED -> (downstream states owned by workflow engine)
 */
public class Transaction {

    public enum Status {
        INSTRUCTION_RECEIVED
    }

    public enum Source {
        EMAIL_INGEST
    }

    private final String id;
    private final String fromEmail;
    private final String fromDomain;
    private final String counterpartyId;
    private final String instrumentType;
    private final String amount;
    private final String currency;
    private final String destinationAccount;
    private final String rawSubject;
    private final String rawBody;
    private final Status status;
    private final Source source;
    private final Instant createdAt;

    public Transaction(
            String fromEmail,
            String fromDomain,
            String counterpartyId,
            String instrumentType,
            String amount,
            String currency,
            String destinationAccount,
            String rawSubject,
            String rawBody
    ) {
        this.id = UUID.randomUUID().toString();
        this.fromEmail = fromEmail;
        this.fromDomain = fromDomain;
        this.counterpartyId = counterpartyId;
        this.instrumentType = instrumentType;
        this.amount = amount;
        this.currency = currency;
        this.destinationAccount = destinationAccount;
        this.rawSubject = rawSubject;
        this.rawBody = rawBody;
        this.status = Status.INSTRUCTION_RECEIVED;
        this.source = Source.EMAIL_INGEST;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getFromEmail() { return fromEmail; }
    public String getFromDomain() { return fromDomain; }
    public String getCounterpartyId() { return counterpartyId; }
    public String getInstrumentType() { return instrumentType; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDestinationAccount() { return destinationAccount; }
    public String getRawSubject() { return rawSubject; }
    public String getRawBody() { return rawBody; }
    public Status getStatus() { return status; }
    public Source getSource() { return source; }
    public Instant getCreatedAt() { return createdAt; }
}
