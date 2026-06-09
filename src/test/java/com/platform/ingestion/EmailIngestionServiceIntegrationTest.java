package com.platform.ingestion;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Email Ingestion Service.
 *
 * Test selection rationale (financial platform context):
 *
 *  1. Happy path — confirms the full pipeline produces a transaction with correct fields.
 *  2. Domain not whitelisted — the primary security gate; must return a descriptive error, not a 500.
 *  3. Duplicate ingestion — prevents double-processing of the same instruction, which in a
 *     financial context would mean executing the same payment twice.
 *  4. Missing required field — confirms we reject malformed emails before any record is written;
 *     partial data must never reach the workflow engine.
 *  5. Atomic audit trail — after a successful ingest, GET /transactions/{id} must return the
 *     transaction AND its audit log in one response; verifies the atomic commit path.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EmailIngestionServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.platform.ingestion.repository.InMemoryStore store;

    // Seed a whitelisted domain before each test
    @BeforeEach
    void seedWhitelist() throws Exception {
        store.reset();
        mockMvc.perform(post("/whitelist")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "domain", "acme-bank.com",
                        "counterparty_id", "CP-001"
                ))))
                .andExpect(status().isCreated());
    }

    // -------------------------------------------------------------------------
    // Test 1: Happy path — valid email from whitelisted domain
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Valid email from whitelisted domain creates transaction with status INSTRUCTION_RECEIVED")
    void happyPath_validEmail_createsTransaction() throws Exception {
        MvcResult result = mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validIngestPayload("ops@acme-bank.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.transactionId", not(emptyString())))
                .andExpect(jsonPath("$.status", is("INSTRUCTION_RECEIVED")))
                .andExpect(jsonPath("$.source", is("EMAIL_INGEST")))
                .andExpect(jsonPath("$.counterpartyId", is("CP-001")))
                .andExpect(jsonPath("$.instrumentType", is("FX_FORWARD")))
                .andExpect(jsonPath("$.amount", is("1500000.00")))
                .andExpect(jsonPath("$.currency", is("USD")))
                .andExpect(jsonPath("$.destinationAccount", is("GB29NWBK60161331926819")))
                .andReturn();

        // Also confirm GET /transactions/{id} returns the same record
        String txId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("transactionId").asText();

        mockMvc.perform(get("/transactions/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(txId)))
                .andExpect(jsonPath("$.status", is("INSTRUCTION_RECEIVED")));
    }

    // -------------------------------------------------------------------------
    // Test 2: Domain not whitelisted — must return descriptive error, not 500
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Email from non-whitelisted domain is rejected with descriptive error")
    void domainNotWhitelisted_returnsDescriptiveError() throws Exception {
        mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validIngestPayload("ops@unknown-counterparty.com")))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("DOMAIN_NOT_WHITELISTED")))
                .andExpect(jsonPath("$.message", containsString("unknown-counterparty.com")));
    }

    // -------------------------------------------------------------------------
    // Test 3: Duplicate ingestion — same from_email + body_text within 60 minutes
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Identical email submitted twice within 60 minutes is rejected as duplicate")
    void duplicateIngestion_isRejectedWithConflict() throws Exception {
        String payload = validIngestPayload("ops@acme-bank.com");

        // First submission — should succeed
        mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isCreated());

        // Second submission — identical from_email + body_text — should be rejected
        mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error", is("DUPLICATE_INGESTION")));
    }

    // -------------------------------------------------------------------------
    // Test 4: Missing required field in email body
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Email body missing DESTINATION_ACCOUNT is rejected with parse failure error")
    void missingRequiredField_returnsParseFailureError() throws Exception {
        String incompleteBody = """
                INSTRUMENT_TYPE: FX_FORWARD
                AMOUNT: 1500000.00
                CURRENCY: USD
                """;
        // Note: DESTINATION_ACCOUNT is deliberately omitted

        String payload = objectMapper.writeValueAsString(Map.of(
                "from_email", "ops@acme-bank.com",
                "from_domain", "acme-bank.com",
                "subject", "Instruction Ref: TXN-9901",
                "body_text", incompleteBody
        ));

        mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error", is("EMAIL_PARSE_FAILURE")))
                .andExpect(jsonPath("$.message", containsString("DESTINATION_ACCOUNT")));
    }

    // -------------------------------------------------------------------------
    // Test 5: Atomic audit trail — transaction and audit log written together
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("Successful ingest results in transaction with audit log entry in GET response")
    void successfulIngest_auditLogWrittenAtomically() throws Exception {
        MvcResult result = mockMvc.perform(post("/ingest")
                .contentType(MediaType.APPLICATION_JSON)
                .content(validIngestPayload("audit-test@acme-bank.com")))
                .andExpect(status().isCreated())
                .andReturn();

        String txId = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("transactionId").asText();

        mockMvc.perform(get("/transactions/" + txId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(txId)))
                // Audit log must be present and non-empty
                .andExpect(jsonPath("$.auditLog", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.auditLog[0].outcome", is("SUCCESS")))
                .andExpect(jsonPath("$.auditLog[0].timestamp", not(emptyString())));
    }

    // -------------------------------------------------------------------------
    // Helper
    // -------------------------------------------------------------------------

    /**
     * Builds a well-formed ingest request JSON string with all required fields.
     * Body text uses a slightly varied reference per fromEmail to avoid cross-test dedup collisions.
     */
    private String validIngestPayload(String fromEmail) throws Exception {
        String body = """
                INSTRUMENT_TYPE: FX_FORWARD
                AMOUNT: 1500000.00
                CURRENCY: USD
                DESTINATION_ACCOUNT: GB29NWBK60161331926819
                REF: %s
                """.formatted(fromEmail); // makes body unique per sender in multi-sender tests

        return objectMapper.writeValueAsString(Map.of(
                "from_email", fromEmail,
                "from_domain", fromEmail.substring(fromEmail.indexOf('@') + 1),
                "subject", "Instruction Ref: TXN-9901",
                "body_text", body
        ));
    }
}
