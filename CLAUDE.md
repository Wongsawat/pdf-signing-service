# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Git Commit Conventions

When creating git commits for this repository:
- **Do NOT include** `Co-Authored-By:` or any co-author attribution in commit messages
- Keep commit messages focused and concise
- **All `CLAUDE.md` files are excluded from git tracking via `.gitignore`** - these instruction files remain local and are not committed to the repository

## Project Overview

**PDF Signing Service** - Spring Boot microservice for digitally signing PDF documents using PAdES (PDF Advanced Electronic Signatures) format via deferred signing.

**Tech Stack**: Java 21, Spring Boot 3.4.13, Apache Camel 4.14.4, Spring Cloud OpenFeign, Resilience4j, Apache PDFBox 3.0.6, BouncyCastle 1.83, CSC API v2.0, PostgreSQL, Kafka, saga-commons 1.0.0-SNAPSHOT

### Architecture Pattern

**Saga Orchestration** (as of commit 3652034):
- Consumes: `saga.command.pdf-signing`, `saga.compensation.pdf-signing` (from saga orchestrator)
- Produces:
  - `saga.reply.pdf-signing` (via outbox + Debezium CDC) → saga orchestrator
  - `notification.events` (via outbox + Debezium CDC) → notification-service (observer)

Reference implementation: `../xml-signing-service/`

> **Important**: The notification-service is a **reactive observer** that receives notification events regardless of saga orchestration. This service implements a **dual-publishing pattern**: one event for saga coordination (to orchestrator) and one event for user notifications (to notification-service).

## Build and Run Commands

```bash
# Build
mvn clean package

# Run locally (requires PostgreSQL, Kafka, and eidasremotesigning)
export DB_HOST=localhost
export KAFKA_BROKERS=localhost:9092
export CSC_SERVICE_URL=http://localhost:9000
export PADES_LEVEL=BASELINE_B
export SIGNED_PDF_STORAGE_PATH=/var/signed-documents
mkdir -p /var/signed-documents
mvn spring-boot:run

# Run with Docker test environment (different ports)
export DB_HOST=localhost
export DB_PORT=5433
export KAFKA_BROKERS=localhost:9093
export CSC_SERVICE_URL=http://localhost:9000
export SIGNED_PDF_STORAGE_PATH=/var/signed-documents
mkdir -p /var/signed-documents
mvn spring-boot:run

# Run tests (18 unit tests)
mvn test

# Run tests with coverage verification (JaCoCo 90% requirement configured in pom.xml)
mvn verify

# Run single test
mvn test -Dtest=ClassName#testMethod

# Database migrations
mvn flyway:migrate
mvn flyway:info
```

## Architecture

### Layer Structure (DDD)

```
com.wpanther.pdfsigning/
├── PdfSigningServiceApplication.java    # Main entry point
├── domain/
│   ├── model/
│   │   ├── SignedPdfDocument.java      # Aggregate root with state machine
│   │   ├── SignedPdfDocumentId.java    # Value object (UUID wrapper)
│   │   ├── SigningStatus.java          # PENDING → SIGNING → COMPLETED/FAILED
│   │   └── PadesLevel.java             # BASELINE_B, BASELINE_T, BASELINE_LT, BASELINE_LTA
│   ├── repository/
│   │   └── SignedPdfDocumentRepository.java  # Domain repository interface
│   ├── service/
│   │   └── PdfSigningService.java      # Domain service interface (PDF signing contract)
│   └── event/
│       ├── ProcessPdfSigningCommand.java      # Saga command (from orchestrator)
│       ├── CompensatePdfSigningCommand.java   # Saga compensation command
│       ├── PdfSigningReplyEvent.java          # Saga reply (to orchestrator)
│       ├── PdfSignedNotificationEvent.java    # Notification (to notification-service)
│       └── PdfSigningFailedNotificationEvent.java
│
├── application/
│   └── service/
│       └── SagaCommandHandler.java            # Saga command handler (process + compensate)
│
└── infrastructure/
    ├── persistence/
    │   ├── SignedPdfDocumentEntity.java        # JPA entity
    │   ├── JpaSignedPdfDocumentRepository.java # Spring Data repository
    │   ├── SignedPdfDocumentMapper.java        # MapStruct mapper
    │   ├── SignedPdfDocumentRepositoryAdapter.java  # Repository implementation
    │   └── outbox/
    │       ├── OutboxEventEntity.java         # Outbox pattern JPA entity
    │       ├── SpringDataOutboxRepository.java # Spring Data repository
    │       └── JpaOutboxEventRepository.java   # saga-commons adapter
    ├── client/
    │   ├── PdfSigningServiceImpl.java          # PDF signing implementation (deferred signing)
    │   └── csc/
    │       ├── CSCAuthClient.java              # Feign client (OAuth2 + SAD token)
    │       ├── CSCApiClient.java               # Feign client (signHash)
    │       └── dto/                            # CSC API DTOs
    ├── messaging/
    │   ├── SagaReplyPublisher.java              # Saga reply publisher (outbox)
    │   ├── NotificationEventPublisher.java      # Notification publisher (outbox)
    │   └── PdfSigningEventPublisher.java        # Combined dual-publishing wrapper
    ├── pdf/
    │   ├── PadesSignatureEmbedder.java         # PDF byte range digest, CMS, embedding
    │   └── CertificateParser.java               # PEM certificate chain parser
    └── config/
        ├── SagaRouteConfig.java                 # Saga topic consumers (Camel)
        ├── FeignConfig.java                    # Circuit breaker configuration
        └── CSCErrorDecoder.java                # Custom error handling
```

### PDF Signing Workflow (Saga Orchestration - Deferred Signing)

```
ProcessPdfSigningCommand (Kafka: saga.command.pdf-signing)
  - Produced by: saga orchestrator
  - Contains: sagaId, documentId, pdfUrl, invoiceNumber, documentType
          ↓
   SagaRouteConfig (Camel: kafka:saga.command.pdf-signing)
          ↓
   SagaCommandHandler.handleProcessCommand()
          ├── Check idempotency (invoice_id unique constraint)
          ├── Create or retrieve SignedPdfDocument
          ├── Validate retry limits
          ├── Start signing (state → SIGNING)
          ↓
   PdfSigningServiceImpl.signPdf()
          ├── Download PDF from URL
          ├── Compute PDF byte range digest (PDFBox)
          ├── CSCAuthClient.authorize() → SAD token
          ├── Encode digest as base64url
          ├── CSCApiClient.signHash() → raw signature
          ├── Parse certificate chain (BouncyCastle PEMParser)
          ├── Build CMS/PKCS#7 signature (BouncyCastle)
          ├── Embed signature into PDF (PDFBox)
          ├── Save to filesystem (YYYY/MM/DD/signed-pdf-{uuid}.pdf)
          └── Return SignedPdfResult
          ↓
   Mark completed (state → COMPLETED)
          ↓
   PdfSigningEventPublisher.publishSuccess()
          ├── saga.reply.pdf-signing (via outbox) → saga orchestrator
          └── notification.events (via outbox) → notification-service
```

### Domain Model State Machine

**SignedPdfDocument** is the aggregate root:
- `PENDING` → `startSigning()` → `SIGNING`
- `SIGNING` → `markCompleted(...)` → `COMPLETED`
- Any state → `markFailed(message)` → `FAILED`
- `FAILED` → `resetForRetry()` → `PENDING` (if retries < max)

State transition methods enforce invariants and throw `IllegalStateException` on invalid transitions.

**PadesLevel** enum defines conformance levels:
- `BASELINE_B` - Basic signature (current default)
- `BASELINE_T` - With trusted timestamp
- `BASELINE_LT` - Long-term validation (TSA + OCSP/CRL)
- `BASELINE_LTA` - Archive timestamp

## Prerequisites

Before running this service locally:

1. **PostgreSQL** on `localhost:5432` with database `pdfsigning_db`
2. **Kafka** on `localhost:9092`
3. **eidasremotesigning service** on `localhost:9000` (required for PDF signing)
4. **saga-commons library** installed: `cd /home/wpanther/projects/etax/saga-commons && mvn clean install`
5. **Debezium CDC** (optional, for outbox event routing): See `debezium/DEBEZIUM_SETUP.md`

## Key Configuration

Environment variables (in `application.yml`):

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | localhost | PostgreSQL host |
| `DB_NAME` | pdfsigning_db | Database name |
| `KAFKA_BROKERS` | localhost:9092 | Kafka bootstrap servers |
| `CSC_SERVICE_URL` | http://localhost:9000 | eidasremotesigning service URL |
| `CSC_CREDENTIAL_ID` | default-credential | CSC credential identifier |
| `CSC_CLIENT_ID` | pdf-signing-service | CSC client ID |
| `PADES_LEVEL` | BASELINE_B | PAdES conformance level |
| `SIGNED_PDF_STORAGE_PATH` | /var/signed-documents | Signed PDF storage directory |
| `SIGNED_PDF_STORAGE_BASE_URL` | http://localhost:8087 | Base URL for signed PDF access |
| `SIGNING_MAX_RETRIES` | 3 | Maximum retry attempts |
| `OUTBOX_CLEANUP_ENABLED` | false | Enable outbox cleanup job |

### PAdES Configuration

```yaml
app:
  pades:
    level: BASELINE_B    # B-B, B-T, B-LT, or B-LTA
  csc:
    sign-hash-endpoint: /csc/v2/signatures/signHash
    hash-algo: SHA256
```

## Kafka Topics (Saga Orchestration)

| Topic | Direction | Event Type |
|-------|-----------|------------|
| `saga.command.pdf-signing` | Consume | ProcessPdfSigningCommand (from orchestrator) |
| `saga.compensation.pdf-signing` | Consume | CompensatePdfSigningCommand (from orchestrator) |
| `saga.reply.pdf-signing` | Produce (via outbox + CDC) | PdfSigningReplyEvent (to orchestrator) |
| `notification.events` | Produce (via outbox + CDC) | PdfSignedNotificationEvent/PdfSigningFailedNotificationEvent (to notification-service) |
| `pdf.signing.dlq` | DLQ | Camel Dead Letter Channel |

### ProcessPdfSigningCommand (input)

Extends `IntegrationEvent` (from saga-commons)

**Saga fields** (from orchestrator):
- `sagaId` (String) - Saga instance identifier
- `sagaStep` (String) - Current step in saga (e.g., "sign-pdf")
- `correlationId` (String) - Correlation ID for tracing

**Event-specific fields**:
- `documentId` (String) - Document identifier (invoice ID)
- `invoiceNumber` (String) - Human-readable invoice number
- `documentType` (String) - Document type (INVOICE, TAX_INVOICE, etc.)
- `pdfUrl` (String) - URL to the generated PDF
- `pdfSize` (Long) - File size in bytes
- `xmlEmbedded` (Boolean) - Whether XML is embedded in the PDF

### PdfSigningReplyEvent (output to orchestrator)

Extends `SagaReply` (from saga-commons)

**Saga fields**:
- `sagaId`, `sagaStep`, `correlationId`
- `status` (ReplyStatus): SUCCESS, FAILURE, COMPENSATED

**SUCCESS reply includes**:
- `signedDocumentId`, `signedPdfUrl`, `signedPdfSize`
- `transactionId`, `certificate`, `signatureLevel`, `signatureTimestamp`

### PdfSignedNotificationEvent (output to notification-service)

Extends `IntegrationEvent` (from saga-commons)

**Event fields**:
- `invoiceId`, `invoiceNumber`, `documentType`
- `signedDocumentId`, `signedPdfUrl`, `signedPdfSize`
- `signatureLevel`, `signatureTimestamp`, `correlationId`

## Transactional Outbox Pattern

The service uses the **transactional outbox pattern** for reliable event publishing:

1. Events are saved to `outbox_events` table in the same transaction as business state changes
2. Debezium CDC streams changes from `outbox_events` to Kafka topics
3. `topic` field in outbox determines routing (saga.reply.pdf-signing or notification.events)

**Outbox table schema**:
- `id` (UUID) - Primary key
- `aggregate_type`, `aggregate_id` - Aggregate identification
- `event_type` - Event type (PdfSigningReply, PdfSigned, etc.)
- `payload` - Event payload (JSON)
- `status` - PENDING, PUBLISHED, FAILED
- `topic`, `partition_key`, `headers` - Debezium CDC routing fields

**Configuration**:
```yaml
saga:
  outbox:
    cleanup:
      enabled: ${OUTBOX_CLEANUP_ENABLED:false}
      cron-expression: "0 0 2 * * ?"
      retention-hours: 24
```

## CSC API Integration (Deferred Signing)

This service integrates with **eidasremotesigning** (CSC API v2.0) for PDF signing using **deferred signing**:

### 1. Authorization (CSCAuthClient)

```
POST /csc/v2/oauth2/authorize
{
  "credentialID": "default-credential",
  "numSignatures": 1,
  "hash": ["Base64(SHA256(pdfDigest))"]
}
→ { "SAD": "token" }
```

### 2. Signing (CSCApiClient - signHash endpoint)

```
POST /csc/v2/signatures/signHash
{
  "clientId": "pdf-signing-service",
  "credentialID": "default-credential",
  "hashAlgo": "SHA256",
  "signatureData": {
    "hashToSign": ["base64url(SHA256(pdfDigest))"]
  }
}
→ {
  "signatures": ["Base64(rawSignature)"],
  "signatureAlgorithm": "1.2.840.113549.1.1.11",
  "certificate": "-----BEGIN CERTIFICATE-----..."
}
```

**Key points:**
- PDF byte range digest is computed locally (Apache PDFBox 3.0.6)
- Only the hash is sent to CSC (base64url-encoded)
- CMS/PKCS#7 is constructed locally (BouncyCastle 1.83)
- Signature is embedded into PDF locally (Apache PDFBox 3.0.6)
- Certificate chain is parsed from PEM response (BouncyCastle PEMParser)

### PAdES Signed Attributes

Per ETSI EN 319 142-1, the following signed attributes are added:
- `contentType` (id-data): 1.2.840.113549.1.7.1
- `messageDigest`: SHA-256 of PDF byte range
- `signingTime`: Current timestamp
- `signingCertificateV2`: SHA-256 hash of signer certificate

**SubFilter:** `ETSI.CAdES.detached`

## Database

### Tables with Flyway migrations in `db/migration/`:

**Migrations**:
- `V1__create_signed_pdf_documents_table.sql` - Initial table creation with indexes
- `V2__add_document_type.sql` - Add document_type column (INVOICE, TAX_INVOICE, etc.)
- `V3__create_outbox_events_table.sql` - Outbox pattern table for CDC

**signed_pdf_documents schema**:
- `id` (UUID) - Primary key
- `invoice_id` (VARCHAR) - Reference to invoice (unique, for idempotency)
- `invoice_number` (VARCHAR) - Human-readable invoice identifier
- `document_type` (VARCHAR) - Document type (INVOICE, TAX_INVOICE, etc.)
- `original_pdf_url` (VARCHAR) - URL of unsigned PDF from pdf-generation-service
- `original_pdf_size` (BIGINT) - Original PDF file size
- `signed_pdf_path` (VARCHAR) - Filesystem path to signed PDF
- `signed_pdf_url` (VARCHAR) - Public URL to access signed PDF
- `signed_pdf_size` (BIGINT) - Signed PDF file size
- `transaction_id` (VARCHAR) - CSC API transaction ID (SAD token)
- `certificate` (TEXT) - PEM-encoded signing certificate
- `signature_level` (VARCHAR) - Signature level (PAdES-BASELINE-B)
- `signature_timestamp` (TIMESTAMP) - Timestamp
- `status` (VARCHAR) - Signing status: PENDING, SIGNING, COMPLETED, FAILED
- `error_message` (TEXT) - Error message if failed
- `retry_count` (INTEGER) - Number of retry attempts (default: 0)
- `created_at`, `completed_at`, `updated_at` (TIMESTAMP) - Timestamps
- `correlation_id` (VARCHAR) - Correlation ID for event tracing

**Indexes**: `invoice_id` (unique), `status`, `invoice_number`, `document_type`, `created_at`

## Key Implementation Details

### Idempotency
- Unique constraint on `invoice_id` prevents duplicate signing
- `SagaCommandHandler` checks existing documents before creating new ones
- Already completed documents trigger immediate SUCCESS reply (no re-signing)

### Retry Logic
- Failed signings can be retried up to `SIGNING_MAX_RETRIES` (default: 3)
- Retry count tracked in domain model
- `canRetry()` method enforces retry limits

### Compensation
- `handleCompensation()` deletes signed PDF from filesystem
- Removes document from database
- Sends COMPENSATED reply to orchestrator
- Idempotent (no error if document doesn't exist)

### File Storage
Files stored in date-based structure: `{SIGNED_PDF_STORAGE_PATH}/YYYY/MM/DD/signed-pdf-{uuid}.pdf`

### Apache Camel Error Handling
`SagaRouteConfig` uses Dead Letter Channel pattern with exponential backoff:
- 3 redelivery attempts (1s → 2s → 4s → 10s max delay)
- Failed events routed to `pdf.signing.dlq` topic
- `autoCommitEnable=false` ensures Camel only commits on success

### Circuit Breaker
Resilience4j circuit breaker configured for CSC API clients:
- Sliding window: 10 calls
- Failure threshold: 50%
- Wait duration in open state: 10s
- Timeout: 5s (auth), 30s (sign-hash)

### Annotation Processor Order
Lombok is configured before MapStruct in `pom.xml` under `maven-compiler-plugin`. This order is critical - Lombok must generate getters/setters/builders before MapStruct generates entity mappers.

### No REST API
This service is event-driven only. No REST endpoints except Spring Actuator (`/actuator/health`, `/actuator/metrics`, `/actuator/camelroutes`).

## Integration Points

- **Upstream** (Saga orchestrator):
  - saga orchestrator → `saga.command.pdf-signing` → pdf-signing-service
  - saga orchestrator → `saga.compensation.pdf-signing` → pdf-signing-service (rollback)
- **Downstream** (via outbox + Debezium CDC):
  - pdf-signing-service → `saga.reply.pdf-signing` → saga orchestrator
  - pdf-signing-service → `notification.events` → notification-service (observer)
- **External Dependencies**:
  - saga-commons library (com.wpanther:saga-commons) for IntegrationEvent, OutboxService
  - eidasremotesigning CSC API (localhost:9000) for digital signatures

## Testing

**Unit tests** (18 tests total across 6 test classes):
- `ProcessPdfSigningCommandTest` (2 tests) - Command creation and deserialization
- `PdfSigningReplyEventTest` (3 tests) - SUCCESS/FAILURE/COMPENSATED factory methods
- `PdfSignedNotificationEventTest` (2 tests) - Notification event creation
- `SagaCommandHandlerTest` (4 tests) - Command processing, idempotency, compensation
- `PadesSignatureEmbedderTest` (3 tests) - PDF digest computation, CMS building, signature embedding
- `CertificateParserTest` (4 tests) - PEM certificate chain parsing

## Debezium CDC Setup

See `debezium/DEBEZIUM_SETUP.md` for complete Debezium CDC connector configuration.

**Quick setup**:
```bash
# Register connector with Kafka Connect
curl -X POST http://localhost:8083/connectors \
  -H "Content-Type: application/json" \
  -d @debezium/connector-config.json

# Check status
curl http://localhost:8083/connectors/pdf-signing-outbox-connector/status
```

**Connector configuration** (`debezium/connector-config.json`):
- PostgreSQL CDC connector
- Captures changes from `outbox_events` table
- EventRouter SMT routes events by `topic` field
- Topics: `saga.reply.pdf-signing`, `notification.events`

## Podman Support (for tests)
Tests use Testcontainers for database integration testing. For Podman (instead of Docker), set before running tests:
```bash
export DOCKER_HOST="unix:///run/user/$(id -u)/podman/podman.sock"
```

## Migration History

- **Commit 3652034**: Migrated from delegated signing (signDocument) to deferred signing (signHash)
  - Added Apache PDFBox 3.0.6 and BouncyCastle 1.83 dependencies
  - Created `PadesSignatureEmbedder` for PDF byte range digest computation
  - Created `CertificateParser` for PEM certificate chain parsing
  - Created `CSCSignatureRequest/Response` DTOs for signHash endpoint
  - Replaced `CSCApiClient.signDocument()` with `signHash()` method
  - Rewrote `PdfSigningServiceImpl` for deferred signing flow
  - Added `PadesLevel` enum (BASELINE_B, BASELINE_T, BASELINE_LT, BASELINE_LTA)
  - Updated signature level from PAdES-BASELINE-T to PAdES-BASELINE-B
  - Added 7 new unit tests (18 total)
  - See implementation plan for detailed migration details
- **Commit 4270f3c**: Migrated from event choreography to saga orchestration
  - Removed legacy choreography (pdf.generated → pdf.signed)
  - Added saga command handlers and outbox pattern
  - Implemented dual-publishing (saga replies + notifications)
  - See `SAGA_MIGRATION_PLAN.md` for detailed migration plan
