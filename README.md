# Email Ingestion Service

Standalone microservice that automates instruction creation from whitelisted counterparty emails. Part of the regulated financial infrastructure platform.

---

## Table of Contents

- [Quick Start (Automated)](#quick-start-automated)
- [Installation & Running](#installation--running)
- [Running Tests](#running-tests)
- [API Quick Reference](#api-quick-reference)
- [Postman Testing Guide](#postman-testing-guide)
- [Three Most Important Design Decisions](#three-most-important-design-decisions)
- [Two Things I Would Do Differently in Production](#two-things-i-would-do-differently-in-production)
- [One Thing I Would Change About the Spec](#one-thing-i-would-change-about-the-spec)

---

## Quick Start (Automated)

If you don't already have Java and Maven installed, use the bundled setup script for your platform. It installs the prerequisites (Java 17 and Maven), builds the project, and starts the service automatically. The service is ready when you see `Started EmailIngestionServiceApplication on port 8080`. Press `Ctrl+C` to stop it.

### macOS — `run.sh`

```bash
# 1. Open Terminal and navigate to this folder
cd ~/Downloads/email-ingestion-service

# 2. Make the script executable (one time)
chmod +x run.sh

# 3. Run it
./run.sh
```

The script installs Homebrew (if missing), then Java 17 and Maven via Homebrew, builds the app, and runs it.

### Windows — `run.ps1`

```powershell
# 1. Open PowerShell as Administrator
#    (Windows key -> type "PowerShell" -> right-click -> "Run as Administrator")

# 2. Allow scripts to run (one time)
Set-ExecutionPolicy -Scope CurrentUser RemoteSigned

# 3. Navigate to this folder
cd "$env:USERPROFILE\Downloads\email-ingestion-service"

# 4. Run it
.\run.ps1
```

The script installs Java 17 and Maven via `winget` (with a direct-download fallback), builds the app, and runs it.

> Already have Java 17 and Maven? Skip the scripts and follow the manual steps below.

---

## Installation & Running

### Prerequisites

| Tool  | Minimum Version | Check           |
|-------|----------------|-----------------|
| Java  | 17             | `java -version` |
| Maven | 3.8            | `mvn -version`  |

No database, no Docker, no external services required. Everything runs in-memory.

### Steps

```bash
# 1. Unzip the archive
unzip email-ingestion-service.zip
cd email-ingestion-service

# 2. Build (downloads dependencies, compiles, runs tests)
mvn clean package

# 3. Run
mvn spring-boot:run
```

You should see:
```
Started EmailIngestionServiceApplication on port 8080
```

The service is ready when that line appears. All data is in-memory — it resets on restart.

---

## Running Tests

```bash
mvn test
```

Five integration tests run against a full Spring context. No external dependencies. Expected output: `BUILD SUCCESS`, 5 tests run, 0 failures.

---

## API Quick Reference

| Method | Endpoint             | Description                                     |
|--------|----------------------|-------------------------------------------------|
| `POST` | `/ingest`            | Submit a simulated inbound email payload        |
| `GET`  | `/whitelist`         | List all whitelisted counterparty domains       |
| `POST` | `/whitelist`         | Add a domain to the whitelist                   |
| `GET`  | `/transactions/{id}` | Fetch a transaction and its ingestion audit log |

### Email Body Format

The parser expects `KEY: VALUE` pairs, one per line (case-insensitive keys):

```
INSTRUMENT_TYPE: FX_FORWARD
AMOUNT: 1500000.00
CURRENCY: USD
DESTINATION_ACCOUNT: GB29NWBK60161331926819
```

All four fields are required. A missing or blank field causes a `422` with the name of the missing field.

---

## Postman Testing Guide

Run these in order — they build on each other.

### Setup (one time)

1. Download and install Postman from [postman.com/downloads](https://www.postman.com/downloads)
2. Make sure the service is running (`Started EmailIngestionServiceApplication on port 8080`)
3. For every `POST` request: click the **Body** tab → select **raw** → set the type dropdown to **JSON**

---

### Step 0 — Confirm the service is up

| Field  | Value                             |
|--------|-----------------------------------|
| Method | `GET`                             |
| URL    | `http://localhost:8080/whitelist` |

**Expected response (200 OK):**
```json
[]
```
Empty array — no domains whitelisted yet.

---

### Step 1 — Add a domain to the whitelist

| Field  | Value                             |
|--------|-----------------------------------|
| Method | `POST`                            |
| URL    | `http://localhost:8080/whitelist` |

**Request body:**
```json
{
  "domain": "acme-bank.com",
  "counterparty_id": "CP-001"
}
```

**Expected response (201 Created):**
```json
{
  "domain": "acme-bank.com",
  "counterpartyId": "CP-001",
  "addedAt": "2024-..."
}
```

---

### Step 2 — View the whitelist

| Field  | Value                             |
|--------|-----------------------------------|
| Method | `GET`                             |
| URL    | `http://localhost:8080/whitelist` |

**Expected response (200 OK):**
```json
[
  {
    "domain": "acme-bank.com",
    "counterpartyId": "CP-001",
    "addedAt": "2024-..."
  }
]
```

---

### Step 3 — Happy path: valid email from whitelisted domain

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |

**Request body:**
```json
{
  "from_email": "ops@acme-bank.com",
  "from_domain": "acme-bank.com",
  "subject": "Instruction Ref: TXN-9901",
  "body_text": "INSTRUMENT_TYPE: FX_FORWARD\nAMOUNT: 1500000.00\nCURRENCY: USD\nDESTINATION_ACCOUNT: GB29NWBK60161331926819"
}
```

**Expected response (201 Created):**
```json
{
  "transactionId": "<uuid>",
  "status": "INSTRUCTION_RECEIVED",
  "source": "EMAIL_INGEST",
  "counterpartyId": "CP-001",
  "instrumentType": "FX_FORWARD",
  "amount": "1500000.00",
  "currency": "USD",
  "destinationAccount": "GB29NWBK60161331926819",
  "createdAt": "2024-..."
}
```

Copy the `transactionId` value — you will need it for Step 8.

---

### Step 4 — Rejection: domain not whitelisted

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |

**Request body:**
```json
{
  "from_email": "ops@unknown-counterparty.com",
  "from_domain": "unknown-counterparty.com",
  "subject": "Instruction Ref: TXN-9902",
  "body_text": "INSTRUMENT_TYPE: FX_FORWARD\nAMOUNT: 500000.00\nCURRENCY: GBP\nDESTINATION_ACCOUNT: GB29NWBK60161331926819"
}
```

**Expected response (422 Unprocessable Entity):**
```json
{
  "error": "DOMAIN_NOT_WHITELISTED",
  "message": "Sender domain is not whitelisted: unknown-counterparty.com",
  "timestamp": "2024-..."
}
```

---

### Step 5 — Rejection: duplicate submission within 60 minutes

Send the exact same request body as Step 3 again (same `from_email` + `body_text`).

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |
| Body   | Same JSON as Step 3             |

**Expected response (409 Conflict):**
```json
{
  "error": "DUPLICATE_INGESTION",
  "message": "Duplicate ingestion detected from: ops@acme-bank.com within the 60-minute window.",
  "timestamp": "2024-..."
}
```

---

### Step 6 — Rejection: missing required field in email body

Note: `DESTINATION_ACCOUNT` is deliberately omitted.

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |

**Request body:**
```json
{
  "from_email": "ops@acme-bank.com",
  "from_domain": "acme-bank.com",
  "subject": "Instruction Ref: TXN-9903",
  "body_text": "INSTRUMENT_TYPE: FX_FORWARD\nAMOUNT: 750000.00\nCURRENCY: EUR"
}
```

**Expected response (422 Unprocessable Entity):**
```json
{
  "error": "EMAIL_PARSE_FAILURE",
  "message": "Email body is missing required instruction field: DESTINATION_ACCOUNT",
  "timestamp": "2024-..."
}
```

---

### Step 7 — Rejection: invalid amount

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |

**Request body:**
```json
{
  "from_email": "ops@acme-bank.com",
  "from_domain": "acme-bank.com",
  "subject": "Instruction Ref: TXN-9904",
  "body_text": "INSTRUMENT_TYPE: FX_FORWARD\nAMOUNT: -500.00\nCURRENCY: USD\nDESTINATION_ACCOUNT: GB29NWBK60161331926819"
}
```

**Expected response (422 Unprocessable Entity):**
```json
{
  "error": "EMAIL_PARSE_FAILURE",
  "message": "Email body is missing required instruction field: AMOUNT (must be positive, got: -500.00)",
  "timestamp": "2024-..."
}
```

---

### Step 8 — Fetch transaction with audit log

Replace `<transaction-id>` with the `transactionId` you copied from Step 3.

| Field  | Value                                                  |
|--------|--------------------------------------------------------|
| Method | `GET`                                                  |
| URL    | `http://localhost:8080/transactions/<transaction-id>`  |

**Expected response (200 OK):**
```json
{
  "id": "<transaction-id>",
  "fromEmail": "ops@acme-bank.com",
  "fromDomain": "acme-bank.com",
  "counterpartyId": "CP-001",
  "instrumentType": "FX_FORWARD",
  "amount": "1500000.00",
  "currency": "USD",
  "destinationAccount": "GB29NWBK60161331926819",
  "status": "INSTRUCTION_RECEIVED",
  "source": "EMAIL_INGEST",
  "createdAt": "2024-...",
  "auditLog": [
    {
      "id": "<uuid>",
      "outcome": "SUCCESS",
      "detail": "Transaction created successfully via email ingestion",
      "timestamp": "2024-..."
    }
  ]
}
```

---

### Step 9 — Fetch non-existent transaction

| Field  | Value                                                   |
|--------|---------------------------------------------------------|
| Method | `GET`                                                   |
| URL    | `http://localhost:8080/transactions/does-not-exist`     |

**Expected:** `404 Not Found`

---

### Step 10 — Add a second counterparty and verify isolation

**10a — Add second domain**

| Field  | Value                             |
|--------|-----------------------------------|
| Method | `POST`                            |
| URL    | `http://localhost:8080/whitelist` |

**Request body:**
```json
{
  "domain": "global-securities.io",
  "counterparty_id": "CP-002"
}
```

**10b — Verify both entries appear**

| Field  | Value                             |
|--------|-----------------------------------|
| Method | `GET`                             |
| URL    | `http://localhost:8080/whitelist` |

Response should contain both `acme-bank.com` and `global-securities.io`.

**10c — Ingest from second counterparty**

| Field  | Value                           |
|--------|---------------------------------|
| Method | `POST`                          |
| URL    | `http://localhost:8080/ingest`  |

**Request body:**
```json
{
  "from_email": "treasury@global-securities.io",
  "from_domain": "global-securities.io",
  "subject": "Instruction Ref: TXN-0042",
  "body_text": "INSTRUMENT_TYPE: BOND_PURCHASE\nAMOUNT: 5000000.00\nCURRENCY: GBP\nDESTINATION_ACCOUNT: GB33BUKB20201555555555"
}
```

**Expected response (201 Created):**
```json
{
  "transactionId": "<uuid>",
  "status": "INSTRUCTION_RECEIVED",
  "counterpartyId": "CP-002"
}
```

---

## Three Most Important Design Decisions

### 1. Domain extracted from `from_email`, not just the `from_domain` field

The payload includes both `from_email` and `from_domain`. The whitelist check is performed against the domain parsed directly out of `from_email`. The `from_domain` field is accepted but not trusted for the security decision.

**Why it matters:** A misconfigured or malicious client could send `from_domain: acme-bank.com` while setting `from_email: attacker@evil.com`. Trusting `from_domain` alone would bypass the whitelist entirely. In production, this is further reinforced by DKIM/SPF header verification performed upstream before the payload reaches this service.

---

### 2. Atomic commit of Transaction and IngestionAuditLog

The spec requires these to either both succeed or both fail. In the in-memory implementation this is enforced by a `synchronized` commit method in `InMemoryStore` — no thread can observe a Transaction without its corresponding audit log or vice-versa.

**Why it matters:** In a regulated environment, every instruction must have a complete audit trail. A transaction written without an audit log is an operational and compliance gap. In production (PostgreSQL), this becomes a single DB transaction wrapping both `INSERT` statements.

---

### 3. Hard-reject parsing — no inference, no defaults

The email parser throws `EmailParseException` on the first missing or blank required field. It does not attempt to infer a default value, use the subject line as a fallback, or accept partial records.

**Why it matters:** These are high-value, irreversible financial instructions. An incorrect amount or destination account silently inferred from incomplete data is worse than a clear rejection. The counterparty receives a `422` with the name of the missing field and can resubmit a corrected email. The audit log records the rejection with full detail.

---

## Two Things I Would Do Differently in Production

**1. DKIM/SPF verification before domain whitelisting.**
The current implementation trusts that the email relay has already verified the sender. In production, the service would inspect raw email headers (or receive a verification result from an upstream mail gateway) to confirm the sender's domain is cryptographically authenticated. Without this, a spoofed `From:` header could pass domain whitelist validation.

**2. Persistent, append-only audit log in a separate store.**
The current audit log lives in the same in-memory map as transactions. In production, audit records would go to an append-only store (e.g. a separate PostgreSQL table with no `DELETE`/`UPDATE` grants, or an immutable log service). This prevents any application bug — or a compromised service account — from modifying the audit trail after the fact. The transaction and audit log would still be written in a single DB transaction to preserve atomicity.

---

## One Thing I Would Change About the Spec

The spec says to reject a duplicate if `from_email` and `body_text` match within 60 minutes. Body text is a poor dedup key for high-value instructions because two legitimately separate instructions from the same counterparty could have identical amounts, instruments, and destinations (e.g. a recurring daily FX hedge). A better dedup key would be a counterparty-provided idempotency token (e.g. a `REFERENCE_ID` field in the email body) combined with the sender domain. This moves deduplication from "identical content" to "intentionally repeated submission" — which is the actual thing we want to catch.
