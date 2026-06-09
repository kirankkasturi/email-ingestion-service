package com.platform.ingestion.service;

import com.platform.ingestion.exception.EmailParseException;
import com.platform.ingestion.model.ParsedInstruction;
import org.springframework.stereotype.Service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a structured counterparty email body into a {@link ParsedInstruction}.
 *
 * Expected format (key-value, one per line, case-insensitive keys):
 *
 *   INSTRUMENT_TYPE: FX_FORWARD
 *   AMOUNT: 1500000.00
 *   CURRENCY: USD
 *   DESTINATION_ACCOUNT: GB29NWBK60161331926819
 *
 * Design decision: we use explicit key-value parsing rather than a template
 * or NLP approach. For a regulated financial platform, deterministic parsing
 * with hard failures on missing fields is safer than fuzzy extraction that might
 * silently infer an incorrect value. If the counterparty's email doesn't conform,
 * we reject it with a clear error — never guess.
 *
 * In production, the structured email format would be agreed contractually with
 * each counterparty and validated against a per-counterparty schema.
 */
@Service
public class EmailParserService {

    private static final Pattern FIELD_PATTERN =
            Pattern.compile("^([\\w]+)\\s*:\\s*(.+)$", Pattern.MULTILINE);

    public ParsedInstruction parse(String bodyText) {
        if (bodyText == null || bodyText.isBlank()) {
            throw new EmailParseException("body_text (empty)");
        }

        Matcher matcher = FIELD_PATTERN.matcher(bodyText);
        java.util.Map<String, String> fields = new java.util.HashMap<>();
        while (matcher.find()) {
            fields.put(matcher.group(1).toUpperCase().trim(), matcher.group(2).trim());
        }

        String instrumentType    = requireField(fields, "INSTRUMENT_TYPE");
        String amount            = requireField(fields, "AMOUNT");
        String currency          = requireField(fields, "CURRENCY");
        String destinationAccount = requireField(fields, "DESTINATION_ACCOUNT");

        validateAmount(amount);

        return new ParsedInstruction(instrumentType, amount, currency, destinationAccount);
    }

    private String requireField(java.util.Map<String, String> fields, String key) {
        String value = fields.get(key);
        if (value == null || value.isBlank()) {
            throw new EmailParseException(key);
        }
        return value;
    }

    private void validateAmount(String amount) {
        try {
            java.math.BigDecimal parsed = new java.math.BigDecimal(amount);
            if (parsed.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                throw new EmailParseException("AMOUNT (must be positive, got: " + amount + ")");
            }
        } catch (NumberFormatException e) {
            throw new EmailParseException("AMOUNT (not a valid number: " + amount + ")");
        }
    }
}
