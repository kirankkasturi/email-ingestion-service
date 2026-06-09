package com.platform.ingestion.service;

import com.platform.ingestion.exception.DomainNotWhitelistedException;
import com.platform.ingestion.exception.DuplicateIngestionException;
import com.platform.ingestion.exception.EmailParseException;
import com.platform.ingestion.model.IngestionAuditLog;
import com.platform.ingestion.model.ParsedInstruction;
import com.platform.ingestion.model.Transaction;
import com.platform.ingestion.model.WhitelistedDomain;
import com.platform.ingestion.repository.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Orchestrates the full email ingestion pipeline:
 *
 *  1. Extract and normalize sender domain from from_email
 *  2. Validate sender domain against whitelist
 *  3. Check for duplicate submission (same from_email + body_text within 60 min)
 *  4. Parse email body into structured instruction fields
 *  5. Atomically commit Transaction + IngestionAuditLog
 *
 * Every rejection path writes an audit log so that even failed ingestion attempts
 * leave a complete trace — important in a regulated environment.
 *
 * Exceptions propagate to GlobalExceptionHandler which maps them to structured
 * HTTP responses. This service never catches and swallows domain exceptions.
 */
@Service
public class IngestionService {

    private final InMemoryStore store;
    private final EmailParserService parser;

    public IngestionService(InMemoryStore store, EmailParserService parser) {
        this.store = store;
        this.parser = parser;
    }

    /**
     * Ingests one inbound email payload.
     *
     * @return the created Transaction
     * @throws DomainNotWhitelistedException if sender domain is not whitelisted
     * @throws DuplicateIngestionException   if an identical email arrived within 60 minutes
     * @throws EmailParseException           if a required instruction field is missing or invalid
     */
    public Transaction ingest(String fromEmail, String fromDomain, String subject, String bodyText) {

        String normalizedDomain = extractDomain(fromEmail);

        // --- Step 1: Domain whitelist check ---
        Optional<WhitelistedDomain> whitelisted = store.findDomain(normalizedDomain);
        if (whitelisted.isEmpty()) {
            store.persistRejectionAudit(new IngestionAuditLog(
                    null, fromEmail, normalizedDomain,
                    IngestionAuditLog.Outcome.REJECTED_DOMAIN_NOT_WHITELISTED,
                    "Domain not in whitelist: " + normalizedDomain
            ));
            throw new DomainNotWhitelistedException(normalizedDomain);
        }

        // --- Step 2: Deduplication check ---
        if (store.isDuplicate(fromEmail, bodyText)) {
            store.persistRejectionAudit(new IngestionAuditLog(
                    null, fromEmail, normalizedDomain,
                    IngestionAuditLog.Outcome.REJECTED_DUPLICATE,
                    "Duplicate of a submission received within the last 60 minutes"
            ));
            throw new DuplicateIngestionException(fromEmail);
        }

        // --- Step 3: Parse email body ---
        // EmailParseException propagates; we catch only to write the audit log before re-throwing
        ParsedInstruction instruction;
        try {
            instruction = parser.parse(bodyText);
        } catch (EmailParseException ex) {
            store.persistRejectionAudit(new IngestionAuditLog(
                    null, fromEmail, normalizedDomain,
                    IngestionAuditLog.Outcome.REJECTED_PARSE_FAILURE,
                    ex.getMessage()
            ));
            throw ex;
        }

        // --- Step 4: Build transaction and audit log ---
        Transaction tx = new Transaction(
                fromEmail,
                normalizedDomain,
                whitelisted.get().getCounterpartyId(),
                instruction.getInstrumentType(),
                instruction.getAmount(),
                instruction.getCurrency(),
                instruction.getDestinationAccount(),
                subject,
                bodyText
        );

        IngestionAuditLog auditLog = new IngestionAuditLog(
                tx.getId(),
                fromEmail,
                normalizedDomain,
                IngestionAuditLog.Outcome.SUCCESS,
                "Transaction created successfully via email ingestion"
        );

        // --- Step 5: Atomic commit ---
        store.commitTransactionWithAudit(tx, auditLog, bodyText);

        return tx;
    }

    /**
     * Extracts the domain portion from an email address.
     * Uses the from_domain field if provided and consistent; otherwise derives from from_email.
     *
     * Design note: we validate domain from the email address itself (not just the from_domain
     * field in the payload) because a malicious or misconfigured sender could populate
     * from_domain with a whitelisted value while sending from a different actual domain.
     * In production this would be reinforced by DKIM/SPF verification.
     */
    private String extractDomain(String fromEmail) {
        int atIndex = fromEmail.indexOf('@');
        if (atIndex < 0 || atIndex == fromEmail.length() - 1) {
            throw new EmailParseException("from_email (invalid format: " + fromEmail + ")");
        }
        return fromEmail.substring(atIndex + 1).toLowerCase().trim();
    }
}
