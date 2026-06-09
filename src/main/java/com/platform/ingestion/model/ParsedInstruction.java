package com.platform.ingestion.model;

/**
 * Holds structured fields extracted from a counterparty email body.
 * All fields required — a missing field means the email fails parsing and is rejected.
 */
public class ParsedInstruction {

    private final String instrumentType;
    private final String amount;
    private final String currency;
    private final String destinationAccount;

    public ParsedInstruction(
            String instrumentType,
            String amount,
            String currency,
            String destinationAccount
    ) {
        this.instrumentType = instrumentType;
        this.amount = amount;
        this.currency = currency;
        this.destinationAccount = destinationAccount;
    }

    public String getInstrumentType() { return instrumentType; }
    public String getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getDestinationAccount() { return destinationAccount; }
}
