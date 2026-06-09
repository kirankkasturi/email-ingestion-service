package com.platform.ingestion.model;

import java.time.Instant;

/**
 * Represents a counterparty domain that is permitted to submit instructions via email.
 * Only emails from whitelisted domains are accepted by the ingestion service.
 */
public class WhitelistedDomain {

    private final String domain;
    private final String counterpartyId;
    private final Instant addedAt;

    public WhitelistedDomain(String domain, String counterpartyId) {
        // Normalize to lowercase to prevent case-sensitivity bypass
        this.domain = domain.toLowerCase().trim();
        this.counterpartyId = counterpartyId;
        this.addedAt = Instant.now();
    }

    public String getDomain() { return domain; }
    public String getCounterpartyId() { return counterpartyId; }
    public Instant getAddedAt() { return addedAt; }
}
