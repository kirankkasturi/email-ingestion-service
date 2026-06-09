package com.platform.ingestion.controller;

import com.platform.ingestion.model.IngestionAuditLog;
import com.platform.ingestion.model.Transaction;
import com.platform.ingestion.model.WhitelistedDomain;
import com.platform.ingestion.repository.InMemoryStore;
import com.platform.ingestion.service.IngestionService;
import com.platform.ingestion.service.WhitelistService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@RestController
public class IngestionController {

    private final IngestionService ingestionService;
    private final WhitelistService whitelistService;
    private final InMemoryStore store;

    public IngestionController(
            IngestionService ingestionService,
            WhitelistService whitelistService,
            InMemoryStore store
    ) {
        this.ingestionService = ingestionService;
        this.whitelistService = whitelistService;
        this.store = store;
    }

    // -------------------------------------------------------------------------
    // POST /ingest
    // -------------------------------------------------------------------------

    @PostMapping("/ingest")
    public ResponseEntity<Dtos.IngestResponse> ingest(@Valid @RequestBody Dtos.IngestRequest request) {
        Transaction tx = ingestionService.ingest(
                request.from_email(),
                request.from_domain(),
                request.subject(),
                request.body_text()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(toIngestResponse(tx));
    }

    // -------------------------------------------------------------------------
    // GET /whitelist
    // -------------------------------------------------------------------------

    @GetMapping("/whitelist")
    public ResponseEntity<List<Dtos.WhitelistEntryResponse>> getWhitelist() {
        Collection<WhitelistedDomain> domains = whitelistService.listDomains();
        List<Dtos.WhitelistEntryResponse> response = domains.stream()
                .map(d -> new Dtos.WhitelistEntryResponse(
                        d.getDomain(),
                        d.getCounterpartyId(),
                        d.getAddedAt().toString()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // POST /whitelist
    // -------------------------------------------------------------------------

    @PostMapping("/whitelist")
    public ResponseEntity<Dtos.AddDomainResponse> addToWhitelist(@Valid @RequestBody Dtos.AddDomainRequest request) {
        whitelistService.addDomain(request.domain(), request.counterparty_id());
        // Fetch back to return addedAt from the stored object
        WhitelistedDomain stored = store.findDomain(request.domain()).orElseThrow();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new Dtos.AddDomainResponse(
                        stored.getDomain(),
                        stored.getCounterpartyId(),
                        stored.getAddedAt().toString()
                ));
    }

    // -------------------------------------------------------------------------
    // GET /transactions/{id}
    // -------------------------------------------------------------------------

    @GetMapping("/transactions/{id}")
    public ResponseEntity<Dtos.TransactionResponse> getTransaction(@PathVariable String id) {
        return store.findTransactionWithAudit(id)
                .map(twa -> ResponseEntity.ok(toTransactionResponse(twa.transaction(), twa.auditLogs())))
                .orElse(ResponseEntity.notFound().build());
    }

    // -------------------------------------------------------------------------
    // Mappers
    // -------------------------------------------------------------------------

    private Dtos.IngestResponse toIngestResponse(Transaction tx) {
        return new Dtos.IngestResponse(
                tx.getId(),
                tx.getStatus().name(),
                tx.getSource().name(),
                tx.getCounterpartyId(),
                tx.getInstrumentType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDestinationAccount(),
                tx.getCreatedAt().toString()
        );
    }

    private Dtos.TransactionResponse toTransactionResponse(Transaction tx, List<IngestionAuditLog> logs) {
        List<Dtos.AuditLogEntry> auditEntries = logs.stream()
                .map(l -> new Dtos.AuditLogEntry(
                        l.getId(),
                        l.getOutcome().name(),
                        l.getDetail(),
                        l.getTimestamp().toString()
                ))
                .collect(Collectors.toList());

        return new Dtos.TransactionResponse(
                tx.getId(),
                tx.getFromEmail(),
                tx.getFromDomain(),
                tx.getCounterpartyId(),
                tx.getInstrumentType(),
                tx.getAmount(),
                tx.getCurrency(),
                tx.getDestinationAccount(),
                tx.getStatus().name(),
                tx.getSource().name(),
                tx.getCreatedAt().toString(),
                auditEntries
        );
    }
}
