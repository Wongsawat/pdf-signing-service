# Hexagonal Architecture Migration Design (Canonical Alignment)

**Date:** 2026-03-09
**Service:** pdf-signing-service (port 8087)
**Type:** Pure refactor — package rename + relocation, no logic changes
**Strategy:** Phase-by-phase incremental (one commit per logical group, tests green after each)

---

## Context

The pdf-signing-service completed an initial hexagonal migration (commit `aa407d5`): adapter/in/out split, outbound port interfaces, outbox pattern, and Camel route injection are all in place. This migration completes **canonical alignment** with the layout established across all other services (invoice-pdf, taxinvoice-pdf, ebms-sending, notification, orchestrator, document-intake, document-storage):

- `domain/` ← `application/` ← `infrastructure/` (strict dependency rule)
- `application/usecase/` for inbound port interfaces and their implementations
- `domain/repository/` for domain-owned repository ports
- `application/port/out/` for non-domain outbound ports
- `application/dto/event/` for Kafka wire DTOs (not domain events)
- `infrastructure/adapter/out/<concern>/` owns its Feign clients and utilities
- `infrastructure/config/` with concern-based sub-packages

**Remaining gaps:**

| Current | Target | Change |
|---|---|---|
| `domain/port/in/SagaCommandPort` | `application/usecase/` | Move — inbound port belongs in application |
| `domain/port/out/SignedPdfDocumentRepository` | `domain/repository/` | Move — repository is domain-owned |
| `domain/port/out/{DocumentDownloadPort, DocumentStoragePort, PdfGenerationPort, SigningPort, PdfSagaReplyPort, PdfSignedEventPort}` | `application/port/out/` | Move — application-layer contracts |
| `domain/event/*` (5 classes) | `application/dto/event/` | Move — Kafka wire DTOs, not domain events |
| `domain/service/DomainPdfSigningService` | `application/usecase/` | Move — orchestrates via ports (application concern) |
| `application/service/SagaCommandHandler` | `application/usecase/` | Move — merge into usecase package |
| `infrastructure/client/csc/` | `infrastructure/adapter/out/csc/client/` + `dto/` | Consolidate alongside CscSigningAdapter |
| `infrastructure/pdf/` (3 utility classes) | `infrastructure/adapter/out/pdf/` | Consolidate alongside PadesSignatureAdapter |
| `infrastructure/config/` flat | `infrastructure/config/feign/` + `properties/` (existing) | Add feign sub-package |

---

## Target Package Structure

```
com.wpanther.pdfsigning/
├── domain/
│   ├── model/                              # UNCHANGED
│   │   ├── SignedPdfDocument.java
│   │   ├── SignedPdfDocumentId.java
│   │   ├── SigningStatus.java
│   │   ├── PadesLevel.java
│   │   ├── SigningException.java
│   │   └── StorageException.java
│   └── repository/                         # NEW — moved from domain/port/out/
│       └── SignedPdfDocumentRepository.java
│   # domain/port/    FULLY REMOVED
│   # domain/event/   FULLY REMOVED
│   # domain/service/ FULLY REMOVED
│
├── application/
│   ├── usecase/                            # MERGED: domain/port/in/ + domain/service/ + application/service/
│   │   ├── SagaCommandPort.java            # MOVED from domain/port/in/
│   │   ├── SagaCommandHandler.java         # MOVED from application/service/
│   │   └── DomainPdfSigningService.java    # MOVED from domain/service/
│   ├── port/out/                           # MOVED from domain/port/out/ (non-repository)
│   │   ├── DocumentDownloadPort.java
│   │   ├── DocumentStoragePort.java
│   │   ├── PdfGenerationPort.java
│   │   ├── SigningPort.java
│   │   ├── PdfSagaReplyPort.java
│   │   └── PdfSignedEventPort.java
│   └── dto/
│       └── event/                          # MOVED from domain/event/
│           ├── ProcessPdfSigningCommand.java
│           ├── CompensatePdfSigningCommand.java
│           ├── PdfSigningReplyEvent.java
│           ├── PdfSignedNotificationEvent.java
│           └── PdfSigningFailedNotificationEvent.java
│
└── infrastructure/
    ├── adapter/
    │   ├── in/
    │   │   └── camel/                      # UNCHANGED
    │   │       └── SagaRouteConfig.java
    │   └── out/
    │       ├── csc/                        # EXPANDED — absorbs infrastructure/client/csc/
    │       │   ├── CscSigningAdapter.java  # UNCHANGED (already here)
    │       │   ├── client/                 # MOVED from infrastructure/client/csc/
    │       │   │   ├── CSCApiClient.java
    │       │   │   ├── CSCAuthClient.java
    │       │   │   └── SadTokenValidator.java
    │       │   └── dto/                    # MOVED from infrastructure/client/csc/dto/
    │       │       ├── CSCAuthorizeRequest.java
    │       │       ├── CSCAuthorizeResponse.java
    │       │       ├── CSCSignatureRequest.java
    │       │       └── CSCSignatureResponse.java
    │       ├── download/                   # UNCHANGED
    │       │   └── HttpDocumentDownloadAdapter.java
    │       ├── pdf/                        # EXPANDED — absorbs infrastructure/pdf/
    │       │   ├── PadesSignatureAdapter.java  # UNCHANGED
    │       │   ├── CertificateParser.java      # MOVED from infrastructure/pdf/
    │       │   ├── CertificateValidator.java   # MOVED from infrastructure/pdf/
    │       │   └── PadesSignatureEmbedder.java # MOVED from infrastructure/pdf/
    │       ├── storage/                    # UNCHANGED
    │       │   ├── LocalStorageAdapter.java
    │       │   └── S3StorageAdapter.java
    │       └── messaging/                  # UNCHANGED
    │           ├── OutboxSagaReplyAdapter.java
    │           └── OutboxPdfSignedEventAdapter.java
    ├── config/
    │   ├── feign/                          # NEW sub-package
    │   │   ├── FeignConfig.java            # MOVED from config/
    │   │   └── CSCErrorDecoder.java        # MOVED from config/
    │   └── properties/                     # UNCHANGED (already a sub-package)
    │       ├── CscProperties.java
    │       ├── KafkaProperties.java
    │       ├── PadesProperties.java
    │       ├── SigningProperties.java
    │       └── StorageProperties.java
    └── persistence/                        # UNCHANGED
        ├── SignedPdfDocumentEntity.java
        ├── SignedPdfDocumentMapper.java
        ├── SignedPdfDocumentRepositoryAdapter.java
        ├── JpaSignedPdfDocumentRepository.java
        └── outbox/
            ├── OutboxEventEntity.java
            ├── JpaOutboxEventRepository.java
            └── SpringDataOutboxRepository.java
```

---

## Component Design

### `application/usecase/` Merge

Three sources collapse into one package — only package declarations change:

- `SagaCommandPort` (interface) — `domain/port/in/` → `application/usecase/`
- `SagaCommandHandler` (implements `SagaCommandPort`) — `application/service/` → `application/usecase/`
- `DomainPdfSigningService` (orchestrates signing via ports) — `domain/service/` → `application/usecase/`

`SagaRouteConfig` injects `SagaCommandPort` — only its import changes.

After the move `domain/port/`, `domain/service/`, and `application/service/` are fully deleted.

### `domain/repository/` Split from `domain/port/out/`

`SignedPdfDocumentRepository` is a domain-owned contract (pure persistence, no I/O side effects). It moves to `domain/repository/`. All other ports are application-layer contracts and move to `application/port/out/`:

| Port | Destination | Rationale |
|---|---|---|
| `SignedPdfDocumentRepository` | `domain/repository/` | Repository — domain owns this contract |
| `DocumentDownloadPort` | `application/port/out/` | HTTP I/O — application concern |
| `DocumentStoragePort` | `application/port/out/` | File/S3 I/O — application concern |
| `PdfGenerationPort` | `application/port/out/` | PDF byte-range — application concern |
| `SigningPort` | `application/port/out/` | CSC API — application concern |
| `PdfSagaReplyPort` | `application/port/out/` | Outbox messaging — application concern |
| `PdfSignedEventPort` | `application/port/out/` | Outbox messaging — application concern |

`DomainPdfSigningService` (now in `application/usecase/`) imports both `domain/repository/` and `application/port/out/` — both legal from the application layer.

### Kafka DTO Relocation (`domain/event/` → `application/dto/event/`)

All five classes are Kafka wire DTOs (extends `SagaCommand`, `SagaReply`, `TraceEvent`). They are serialization contracts, not domain events. After the move, `domain/event/` is empty and deleted.

**Import chain after move:**
- `SagaCommandHandler` → `application.dto.event.*`
- `SagaRouteConfig` → `application.dto.event.*`
- `OutboxSagaReplyAdapter` → `application.dto.event.*`
- `OutboxPdfSignedEventAdapter` → `application.dto.event.*`

### CSC Consolidation (`infrastructure/client/csc/` → `infrastructure/adapter/out/csc/`)

`CscSigningAdapter` already lives in `infrastructure/adapter/out/csc/`. The Feign clients and DTOs it depends on move alongside it in `client/` and `dto/` sub-packages. After the move, `infrastructure/client/` is fully deleted.

`CscSigningAdapter` import changes:
- `infrastructure.client.csc.CSCApiClient` → `infrastructure.adapter.out.csc.client.CSCApiClient`
- `infrastructure.client.csc.CSCAuthClient` → `infrastructure.adapter.out.csc.client.CSCAuthClient`
- `infrastructure.client.csc.SadTokenValidator` → `infrastructure.adapter.out.csc.client.SadTokenValidator`
- `infrastructure.client.csc.dto.*` → `infrastructure.adapter.out.csc.dto.*`

`PdfSigningServiceApplication` has `@EnableFeignClients` with root package scan — covers the new location automatically.

`FeignConfig` (now in `infrastructure/config/feign/`) references `CSCApiClient` and `CSCAuthClient` — its imports update accordingly.

### PDF Utility Consolidation (`infrastructure/pdf/` → `infrastructure/adapter/out/pdf/`)

`CertificateParser`, `CertificateValidator`, and `PadesSignatureEmbedder` are used exclusively by `PadesSignatureAdapter`. Co-locating them removes the `infrastructure/pdf/` orphan package. After the move, `infrastructure/pdf/` is fully deleted.

### Config Sub-Packages

`FeignConfig` and `CSCErrorDecoder` move to `infrastructure/config/feign/`. The `properties/` sub-package stays untouched.

---

## Dependency Rules

| Package | May import from | Must NOT import from |
|---|---|---|
| `domain/model/` | stdlib, Lombok | application/, infrastructure/ |
| `domain/repository/` | `domain/model/` | application/, infrastructure/ |
| `application/usecase/` | `domain/`, `application/port/out/`, `application/dto/` | infrastructure/ |
| `application/port/out/` | `domain/model/`, `application/dto/` | infrastructure/ |
| `application/dto/event/` | stdlib, Jackson, saga-commons | domain/, infrastructure/ |
| `infrastructure/adapter/in/` | `application/usecase/`, `application/dto/` | `infrastructure/adapter/out/` directly |
| `infrastructure/adapter/out/` | `application/port/out/`, `domain/`, `application/dto/` | `infrastructure/adapter/in/` |
| `infrastructure/config/` | everything (Spring wiring — allowed) | — |

---

## Data Flow

### Inbound: Saga Command

```
saga.command.pdf-signing (Kafka)
  → infrastructure/adapter/in/camel/SagaRouteConfig
  → application/usecase/SagaCommandPort
  → application/usecase/SagaCommandHandler
      ├── [TX1] SignedPdfDocumentRepositoryAdapter.save()  PENDING→SIGNING
      ├── application/port/out/DocumentDownloadPort        download unsigned PDF
      ├── application/usecase/DomainPdfSigningService
      │     ├── application/port/out/PdfGenerationPort     compute byte-range digest
      │     ├── application/port/out/SigningPort            CSC API → CMS signature bytes
      │     └── application/port/out/PdfGenerationPort     embed signature → signed PDF
      ├── application/port/out/DocumentStoragePort         store signed PDF → URL
      └── [TX2] repository.save() SIGNING→COMPLETED
              + outbox → saga.reply.pdf-signing (SUCCESS)
              + outbox → notification.events
```

### Inbound: Compensation

```
saga.compensation.pdf-signing (Kafka)
  → infrastructure/adapter/in/camel/SagaRouteConfig
  → application/usecase/SagaCommandPort
  → application/usecase/SagaCommandHandler.handleCompensation()
      ├── [TX] repository.deleteById() + flush
      ├── DocumentStoragePort.delete() (best-effort)
      └── [TX] outbox → saga.reply.pdf-signing (COMPENSATED)
```

### Outbound: CSC PAdES Signing

```
application/usecase/DomainPdfSigningService
  → application/port/out/SigningPort
      ↓ implemented by
  infrastructure/adapter/out/csc/CscSigningAdapter
      ├── client/CSCAuthClient     → /csc/v2/oauth2/authorize  (SAD token)
      ├── client/SadTokenValidator → validate expiry
      ├── client/CSCApiClient      → /csc/v2/signatures/signHash
      └── dto/{CSCAuthorizeRequest, CSCSignatureRequest, ...}
```

### Outbound: PDF Byte-Range + Embed

```
application/usecase/DomainPdfSigningService
  → application/port/out/PdfGenerationPort
      ↓ implemented by
  infrastructure/adapter/out/pdf/PadesSignatureAdapter
      ├── CertificateParser     → parse PEM chain
      ├── CertificateValidator  → validate X.509 expiry + validity
      └── PadesSignatureEmbedder → embed CMS/PKCS#7 into PDF byte range
```

### Outbound: Reply + Notification (via Outbox)

```
application/usecase/SagaCommandHandler
  → application/port/out/PdfSagaReplyPort
      ↓ implemented by
  infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter
      → outbox_events → Debezium CDC → saga.reply.pdf-signing

  → application/port/out/PdfSignedEventPort
      ↓ implemented by
  infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter
      → outbox_events → Debezium CDC → notification.events
```

---

## Import Mapping (Old → New)

| Old import | New import |
|---|---|
| `domain.port.in.SagaCommandPort` | `application.usecase.SagaCommandPort` |
| `domain.port.out.SignedPdfDocumentRepository` | `domain.repository.SignedPdfDocumentRepository` |
| `domain.port.out.DocumentDownloadPort` | `application.port.out.DocumentDownloadPort` |
| `domain.port.out.DocumentStoragePort` | `application.port.out.DocumentStoragePort` |
| `domain.port.out.PdfGenerationPort` | `application.port.out.PdfGenerationPort` |
| `domain.port.out.SigningPort` | `application.port.out.SigningPort` |
| `domain.port.out.PdfSagaReplyPort` | `application.port.out.PdfSagaReplyPort` |
| `domain.port.out.PdfSignedEventPort` | `application.port.out.PdfSignedEventPort` |
| `domain.event.ProcessPdfSigningCommand` | `application.dto.event.ProcessPdfSigningCommand` |
| `domain.event.CompensatePdfSigningCommand` | `application.dto.event.CompensatePdfSigningCommand` |
| `domain.event.PdfSigningReplyEvent` | `application.dto.event.PdfSigningReplyEvent` |
| `domain.event.PdfSignedNotificationEvent` | `application.dto.event.PdfSignedNotificationEvent` |
| `domain.event.PdfSigningFailedNotificationEvent` | `application.dto.event.PdfSigningFailedNotificationEvent` |
| `domain.service.DomainPdfSigningService` | `application.usecase.DomainPdfSigningService` |
| `application.service.SagaCommandHandler` | `application.usecase.SagaCommandHandler` |
| `infrastructure.client.csc.CSCApiClient` | `infrastructure.adapter.out.csc.client.CSCApiClient` |
| `infrastructure.client.csc.CSCAuthClient` | `infrastructure.adapter.out.csc.client.CSCAuthClient` |
| `infrastructure.client.csc.SadTokenValidator` | `infrastructure.adapter.out.csc.client.SadTokenValidator` |
| `infrastructure.client.csc.dto.*` | `infrastructure.adapter.out.csc.dto.*` |
| `infrastructure.pdf.CertificateParser` | `infrastructure.adapter.out.pdf.CertificateParser` |
| `infrastructure.pdf.CertificateValidator` | `infrastructure.adapter.out.pdf.CertificateValidator` |
| `infrastructure.pdf.PadesSignatureEmbedder` | `infrastructure.adapter.out.pdf.PadesSignatureEmbedder` |
| `infrastructure.config.FeignConfig` | `infrastructure.config.feign.FeignConfig` |
| `infrastructure.config.CSCErrorDecoder` | `infrastructure.config.feign.CSCErrorDecoder` |

---

## Migration Phases

| Phase | Scope | Commit message |
|---|---|---|
| 1 | Move `domain/event/` → `application/dto/event/`; move `SignedPdfDocumentRepository` → `domain/repository/`; move remaining `domain/port/out/` → `application/port/out/`; merge `domain/port/in/` + `domain/service/` + `application/service/` → `application/usecase/`; delete `domain/port/`, `domain/event/`, `domain/service/` | `Move Kafka event DTOs, split domain ports, merge into application/usecase` |
| 2 | Consolidate `infrastructure/client/csc/` → `infrastructure/adapter/out/csc/client/` + `dto/`; consolidate `infrastructure/pdf/` → `infrastructure/adapter/out/pdf/`; delete `infrastructure/client/`, `infrastructure/pdf/` | `Consolidate CSC clients and PDF utilities into adapter/out packages` |
| 3 | Move `FeignConfig` + `CSCErrorDecoder` → `infrastructure/config/feign/` | `Move Feign config to infrastructure/config/feign sub-package` |
| 4 | Relocate test files to mirror new structure; update JaCoCo exclusions | `Relocate test classes, update JaCoCo exclusions` |
| 5 | Final verification — `mvn verify`, confirm no old package references remain | (verification only) |

---

## Testing Strategy

### Test Relocations (Phase 4)

| Old test path | New test path |
|---|---|
| `domain/event/ProcessPdfSigningCommandTest` | `application/dto/event/` |
| `domain/event/PdfSigningReplyEventTest` | `application/dto/event/` |
| `domain/event/PdfSignedNotificationEventTest` | `application/dto/event/` |
| `domain/service/DomainPdfSigningServiceTest` | `application/usecase/` |
| `application/service/SagaCommandHandlerTest` | `application/usecase/` |
| `infrastructure/client/csc/SadTokenValidatorTest` | `infrastructure/adapter/out/csc/client/` |
| `infrastructure/client/csc/dto/CSCDtoTest` | `infrastructure/adapter/out/csc/dto/` |
| `infrastructure/pdf/CertificateParserTest` | `infrastructure/adapter/out/pdf/` |
| `infrastructure/pdf/CertificateValidatorTest` | `infrastructure/adapter/out/pdf/` |
| `infrastructure/pdf/PadesSignatureEmbedderTest` | `infrastructure/adapter/out/pdf/` |

**Not moved:** `domain/model/*Test`, `infrastructure/adapter/out/csc/CscSigningAdapterTest`, `infrastructure/adapter/out/download/*`, `infrastructure/adapter/out/pdf/PadesSignatureAdapterTest`, `infrastructure/adapter/out/storage/*`, `infrastructure/adapter/out/messaging/*`, `infrastructure/persistence/*`.

### JaCoCo Exclusion Updates

| Stale exclusion | Action |
|---|---|
| `infrastructure.adapter.secondary.download` | Remove (stale path — adapter already at `adapter/out/download`) |
| `infrastructure.storage.*` | Remove (no such package exists) |
| `infrastructure.client.csc.dto` | Remove — replace with `infrastructure.adapter.out.csc.dto` |

Add: `infrastructure.adapter.out.csc.dto` (Lombok-heavy wire DTOs).

### No New Tests Required

Pure package rename with no logic changes. Per moved test file: update package declaration + import statements only.

### Coverage Target

≥ 90% line coverage (`mvn verify`) maintained throughout all phases.

---

## Key Decisions

| Decision | Rationale |
|---|---|
| `domain/port/in/` renamed to `application/usecase/` | Canonical: inbound port interfaces and their implementations co-locate in `usecase/`; `port/in/` is an intermediate naming |
| `domain/service/DomainPdfSigningService` moved to `application/usecase/` | It orchestrates workflow via outbound ports; once ports move to `application/port/out/`, the service cannot stay in `domain/` without violating the dependency rule |
| `domain/port/out/SignedPdfDocumentRepository` → `domain/repository/` | Repository is a domain-owned contract; all other ports are application concerns |
| `domain/event/` fully removed | All five classes are Kafka wire DTOs (extends SagaCommand/SagaReply/TraceEvent); they are not pure domain events |
| `infrastructure/client/csc/` consolidated into `adapter/out/csc/` | Feign clients are wire-protocol details of the CSC adapter; co-location makes the adapter self-contained |
| `infrastructure/pdf/` consolidated into `adapter/out/pdf/` | CertificateParser/Validator/PadesSignatureEmbedder exist solely to support PadesSignatureAdapter; orphan package eliminated |
| `infrastructure/config/feign/` sub-package | Groups FeignConfig + CSCErrorDecoder by concern; `properties/` sub-package already exists |
