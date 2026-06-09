package com.platform.ingestion.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request and response DTOs for the Email Ingestion API.
 * Kept in one file for readability given the small number of shapes.
 */
public final class Dtos {

    private Dtos() {}

    // -------------------------------------------------------------------------
    // POST /ingest
    // -------------------------------------------------------------------------

    public record IngestRequest(
            @NotBlank(message = "from_email is required")
            @Email(message = "from_email must be a valid email address")
            String from_email,

            @NotBlank(message = "from_domain is required")
            String from_domain,

            @NotBlank(message = "subject is required")
            String subject,

            @NotBlank(message = "body_text is required")
            String body_text
    ) {}

    public record IngestResponse(
            String transactionId,
            String status,
            String source,
            String counterpartyId,
            String instrumentType,
            String amount,
            String currency,
            String destinationAccount,
            String createdAt
    ) {}

    // -------------------------------------------------------------------------
    // POST /whitelist
    // -------------------------------------------------------------------------

    public record AddDomainRequest(
            @NotBlank(message = "domain is required")
            String domain,

            @NotBlank(message = "counterparty_id is required")
            String counterparty_id
    ) {}

    public record AddDomainResponse(
            String domain,
            String counterpartyId,
            String addedAt
    ) {}

    public record WhitelistEntryResponse(
            String domain,
            String counterpartyId,
            String addedAt
    ) {}

    // -------------------------------------------------------------------------
    // GET /transactions/{id}
    // -------------------------------------------------------------------------

    public record TransactionResponse(
            String id,
            String fromEmail,
            String fromDomain,
            String counterpartyId,
            String instrumentType,
            String amount,
            String currency,
            String destinationAccount,
            String status,
            String source,
            String createdAt,
            java.util.List<AuditLogEntry> auditLog
    ) {}

    public record AuditLogEntry(
            String id,
            String outcome,
            String detail,
            String timestamp
    ) {}
}
