# PDF Signing Service

Microservice for digitally signing PDF documents using PAdES (PDF Advanced Electronic Signatures) format.

## Overview

The PDF Signing Service:

- ✅ **Downloads** unsigned PDFs from pdf-generation-service
- ✅ **Signs** PDFs using PAdES-BASELINE-T format via CSC API v2.0
- ✅ **Stores** signed PDFs to filesystem
- ✅ **Publishes** events for downstream services

## Architecture

### Technology Stack

| Component | Technology |
|-----------|------------|
| Language | Java 21 |
| Framework | Spring Boot 3.2.5 |
| Messaging | Apache Camel 4.14.4 |
| HTTP Client | Spring Cloud OpenFeign |
| Circuit Breaker | Resilience4j |
| Database | PostgreSQL |
| Message Broker | Apache Kafka |
| Signing API | CSC API v2.0 (eidasremotesigning) |
| Service Discovery | Netflix Eureka |

### Domain Model

**Aggregate Root:**
- `SignedPdfDocument` - Manages PDF signing lifecycle

**Status Enum:**
- `SigningStatus` - PENDING → SIGNING → COMPLETED/FAILED

## PDF Signing Flow

```
1. Receive PdfGeneratedEvent from Kafka (via Camel)
   ↓
2. Check idempotency (already signed?)
   ↓
3. Create SignedPdfDocument aggregate (status = PENDING)
   ↓
4. Start signing (status = SIGNING)
   ↓
5. Download PDF from URL
   ↓
6. Authorize with CSC API (get SAD token)
   ↓
7. Sign PDF via CSC API (PAdES-BASELINE-T)
   ↓
8. Save signed PDF to filesystem
   ↓
9. Mark as completed (status = COMPLETED)
   ↓
10. Publish PdfSignedEvent (via Camel)
```

## Kafka Integration (via Apache Camel)

### Consumed Topics
- `pdf.generated` - PDF generation completed (from invoice-pdf-generation-service, taxinvoice-pdf-generation-service)
- `pdf.signing.requested` - Alternative unified signing request topic

### Published Topics
- `pdf.signed` - PDF signing completed (to document-storage-service, notification-service)
- `pdf.signing.dlq` - Dead Letter Queue for failed events

### Camel Routes

**Consumer Routes:**
- `kafka:pdf.generated` → `PdfSigningOrchestrationService`
- `kafka:pdf.signing.requested` → `PdfSigningOrchestrationService`

**Producer Route:**
- `direct:publish-pdf-signed` → `kafka:pdf.signed`

**Error Handling:**
- Dead Letter Channel with exponential backoff (3 retries, 1s→10s max delay)

### Event Schema

**Input: PdfGeneratedEvent** (consumed from `pdf.generated`)
```json
{
  "eventId": "uuid",
  "eventType": "PdfGenerated",
  "occurredAt": "2025-01-29T10:30:00",
  "version": "1.0",
  "correlationId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentType": "INVOICE",
  "documentId": "uuid",
  "documentUrl": "http://localhost:8083/2025/01/07/invoice-123.pdf",
  "fileSize": 45678,
  "xmlEmbedded": true
}
```

**Output: PdfSignedEvent** (published to `pdf.signed`)
```json
{
  "eventId": "uuid",
  "eventType": "PdfSigned",
  "occurredAt": "2025-01-29T10:31:00",
  "version": "1.0",
  "correlationId": "uuid",
  "invoiceId": "uuid",
  "invoiceNumber": "INV-2025-001",
  "documentType": "INVOICE",
  "signedDocumentId": "uuid",
  "signedPdfUrl": "http://localhost:8087/signed-documents/2025/01/07/signed-pdf-abc123.pdf",
  "signedPdfSize": 48234,
  "transactionId": "TXN-uuid",
  "certificate": "-----BEGIN CERTIFICATE-----...",
  "signatureLevel": "PAdES-BASELINE-T",
  "signatureTimestamp": "2025-01-29T10:31:00"
}
```

## Database Schema

### signed_pdf_documents Table
- `id` (UUID) - Primary key
- `invoice_id` (VARCHAR) - Reference to invoice (unique)
- `invoice_number` (VARCHAR) - Invoice identifier
- `document_type` (VARCHAR) - Document type (INVOICE, TAX_INVOICE, etc.)
- `original_pdf_url` (VARCHAR) - URL of unsigned PDF
- `original_pdf_size` (BIGINT) - Original PDF file size
- `signed_pdf_path` (VARCHAR) - Filesystem path
- `signed_pdf_url` (VARCHAR) - HTTP URL
- `signed_pdf_size` (BIGINT) - Signed PDF file size
- `transaction_id` (VARCHAR) - CSC API transaction ID
- `certificate` (TEXT) - PEM-encoded signing certificate
- `signature_level` (VARCHAR) - Signature level
- `signature_timestamp` (TIMESTAMP) - Signature timestamp from CSC API
- `status` (VARCHAR) - Signing status (PENDING, SIGNING, COMPLETED, FAILED)
- `error_message` (TEXT) - Error message if failed
- `retry_count` (INTEGER) - Number of retry attempts
- `correlation_id` (VARCHAR) - Correlation ID for tracing
- Timestamps: `created_at`, `completed_at`, `updated_at`

## Configuration

### Environment Variables

| Variable | Description | Default |
|----------|-------------|------------|
| `DB_HOST` | PostgreSQL host | `localhost` |
| `DB_NAME` | Database name | `pdfsigning_db` |
| `KAFKA_BROKERS` | Kafka servers | `localhost:9092` |
| `CSC_SERVICE_URL` | eidasremotesigning URL | `http://localhost:9000` |
| `CSC_CREDENTIAL_ID` | CSC credential ID | `default-credential` |
| `SIGNED_PDF_STORAGE_PATH` | Storage path | `/var/signed-documents` |
| `SIGNING_MAX_RETRIES` | Max retry attempts | `3` |

## Running the Service

### Prerequisites
1. PostgreSQL database
2. Kafka broker
3. eidasremotesigning service (CSC API)
4. Storage directory with write permissions

### Build
```bash
mvn clean package
```

### Run Locally
```bash
export DB_HOST=localhost
export KAFKA_BROKERS=localhost:9092
export CSC_SERVICE_URL=http://localhost:9000
export SIGNED_PDF_STORAGE_PATH=/var/signed-documents

mkdir -p /var/signed-documents

mvn spring-boot:run
```

### Run with Docker
```bash
docker build -t pdf-signing-service:latest .

docker run -p 8087:8087 \
  -e DB_HOST=postgres \
  -e KAFKA_BROKERS=kafka:29092 \
  -e CSC_SERVICE_URL=http://eidasremotesigning:9000 \
  -v /host/signed-documents:/var/signed-documents \
  pdf-signing-service:latest
```

## Project Structure

```
src/main/java/com/invoice/pdfsigning/
├── PdfSigningServiceApplication.java
├── domain/
│   ├── model/              # SignedPdfDocument aggregate
│   ├── repository/         # Repository interface
│   ├── service/            # PdfSigningService interface
│   └── event/              # Kafka event DTOs
├── application/
│   └── service/            # PdfSigningOrchestrationService
└── infrastructure/
    ├── persistence/        # JPA entities, repositories
    ├── client/             # CSC API Feign clients
    ├── messaging/          # EventPublisher (Camel producer)
    └── config/             # PdfSigningRouteConfig, Feign, etc.
```

## Key Features

### Idempotency
- Unique constraint on `invoice_id` prevents duplicate signing
- Safe to replay Kafka messages

### Retry Logic
- Failed signings automatically retried up to 3 times
- Camel Dead Letter Channel with exponential backoff (1s→10s max delay)

### Circuit Breaker
- Resilience4j circuit breaker protects CSC API calls
- Automatic fallback and recovery

### Apache Camel Error Handling
- Dead Letter Channel pattern routes failed events to DLQ topic
- `autoCommitEnable=false` ensures Camel only commits on success
- Failed messages automatically retried with exponential backoff

## Integration with CSC API

This service integrates with **eidasremotesigning** for PDF signing:

1. **Authorization**: Get SAD token via `/csc/v2/oauth2/authorize`
2. **Signing**: Sign PDF via `/csc/v2/signatures/signDocument`
3. **PAdES Format**: PDF Advanced Electronic Signatures with timestamp

## Actuator Endpoints

- `/actuator/health` - Health check
- `/actuator/health/camel` - Camel health status
- `/actuator/camelroutes` - List all Camel routes
- `/actuator/metrics` - Application metrics

## License

MIT License

## Contact

Maintained by wpanther (rabbit_roger@yahoo.com)
