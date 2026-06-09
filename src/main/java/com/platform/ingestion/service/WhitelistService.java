package com.platform.ingestion.service;

import com.platform.ingestion.model.WhitelistedDomain;
import com.platform.ingestion.repository.InMemoryStore;
import org.springframework.stereotype.Service;

import java.util.Collection;

@Service
public class WhitelistService {

    private final InMemoryStore store;

    public WhitelistService(InMemoryStore store) {
        this.store = store;
    }

    public void addDomain(String domain, String counterpartyId) {
        store.addDomain(new WhitelistedDomain(domain, counterpartyId));
    }

    public Collection<WhitelistedDomain> listDomains() {
        return store.allDomains();
    }
}
