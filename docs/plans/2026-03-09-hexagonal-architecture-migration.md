# Hexagonal Architecture Canonical Alignment — pdf-signing-service

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Complete canonical hexagonal alignment for pdf-signing-service by relocating misplaced classes to their canonical packages with zero logic changes.

**Architecture:** Pure package rename/relocation across 5 phases: (1) move Kafka DTOs + split/merge ports and usecase, (2) consolidate CSC Feign clients and PDF utilities into their owning adapter packages, (3) move Feign config to concern sub-package, (4) relocate test files, (5) verify. Each phase ends with a green `mvn test` and a commit.

**Tech Stack:** Java 21, Spring Boot 3.2.5, Apache Camel 4.14.4, Maven, JaCoCo 90% line coverage

---

## Context for the Implementer

Read the design doc before starting:
`docs/plans/2026-03-09-hexagonal-architecture-migration-design.md`

The service already has a hexagonal structure. These tasks complete **canonical alignment** only — every step is a package declaration change + import update. No business logic, no new classes, no test logic changes.

**Base source path:** `src/main/java/com/wpanther/pdfsigning/`
**Base test path:** `src/test/java/com/wpanther/pdfsigning/`
**Root package:** `com.wpanther.pdfsigning`

**How to run tests:**
```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

**How to run full coverage check:**
```bash
mvn verify
```

---

## Task 1: Move Kafka Event DTOs from `domain/event/` to `application/dto/event/`

**Design doc ref:** Phase 1 (partial) — Kafka wire DTOs are not domain events; they belong in `application/dto/event/`.

**Files to move (change package declaration only):**
- Move: `src/main/java/com/wpanther/pdfsigning/domain/event/ProcessPdfSigningCommand.java`
- Move: `src/main/java/com/wpanther/pdfsigning/domain/event/CompensatePdfSigningCommand.java`
- Move: `src/main/java/com/wpanther/pdfsigning/domain/event/PdfSigningReplyEvent.java`
- Move: `src/main/java/com/wpanther/pdfsigning/domain/event/PdfSignedNotificationEvent.java`
- Move: `src/main/java/com/wpanther/pdfsigning/domain/event/PdfSigningFailedNotificationEvent.java`

**Files that import from `domain.event` (update imports):**
- `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`

### Step 1: Create the new package directory

```bash
mkdir -p src/main/java/com/wpanther/pdfsigning/application/dto/event
```

### Step 2: Move the five DTO files and update their package declarations

For each of the five files, move it to the new directory and change the first line:

**Old first line (all five files):**
```java
package com.wpanther.pdfsigning.domain.event;
```

**New first line (all five files):**
```java
package com.wpanther.pdfsigning.application.dto.event;
```

Files to create in `src/main/java/com/wpanther/pdfsigning/application/dto/event/`:
- `ProcessPdfSigningCommand.java` — copy from `domain/event/`, update package declaration
- `CompensatePdfSigningCommand.java` — copy from `domain/event/`, update package declaration
- `PdfSigningReplyEvent.java` — copy from `domain/event/`, update package declaration
- `PdfSignedNotificationEvent.java` — copy from `domain/event/`, update package declaration
- `PdfSigningFailedNotificationEvent.java` — copy from `domain/event/`, update package declaration

Then delete the originals:
```bash
rm -rf src/main/java/com/wpanther/pdfsigning/domain/event
```

### Step 3: Update imports in `SagaCommandHandler.java`

In `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`, replace all imports of the form:
```java
import com.wpanther.pdfsigning.domain.event.
```
with:
```java
import com.wpanther.pdfsigning.application.dto.event.
```

### Step 4: Update imports in `SagaRouteConfig.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`, replace all imports of the form:
```java
import com.wpanther.pdfsigning.domain.event.
```
with:
```java
import com.wpanther.pdfsigning.application.dto.event.
```

### Step 5: Update imports in `OutboxSagaReplyAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`, replace all imports of the form:
```java
import com.wpanther.pdfsigning.domain.event.
```
with:
```java
import com.wpanther.pdfsigning.application.dto.event.
```

### Step 6: Update imports in `OutboxPdfSignedEventAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`, replace all imports of the form:
```java
import com.wpanther.pdfsigning.domain.event.
```
with:
```java
import com.wpanther.pdfsigning.application.dto.event.
```

### Step 7: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

Expected: `BUILD SUCCESS` — all tests pass.

### Step 8: Commit

```bash
git add src/main/java/com/wpanther/pdfsigning/application/dto/
git add src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java
git add src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java
git add src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/
git add src/main/java/com/wpanther/pdfsigning/domain/
git commit -m "Move Kafka event DTOs from domain/event to application/dto/event"
```

---

## Task 2: Split `domain/port/out/`, Move `domain/port/in/`, Merge into `application/usecase/`

**Design doc ref:** Phase 1 (complete) — `SignedPdfDocumentRepository` → `domain/repository/`; non-repository ports → `application/port/out/`; inbound port + services → `application/usecase/`.

**Files to create:**
- `src/main/java/com/wpanther/pdfsigning/domain/repository/SignedPdfDocumentRepository.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/DocumentDownloadPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/DocumentStoragePort.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/PdfGenerationPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/SigningPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/PdfSagaReplyPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/port/out/PdfSignedEventPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/usecase/SagaCommandPort.java`
- `src/main/java/com/wpanther/pdfsigning/application/usecase/DomainPdfSigningService.java`
- `src/main/java/com/wpanther/pdfsigning/application/usecase/SagaCommandHandler.java`

**Files to delete after moves:**
- `src/main/java/com/wpanther/pdfsigning/domain/port/` (entire directory)
- `src/main/java/com/wpanther/pdfsigning/domain/service/` (entire directory)
- `src/main/java/com/wpanther/pdfsigning/application/service/` (entire directory)

**Files that need import updates:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/CscSigningAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/download/HttpDocumentDownloadAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/PadesSignatureAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/storage/LocalStorageAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/storage/S3StorageAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/SignedPdfDocumentRepositoryAdapter.java`

### Step 1: Create new package directories

```bash
mkdir -p src/main/java/com/wpanther/pdfsigning/domain/repository
mkdir -p src/main/java/com/wpanther/pdfsigning/application/port/out
mkdir -p src/main/java/com/wpanther/pdfsigning/application/usecase
```

### Step 2: Move `SignedPdfDocumentRepository` → `domain/repository/`

Copy `domain/port/out/SignedPdfDocumentRepository.java` to `domain/repository/SignedPdfDocumentRepository.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.domain.port.out;

// New:
package com.wpanther.pdfsigning.domain.repository;
```

### Step 3: Move six non-repository ports → `application/port/out/`

For each of the following files, copy from `domain/port/out/` to `application/port/out/` and change the package declaration:

Files: `DocumentDownloadPort.java`, `DocumentStoragePort.java`, `PdfGenerationPort.java`, `SigningPort.java`, `PdfSagaReplyPort.java`, `PdfSignedEventPort.java`

**Old package (all six):**
```java
package com.wpanther.pdfsigning.domain.port.out;
```

**New package (all six):**
```java
package com.wpanther.pdfsigning.application.port.out;
```

### Step 4: Move `SagaCommandPort` → `application/usecase/`

Copy `domain/port/in/SagaCommandPort.java` to `application/usecase/SagaCommandPort.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.domain.port.in;

// New:
package com.wpanther.pdfsigning.application.usecase;
```

### Step 5: Move `DomainPdfSigningService` → `application/usecase/`

Copy `domain/service/DomainPdfSigningService.java` to `application/usecase/DomainPdfSigningService.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.domain.service;

// New:
package com.wpanther.pdfsigning.application.usecase;
```

Also update all imports inside `DomainPdfSigningService.java` that reference `domain.port.out.*`:
```java
// Old imports (replace all):
import com.wpanther.pdfsigning.domain.port.out.

// New imports:
import com.wpanther.pdfsigning.application.port.out.
```

And update the import for `SignedPdfDocumentRepository`:
```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;

// New:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
```

### Step 6: Move `SagaCommandHandler` → `application/usecase/`

Copy `application/service/SagaCommandHandler.java` to `application/usecase/SagaCommandHandler.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.application.service;

// New:
package com.wpanther.pdfsigning.application.usecase;
```

Also update all imports inside `SagaCommandHandler.java`:
```java
// Old — replace all:
import com.wpanther.pdfsigning.domain.port.out.

// New:
import com.wpanther.pdfsigning.application.port.out.
```

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;

// New:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
```

```java
// Old:
import com.wpanther.pdfsigning.domain.port.in.SagaCommandPort;

// New:
import com.wpanther.pdfsigning.application.usecase.SagaCommandPort;
```

```java
// Old:
import com.wpanther.pdfsigning.domain.service.DomainPdfSigningService;

// New:
import com.wpanther.pdfsigning.application.usecase.DomainPdfSigningService;
```

### Step 7: Delete the old source directories

```bash
rm -rf src/main/java/com/wpanther/pdfsigning/domain/port
rm -rf src/main/java/com/wpanther/pdfsigning/domain/service
rm -rf src/main/java/com/wpanther/pdfsigning/application/service
```

### Step 8: Update `SagaRouteConfig.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/in/camel/SagaRouteConfig.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.in.SagaCommandPort;

// New:
import com.wpanther.pdfsigning.application.usecase.SagaCommandPort;
```

Also replace any remaining `domain.event` imports (already done in Task 1 but verify):
```java
// Should already be updated — confirm no domain.event imports remain
```

### Step 9: Update `CscSigningAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/CscSigningAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.SigningPort;

// New:
import com.wpanther.pdfsigning.application.port.out.SigningPort;
```

### Step 10: Update `HttpDocumentDownloadAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/download/HttpDocumentDownloadAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.DocumentDownloadPort;

// New:
import com.wpanther.pdfsigning.application.port.out.DocumentDownloadPort;
```

### Step 11: Update `PadesSignatureAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/PadesSignatureAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.PdfGenerationPort;

// New:
import com.wpanther.pdfsigning.application.port.out.PdfGenerationPort;
```

### Step 12: Update `LocalStorageAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/storage/LocalStorageAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.DocumentStoragePort;

// New:
import com.wpanther.pdfsigning.application.port.out.DocumentStoragePort;
```

### Step 13: Update `S3StorageAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/storage/S3StorageAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.DocumentStoragePort;

// New:
import com.wpanther.pdfsigning.application.port.out.DocumentStoragePort;
```

### Step 14: Update `OutboxSagaReplyAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxSagaReplyAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.PdfSagaReplyPort;

// New:
import com.wpanther.pdfsigning.application.port.out.PdfSagaReplyPort;
```

### Step 15: Update `OutboxPdfSignedEventAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/messaging/OutboxPdfSignedEventAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.PdfSignedEventPort;

// New:
import com.wpanther.pdfsigning.application.port.out.PdfSignedEventPort;
```

### Step 16: Update `SignedPdfDocumentRepositoryAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/SignedPdfDocumentRepositoryAdapter.java`:

```java
// Old:
import com.wpanther.pdfsigning.domain.port.out.SignedPdfDocumentRepository;

// New:
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
```

### Step 17: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

Expected: `BUILD SUCCESS` — all tests pass.

If compilation fails with "cannot find symbol", check for any remaining imports referencing `domain.port.*` or `domain.service.*` or `application.service.*`:

```bash
grep -r "domain\.port\." src/main/java/ --include="*.java"
grep -r "domain\.service\." src/main/java/ --include="*.java"
grep -r "application\.service\." src/main/java/ --include="*.java"
```

All results should be empty.

### Step 18: Commit

```bash
git add src/main/java/com/wpanther/pdfsigning/domain/repository/
git add src/main/java/com/wpanther/pdfsigning/application/port/
git add src/main/java/com/wpanther/pdfsigning/application/usecase/
git add src/main/java/com/wpanther/pdfsigning/domain/
git add src/main/java/com/wpanther/pdfsigning/application/
git add src/main/java/com/wpanther/pdfsigning/infrastructure/
git commit -m "Split domain ports, merge domain/port/in + domain/service + application/service into application/usecase"
```

---

## Task 3: Consolidate CSC Feign Clients into `infrastructure/adapter/out/csc/`

**Design doc ref:** Phase 2 (partial) — `infrastructure/client/csc/` and `infrastructure/client/csc/dto/` move alongside `CscSigningAdapter`.

**Files to move:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/CSCApiClient.java` → `infrastructure/adapter/out/csc/client/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/CSCAuthClient.java` → `infrastructure/adapter/out/csc/client/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/SadTokenValidator.java` → `infrastructure/adapter/out/csc/client/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/dto/CSCAuthorizeRequest.java` → `infrastructure/adapter/out/csc/dto/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/dto/CSCAuthorizeResponse.java` → `infrastructure/adapter/out/csc/dto/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/dto/CSCSignatureRequest.java` → `infrastructure/adapter/out/csc/dto/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/client/csc/dto/CSCSignatureResponse.java` → `infrastructure/adapter/out/csc/dto/`

**Files that need import updates:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/CscSigningAdapter.java`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/config/FeignConfig.java`

### Step 1: Create new package directories

```bash
mkdir -p src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/client
mkdir -p src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/dto
```

### Step 2: Move Feign client files → `adapter/out/csc/client/`

For each of `CSCApiClient.java`, `CSCAuthClient.java`, `SadTokenValidator.java`:

Copy from `infrastructure/client/csc/` to `infrastructure/adapter/out/csc/client/` and change the package declaration:

```java
// Old:
package com.wpanther.pdfsigning.infrastructure.client.csc;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client;
```

### Step 3: Move CSC DTO files → `adapter/out/csc/dto/`

For each of `CSCAuthorizeRequest.java`, `CSCAuthorizeResponse.java`, `CSCSignatureRequest.java`, `CSCSignatureResponse.java`:

Copy from `infrastructure/client/csc/dto/` to `infrastructure/adapter/out/csc/dto/` and change the package declaration:

```java
// Old:
package com.wpanther.pdfsigning.infrastructure.client.csc.dto;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto;
```

### Step 4: Delete the old client directory

```bash
rm -rf src/main/java/com/wpanther/pdfsigning/infrastructure/client
```

### Step 5: Update `CscSigningAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/CscSigningAdapter.java`, replace all `infrastructure.client.csc` imports:

```java
// Old — replace all occurrences of this prefix:
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.SadTokenValidator;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCSignatureResponse;

// New:
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.SadTokenValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeRequest;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeResponse;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureRequest;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCSignatureResponse;
```

### Step 6: Update `FeignConfig.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/config/FeignConfig.java`, replace `infrastructure.client.csc` imports:

```java
// Old:
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.client.csc.CSCAuthClient;

// New:
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCApiClient;
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.CSCAuthClient;
```

### Step 7: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

Sanity check — no remaining old client references:
```bash
grep -r "infrastructure\.client\." src/main/java/ --include="*.java"
```

Should return empty.

### Step 8: Commit

```bash
git add src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/
git add src/main/java/com/wpanther/pdfsigning/infrastructure/config/FeignConfig.java
git add src/main/java/com/wpanther/pdfsigning/infrastructure/client/
git commit -m "Consolidate CSC Feign clients and DTOs into infrastructure/adapter/out/csc"
```

---

## Task 4: Consolidate PDF Utilities into `infrastructure/adapter/out/pdf/`

**Design doc ref:** Phase 2 (complete) — `infrastructure/pdf/` utilities move alongside `PadesSignatureAdapter`.

**Files to move:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/pdf/CertificateParser.java` → `infrastructure/adapter/out/pdf/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/pdf/CertificateValidator.java` → `infrastructure/adapter/out/pdf/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/pdf/PadesSignatureEmbedder.java` → `infrastructure/adapter/out/pdf/`

**Files that need import updates:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/PadesSignatureAdapter.java`

### Step 1: Move PDF utility files → `adapter/out/pdf/`

For each of `CertificateParser.java`, `CertificateValidator.java`, `PadesSignatureEmbedder.java`:

Copy from `infrastructure/pdf/` to `infrastructure/adapter/out/pdf/` and change the package declaration:

```java
// Old:
package com.wpanther.pdfsigning.infrastructure.pdf;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;
```

### Step 2: Delete the old pdf directory

```bash
rm -rf src/main/java/com/wpanther/pdfsigning/infrastructure/pdf
```

### Step 3: Update `PadesSignatureAdapter.java`

In `src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/PadesSignatureAdapter.java`, replace all `infrastructure.pdf` imports:

```java
// Old:
import com.wpanther.pdfsigning.infrastructure.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.pdf.CertificateValidator;
import com.wpanther.pdfsigning.infrastructure.pdf.PadesSignatureEmbedder;

// New:
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateParser;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.CertificateValidator;
import com.wpanther.pdfsigning.infrastructure.adapter.out.pdf.PadesSignatureEmbedder;
```

Note: since `PadesSignatureAdapter.java` is in the same package (`infrastructure.adapter.out.pdf`), these imports may be unnecessary after the move — Java classes in the same package don't need imports. **Remove** these three import lines entirely if `PadesSignatureAdapter` is in the same package.

### Step 4: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

Sanity check:
```bash
grep -r "infrastructure\.pdf\." src/main/java/ --include="*.java"
```

Should return empty.

### Step 5: Commit

```bash
git add src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/
git add src/main/java/com/wpanther/pdfsigning/infrastructure/pdf/
git commit -m "Consolidate PDF utilities into infrastructure/adapter/out/pdf"
```

---

## Task 5: Move Feign Config to `infrastructure/config/feign/`

**Design doc ref:** Phase 3 — `FeignConfig` and `CSCErrorDecoder` move to concern sub-package.

**Files to move:**
- `src/main/java/com/wpanther/pdfsigning/infrastructure/config/FeignConfig.java` → `infrastructure/config/feign/`
- `src/main/java/com/wpanther/pdfsigning/infrastructure/config/CSCErrorDecoder.java` → `infrastructure/config/feign/`

**Files that need import updates:** None — Spring `@Configuration` classes are discovered by component scan, not imported by other classes. Verify with:

```bash
grep -r "infrastructure\.config\.FeignConfig\|infrastructure\.config\.CSCErrorDecoder" src/main/java/ --include="*.java"
```

### Step 1: Create the new feign config directory

```bash
mkdir -p src/main/java/com/wpanther/pdfsigning/infrastructure/config/feign
```

### Step 2: Move `FeignConfig.java` → `config/feign/`

Copy `infrastructure/config/FeignConfig.java` to `infrastructure/config/feign/FeignConfig.java` and change the package declaration:

```java
// Old:
package com.wpanther.pdfsigning.infrastructure.config;

// New:
package com.wpanther.pdfsigning.infrastructure.config.feign;
```

Then delete the original:
```bash
rm src/main/java/com/wpanther/pdfsigning/infrastructure/config/FeignConfig.java
```

### Step 3: Move `CSCErrorDecoder.java` → `config/feign/`

Copy `infrastructure/config/CSCErrorDecoder.java` to `infrastructure/config/feign/CSCErrorDecoder.java` and change the package declaration:

```java
// Old:
package com.wpanther.pdfsigning.infrastructure.config;

// New:
package com.wpanther.pdfsigning.infrastructure.config.feign;
```

Then delete the original:
```bash
rm src/main/java/com/wpanther/pdfsigning/infrastructure/config/CSCErrorDecoder.java
```

### Step 4: Verify compilation and tests pass

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn test
```

Expected: `BUILD SUCCESS`.

### Step 5: Commit

```bash
git add src/main/java/com/wpanther/pdfsigning/infrastructure/config/
git commit -m "Move Feign config to infrastructure/config/feign sub-package"
```

---

## Task 6: Relocate Test Files

**Design doc ref:** Phase 4 — mirror source package moves in test tree; update JaCoCo exclusions.

**Test files to relocate (package declaration + imports only):**

| Old test path | New test path |
|---|---|
| `domain/event/ProcessPdfSigningCommandTest.java` | `application/dto/event/` |
| `domain/event/PdfSigningReplyEventTest.java` | `application/dto/event/` |
| `domain/event/PdfSignedNotificationEventTest.java` | `application/dto/event/` |
| `domain/service/DomainPdfSigningServiceTest.java` | `application/usecase/` |
| `application/service/SagaCommandHandlerTest.java` | `application/usecase/` |
| `infrastructure/client/csc/SadTokenValidatorTest.java` | `infrastructure/adapter/out/csc/client/` |
| `infrastructure/client/csc/dto/CSCDtoTest.java` | `infrastructure/adapter/out/csc/dto/` |
| `infrastructure/pdf/CertificateParserTest.java` | `infrastructure/adapter/out/pdf/` |
| `infrastructure/pdf/CertificateValidatorTest.java` | `infrastructure/adapter/out/pdf/` |
| `infrastructure/pdf/PadesSignatureEmbedderTest.java` | `infrastructure/adapter/out/pdf/` |

### Step 1: Create new test package directories

```bash
mkdir -p src/test/java/com/wpanther/pdfsigning/application/dto/event
mkdir -p src/test/java/com/wpanther/pdfsigning/application/usecase
mkdir -p src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/client
mkdir -p src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/dto
```

Note: `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/` already exists (PadesSignatureAdapterTest is there).

### Step 2: Relocate domain/event tests → `application/dto/event/`

For each of `ProcessPdfSigningCommandTest.java`, `PdfSigningReplyEventTest.java`, `PdfSignedNotificationEventTest.java`:

Copy from `src/test/java/com/wpanther/pdfsigning/domain/event/` to `src/test/java/com/wpanther/pdfsigning/application/dto/event/`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.domain.event;

// New:
package com.wpanther.pdfsigning.application.dto.event;
```

Update the imports for the tested classes:
```java
// Old:
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
// (and similar for other event classes)

// New:
import com.wpanther.pdfsigning.application.dto.event.ProcessPdfSigningCommand;
// (and similar)
```

Delete old test files:
```bash
rm -rf src/test/java/com/wpanther/pdfsigning/domain/event
```

### Step 3: Relocate `DomainPdfSigningServiceTest` → `application/usecase/`

Copy `src/test/java/com/wpanther/pdfsigning/domain/service/DomainPdfSigningServiceTest.java` to `src/test/java/com/wpanther/pdfsigning/application/usecase/DomainPdfSigningServiceTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.domain.service;

// New:
package com.wpanther.pdfsigning.application.usecase;
```

Update the import for the tested class:
```java
// Old:
import com.wpanther.pdfsigning.domain.service.DomainPdfSigningService;

// New:
import com.wpanther.pdfsigning.application.usecase.DomainPdfSigningService;
```

Also update imports for any port interfaces used as mocks:
```java
// Old (any domain.port.out.* imports):
import com.wpanther.pdfsigning.domain.port.out.SigningPort;
// etc.

// New:
import com.wpanther.pdfsigning.application.port.out.SigningPort;
// etc.
```

Delete the old test file and its directory:
```bash
rm -rf src/test/java/com/wpanther/pdfsigning/domain/service
```

### Step 4: Relocate `SagaCommandHandlerTest` → `application/usecase/`

Copy `src/test/java/com/wpanther/pdfsigning/application/service/SagaCommandHandlerTest.java` to `src/test/java/com/wpanther/pdfsigning/application/usecase/SagaCommandHandlerTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.application.service;

// New:
package com.wpanther.pdfsigning.application.usecase;
```

Update the import for the tested class:
```java
// Old:
import com.wpanther.pdfsigning.application.service.SagaCommandHandler;

// New:
import com.wpanther.pdfsigning.application.usecase.SagaCommandHandler;
```

Update any port imports from `domain.port.out.*` → `application.port.out.*` and `domain.event.*` → `application.dto.event.*` (should already be done in Tasks 1–2, but verify).

Delete the old test file and directory:
```bash
rm -rf src/test/java/com/wpanther/pdfsigning/application/service
```

### Step 5: Relocate `SadTokenValidatorTest` → `infrastructure/adapter/out/csc/client/`

Copy `src/test/java/com/wpanther/pdfsigning/infrastructure/client/csc/SadTokenValidatorTest.java` to `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/client/SadTokenValidatorTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.infrastructure.client.csc;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client;
```

Update the import for the tested class:
```java
// Old:
import com.wpanther.pdfsigning.infrastructure.client.csc.SadTokenValidator;

// New:
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.client.SadTokenValidator;
```

### Step 6: Relocate `CSCDtoTest` → `infrastructure/adapter/out/csc/dto/`

Copy `src/test/java/com/wpanther/pdfsigning/infrastructure/client/csc/dto/CSCDtoTest.java` to `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/dto/CSCDtoTest.java`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.infrastructure.client.csc.dto;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto;
```

Update imports for tested DTO classes:
```java
// Old:
import com.wpanther.pdfsigning.infrastructure.client.csc.dto.CSCAuthorizeRequest;
// etc.

// New:
import com.wpanther.pdfsigning.infrastructure.adapter.out.csc.dto.CSCAuthorizeRequest;
// etc.
```

Delete old test directories:
```bash
rm -rf src/test/java/com/wpanther/pdfsigning/infrastructure/client
```

### Step 7: Relocate PDF utility tests → `infrastructure/adapter/out/pdf/`

For each of `CertificateParserTest.java`, `CertificateValidatorTest.java`, `PadesSignatureEmbedderTest.java`:

Copy from `src/test/java/com/wpanther/pdfsigning/infrastructure/pdf/` to `src/test/java/com/wpanther/pdfsigning/infrastructure/adapter/out/pdf/`.

Change the package declaration:
```java
// Old:
package com.wpanther.pdfsigning.infrastructure.pdf;

// New:
package com.wpanther.pdfsigning.infrastructure.adapter.out.pdf;
```

Update imports for tested classes (same package after move — remove imports if they're in the same package, or update to `infrastructure.adapter.out.pdf.*`).

Delete old test directory:
```bash
rm -rf src/test/java/com/wpanther/pdfsigning/infrastructure/pdf
```

### Step 8: Update JaCoCo exclusions in `pom.xml`

Open `pom.xml` and find the JaCoCo `<excludes>` section. Apply the following changes:

**Remove these stale entries:**
```xml
<exclude>com/wpanther/pdfsigning/infrastructure/adapter/secondary/download/**</exclude>
<exclude>com/wpanther/pdfsigning/infrastructure/storage/**</exclude>
<exclude>com/wpanther/pdfsigning/infrastructure/client/csc/dto/**</exclude>
```

**Add this new entry** (replace the removed CSC DTO exclusion):
```xml
<exclude>com/wpanther/pdfsigning/infrastructure/adapter/out/csc/dto/**</exclude>
```

The exclusions that should remain unchanged:
```xml
<exclude>**/*Builder*</exclude>
<exclude>**/*Builder$*</exclude>
<exclude>com/wpanther/pdfsigning/infrastructure/config/**</exclude>
<exclude>com/wpanther/pdfsigning/infrastructure/persistence/**</exclude>
<exclude>com/wpanther/pdfsigning/PdfSigningServiceApplication.class</exclude>
```

Note: `infrastructure/config/**` now covers `infrastructure/config/feign/**` automatically — no change needed.

### Step 9: Run full coverage verification

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service
mvn verify
```

Expected: `BUILD SUCCESS` with all JaCoCo coverage checks passing (≥ 90% line coverage).

If coverage drops for a package, check that no test files were accidentally left in old locations:
```bash
find src/test/java -name "*.java" | xargs grep -l "domain\.port\.\|domain\.service\.\|application\.service\.\|infrastructure\.client\.\|infrastructure\.pdf\." 2>/dev/null
```

Should return empty.

### Step 10: Commit

```bash
git add src/test/java/com/wpanther/pdfsigning/
git add pom.xml
git commit -m "Relocate test classes, update JaCoCo exclusions"
```

---

## Task 7: Final Verification

**Design doc ref:** Phase 5 — confirm no old package references remain anywhere.

### Step 1: Confirm no old package references in source

```bash
cd /home/wpanther/projects/etax/invoice-microservices/services/pdf-signing-service

# These should all return empty:
grep -r "domain\.port\." src/ --include="*.java"
grep -r "domain\.service\." src/ --include="*.java"
grep -r "application\.service\." src/ --include="*.java"
grep -r "domain\.event\." src/ --include="*.java"
grep -r "infrastructure\.client\." src/ --include="*.java"
grep -r "infrastructure\.pdf\." src/ --include="*.java"
```

### Step 2: Confirm deleted directories are gone

```bash
# These should all print "No such file or directory":
ls src/main/java/com/wpanther/pdfsigning/domain/port 2>&1
ls src/main/java/com/wpanther/pdfsigning/domain/service 2>&1
ls src/main/java/com/wpanther/pdfsigning/domain/event 2>&1
ls src/main/java/com/wpanther/pdfsigning/application/service 2>&1
ls src/main/java/com/wpanther/pdfsigning/infrastructure/client 2>&1
ls src/main/java/com/wpanther/pdfsigning/infrastructure/pdf 2>&1
ls src/test/java/com/wpanther/pdfsigning/domain/event 2>&1
ls src/test/java/com/wpanther/pdfsigning/domain/service 2>&1
ls src/test/java/com/wpanther/pdfsigning/application/service 2>&1
ls src/test/java/com/wpanther/pdfsigning/infrastructure/client 2>&1
ls src/test/java/com/wpanther/pdfsigning/infrastructure/pdf 2>&1
```

### Step 3: Confirm new directories exist

```bash
# These should all list files:
ls src/main/java/com/wpanther/pdfsigning/domain/repository/
ls src/main/java/com/wpanther/pdfsigning/application/usecase/
ls src/main/java/com/wpanther/pdfsigning/application/port/out/
ls src/main/java/com/wpanther/pdfsigning/application/dto/event/
ls src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/client/
ls src/main/java/com/wpanther/pdfsigning/infrastructure/adapter/out/csc/dto/
ls src/main/java/com/wpanther/pdfsigning/infrastructure/config/feign/
```

### Step 4: Final full build and coverage check

```bash
mvn verify
```

Expected: `BUILD SUCCESS` — all tests pass, all JaCoCo coverage thresholds met (≥ 90% line coverage per package).
