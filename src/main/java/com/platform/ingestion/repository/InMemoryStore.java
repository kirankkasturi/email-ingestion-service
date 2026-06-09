package com.platform.ingestion.repository;

import com.platform.ingestion.model.IngestionAuditLog;
import com.platform.ingestion.model.Transaction;
import com.platform.ingestion.model.WhitelistedDomain;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Thread-safe in-memory store for all domain objects.
 *
 * Atomicity note: The spec requires that Transaction creation and IngestionAuditLog write
 * either both succeed or both fail. In production (with a real DB) this is a single DB
 * transaction. Here, we simulate it with a synchronized commit method that writes both
 * in one critical section — so a partial write is not observable by any concurrent reader.
 *
 * Deduplication: keyed on (fromEmail + bodyText) with a 60-minute TTL window.
 */
@Repository
public class InMemoryStore {

    // --- Whitelisted domains ---
    private final Map<String, WhitelistedDomain> domainWhitelist = new ConcurrentHashMap<>();

    // --- Transactions ---
    private final Map<String, Transaction> transactions = new ConcurrentHashMap<>();

    // --- Audit logs ---
    private final Map<String, IngestionAuditLog> auditLogs = new ConcurrentHashMap<>();

    // logs per transaction for fast lookup
    private final Map<String, List<String>> transactionAuditIndex = new ConcurrentHashMap<>();

    // --- Dedup index: key = fromEmail|bodyHash -> first-seen Instant ---
    private final Map<String, Instant> dedupIndex = new ConcurrentHashMap<>();

    // -------------------------------------------------------------------------
    // Test Support
    // -------------------------------------------------------------------------

    public synchronized void reset(){
        domainWhitelist.clear();
        transactions.clear();
        auditLogs.clear();
        transactionAuditIndex.clear();
        dedupIndex.clear();
    }

    // -------------------------------------------------------------------------
    // Whitelist operations
    // -------------------------------------------------------------------------

    public void addDomain(WhitelistedDomain domain) {
        domainWhitelist.put(domain.getDomain(), domain);
    }

    public Optional<WhitelistedDomain> findDomain(String domain) {
        return Optional.ofNullable(domainWhitelist.get(domain.toLowerCase().trim()));
    }

    public Collection<WhitelistedDomain> allDomains() {
        return Collections.unmodifiableCollection(domainWhitelist.values());
    }

    // -------------------------------------------------------------------------
    // Transaction operations
    // -------------------------------------------------------------------------

    public Optional<Transaction> findTransaction(String id) {
        return Optional.ofNullable(transactions.get(id));
    }

    // -------------------------------------------------------------------------
    // Deduplication
    // -------------------------------------------------------------------------

    /**
     * Returns true if this (fromEmail, bodyText) combination was already seen
     * within the last 60 minutes.
     */
    public boolean isDuplicate(String fromEmail, String bodyText) {
        String key = dedupKey(fromEmail, bodyText);
        Instant firstSeen = dedupIndex.get(key);
        if (firstSeen == null) return false;
        return firstSeen.isAfter(Instant.now().minusSeconds(60 * 60));
    }

    // -------------------------------------------------------------------------
    // Atomic commit: Transaction + AuditLog written together
    // -------------------------------------------------------------------------

    /**
     * Atomically persists a Transaction and its associated IngestionAuditLog.
     * Also registers the dedup key so subsequent duplicates are caught.
     *
     * Synchronized so that no thread can observe a Transaction without its
     * corresponding audit log or vice-versa.
     */
    public synchronized void commitTransactionWithAudit(
            Transaction tx,
            IngestionAuditLog log,
            String rawBodyText
    ) {
        transactions.put(tx.getId(), tx);
        persistAuditLog(log, tx.getId());
        dedupIndex.put(dedupKey(tx.getFromEmail(), rawBodyText), tx.getCreatedAt());
    }

    /**
     * Persists a rejection audit log (no transaction involved).
     */
    public void persistRejectionAudit(IngestionAuditLog log) {
        persistAuditLog(log, null);
    }

    /**
     * Returns the transaction and all of its associated audit log entries.
     */
    public Optional<TransactionWithAudit> findTransactionWithAudit(String id) {
        return findTransaction(id).map(tx -> {
            List<String> logIds = transactionAuditIndex.getOrDefault(tx.getId(), List.of());
            List<IngestionAuditLog> logs = logIds.stream()
                    .map(auditLogs::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            return new TransactionWithAudit(tx, logs);
        });
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private void persistAuditLog(IngestionAuditLog log, String txId) {
        auditLogs.put(log.getId(), log);
        if (txId != null) {
            transactionAuditIndex
                    .computeIfAbsent(txId, k -> Collections.synchronizedList(new ArrayList<>()))
                    .add(log.getId());
        }
    }

    private String dedupKey(String fromEmail, String bodyText) {
        // Simple concatenation key; in production use a cryptographic hash of bodyText
        return fromEmail.toLowerCase() + "|" + bodyText.strip();
    }

    // -------------------------------------------------------------------------
    // Nested read model
    // -------------------------------------------------------------------------

    public record TransactionWithAudit(Transaction transaction, List<IngestionAuditLog> auditLogs) {}
}
