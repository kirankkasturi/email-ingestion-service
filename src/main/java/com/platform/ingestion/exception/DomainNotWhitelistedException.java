package com.platform.ingestion.exception;

/**
 * Thrown when an inbound email's sender domain is not in the whitelist.
 * Results in a 422 Unprocessable Entity — not a 500.
 */
public class DomainNotWhitelistedException extends RuntimeException {
    private final String domain;

    public DomainNotWhitelistedException(String domain) {
        super("Sender domain is not whitelisted: " + domain);
        this.domain = domain;
    }

    public String getDomain() { return domain; }
}
