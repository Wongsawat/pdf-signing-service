# Saga Migration Plan: pdf-signing-service

> **Purpose**: Migrate pdf-signing-service from event choreography to saga orchestration pattern, following the xml-signing-service reference implementation.

> **Reference Architecture**: `/home/wpanther/projects/etax/etax-solution-architecture.md`

> **Reference Implementation**: `../xml-signing-service/`

---

## Current State (Event Choreography)

```
pdf-generation-service → pdf.generated → pdf-signing-service
                                                  ↓
                            ┌─────────────────────┴─────────────────────┐
                            ↓                                           ↓
                      pdf.signed                            pdf-storage-requested
                            ↓                                           ↓
                    notification-service                      document-storage-service
```

**Topics**:
- **Consumes**: `pdf.generated`, `pdf.signing.requested`
- **Produces**: `pdf.signed`, `pdf-storage-requested`
- **Events**: `PdfGeneratedEvent`, `PdfSignedEvent` (extend `IntegrationEvent`)

---

## Target State (Saga Orchestration)

```
saga-orchestrator → saga.command.pdf-signing → pdf-signing-service
                           (ProcessPdfSigningCommand)         ↓
                                                   ┌─────────┴─────────┐
                                                   ↓                   ↓
                                        saga.reply.pdf-signing  notification.events
                                                   ↓                   ↓
                                            saga-orchestrator    notification-service
                                                   ↓
                                             (next step)
```

**Topics**:
- **Consumes**: `saga.command.pdf-signing`, `saga.compensation.pdf-signing`
- **Produces**:
  - `saga.reply.pdf-signing` (via outbox) → Saga orchestrator
  - `notification.events` (via outbox) → Notification service (observer)
- **Events**: `ProcessPdfSigningCommand`, `CompensatePdfSigningCommand` (extend `SagaCommand`)
- **Replies**: `PdfSigningReplyEvent` (extends `SagaReply`)
- **Notifications**: `PdfSignedNotificationEvent` (extends `IntegrationEvent`)

> **Important**: The notification-service is a **reactive observer** that receives notification events regardless of saga orchestration. This dual-publishing pattern ensures:
> 1. **Saga coordination** via `saga.reply.pdf-signing` (orchestrator continues workflow)
> 2. **User notifications** via `notification.events` (email/webhook notifications)

---

## Phase 1: Add Saga Event Classes

### 1.1 Create Saga Command Events

**File**: `src/main/java/com/wpanther/pdfsigning/domain/event/ProcessPdfSigningCommand.java`

```java
package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

/**
 * Saga command from orchestrator to sign a PDF document.
 * Topic: saga.command.pdf-signing
 */
@Getter
public class ProcessPdfSigningCommand extends SagaCommand {

    private static final String COMMAND_TYPE = "ProcessPdfSigning";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("pdfUrl")
    private final String pdfUrl;

    @JsonProperty("pdfSize")
    private final Long pdfSize;

    @JsonProperty("xmlEmbedded")
    private final Boolean xmlEmbedded;

    // Constructor for deserialization
    @JsonCreator
    public ProcessPdfSigningCommand(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("sagaStep") String sagaStep,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("pdfUrl") String pdfUrl,
        @JsonProperty("pdfSize") Long pdfSize,
        @JsonProperty("xmlEmbedded") Boolean xmlEmbedded
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.pdfUrl = pdfUrl;
        this.pdfSize = pdfSize;
        this.xmlEmbedded = xmlEmbedded;
    }

    @Override
    public String getCommandType() {
        return COMMAND_TYPE;
    }
}
```

**File**: `src/main/java/com/wpanther/pdfsigning/domain/event/CompensatePdfSigningCommand.java`

```java
package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.SagaCommand;
import lombok.Getter;

/**
 * Saga command to compensate (rollback) PDF signing.
 * Topic: saga.compensation.pdf-signing
 */
@Getter
public class CompensatePdfSigningCommand extends SagaCommand {

    private static final String COMMAND_TYPE = "CompensatePdfSigning";

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("stepToCompensate")
    private final String stepToCompensate;

    @JsonCreator
    public CompensatePdfSigningCommand(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("sagaStep") String sagaStep,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("stepToCompensate") String stepToCompensate
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId);
        this.documentId = documentId;
        this.documentType = documentType;
        this.stepToCompensate = stepToCompensate;
    }

    @Override
    public String getCommandType() {
        return COMMAND_TYPE;
    }
}
```

### 1.2 Create Saga Reply Event

**File**: `src/main/java/com/wpanther/pdfsigning/domain/event/PdfSigningReplyEvent.java`

```java
package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.ReplyStatus;
import com.wpanther.saga.domain.model.SagaReply;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga reply from pdf-signing-service to orchestrator.
 * Topic: saga.reply.pdf-signing (via outbox)
 */
@Getter
public class PdfSigningReplyEvent extends SagaReply {

    private static final String REPLY_TYPE = "PdfSigningReply";

    @JsonProperty("signedDocumentId")
    private final String signedDocumentId;

    @JsonProperty("signedPdfUrl")
    private final String signedPdfUrl;

    @JsonProperty("signedPdfSize")
    private final Long signedPdfSize;

    @JsonProperty("transactionId")
    private final String transactionId;

    @JsonProperty("certificate")
    private final String certificate;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonProperty("signatureTimestamp")
    private final Instant signatureTimestamp;

    @JsonProperty("errorMessage")
    private final String errorMessage;

    // Factory methods
    public static PdfSigningReplyEvent success(
            String sagaId, String sagaStep, String correlationId,
            String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
            String transactionId, String certificate, String signatureLevel,
            Instant signatureTimestamp) {
        return new PdfSigningReplyEvent(
            UUID.randomUUID(), Instant.now(), REPLY_TYPE, 1,
            sagaId, sagaStep, correlationId, ReplyStatus.SUCCESS,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp,
            null
        );
    }

    public static PdfSigningReplyEvent failure(
            String sagaId, String sagaStep, String correlationId,
            String errorMessage) {
        return new PdfSigningReplyEvent(
            UUID.randomUUID(), Instant.now(), REPLY_TYPE, 1,
            sagaId, sagaStep, correlationId, ReplyStatus.FAILURE,
            null, null, null, null, null, null, null,
            errorMessage
        );
    }

    public static PdfSigningReplyEvent compensated(
            String sagaId, String sagaStep, String correlationId) {
        return new PdfSigningReplyEvent(
            UUID.randomUUID(), Instant.now(), REPLY_TYPE, 1,
            sagaId, sagaStep, correlationId, ReplyStatus.COMPENSATED,
            null, null, null, null, null, null, null,
            null
        );
    }

    // Constructor for deserialization
    @JsonCreator
    public PdfSigningReplyEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("sagaStep") String sagaStep,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("status") ReplyStatus status,
        @JsonProperty("signedDocumentId") String signedDocumentId,
        @JsonProperty("signedPdfUrl") String signedPdfUrl,
        @JsonProperty("signedPdfSize") Long signedPdfSize,
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("certificate") String certificate,
        @JsonProperty("signatureLevel") String signatureLevel,
        @JsonProperty("signatureTimestamp") Instant signatureTimestamp,
        @JsonProperty("errorMessage") String errorMessage
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, sagaStep, correlationId, status);
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
        this.errorMessage = errorMessage;
    }

    @Override
    public String getReplyType() {
        return REPLY_TYPE;
    }
}
```

### 1.3 Create Notification Event (Observer Pattern)

**File**: `src/main/java/com/wpanther/pdfsigning/domain/event/PdfSignedNotificationEvent.java`

```java
package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification event when PDF is signed.
 * Published to notification.events for the observer notification-service.
 * This is separate from saga.reply - orchestrator doesn't consume this.
 */
@Getter
public class PdfSignedNotificationEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "PdfSigned";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("signedDocumentId")
    private final String signedDocumentId;

    @JsonProperty("signedPdfUrl")
    private final String signedPdfUrl;

    @JsonProperty("signedPdfSize")
    private final Long signedPdfSize;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonProperty("signatureTimestamp")
    private final Instant signatureTimestamp;

    @JsonProperty("correlationId")
    private final String correlationId;

    // Factory method
    public static PdfSignedNotificationEvent create(
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {
        PdfSignedNotificationEvent event = new PdfSignedNotificationEvent(
            invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );
        return event;
    }

    // Constructor for creating new events
    private PdfSignedNotificationEvent(
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Constructor for deserialization
    @JsonCreator
    public PdfSignedNotificationEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("signedDocumentId") String signedDocumentId,
        @JsonProperty("signedPdfUrl") String signedPdfUrl,
        @JsonProperty("signedPdfSize") Long signedPdfSize,
        @JsonProperty("signatureLevel") String signatureLevel,
        @JsonProperty("signatureTimestamp") Instant signatureTimestamp,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
        this.correlationId = correlationId;
    }
}
```

---

## Phase 2: Add Outbox Pattern Infrastructure

### 2.1 Add Outbox Dependencies

**Verify in pom.xml** (already present):
```xml
<dependency>
    <groupId>com.wpanther</groupId>
    <artifactId>saga-commons</artifactId>
    <version>${saga.commons.version}</version>
</dependency>
```

### 2.2 Create Outbox Event Entity

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/OutboxEventEntity.java`

```java
package com.wpanther.pdfsigning.infrastructure.persistence;

import com.wpanther.saga.domain.outbox.OutboxStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbox_events", indexes = {
    @Index(name = "idx_outbox_status", columnList = "status"),
    @Index(name = "idx_outbox_created_at", columnList = "created_at"),
    @Index(name = "idx_outbox_aggregate", columnList = "aggregate_id, aggregate_type")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OutboxEventEntity {

    @Id
    @Column(updatable = false, nullable = false)
    private UUID id;

    @Column(nullable = false, length = 100)
    private String aggregateType;

    @Column(nullable = false, length = 100)
    private String aggregateId;

    @Column(nullable = false, length = 100)
    private String eventType;

    @Lob
    @JdbcTypeCode(SqlTypes.JSON)
    private String payload;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private OutboxStatus status;

    @Column(name = "retry_count")
    private Integer retryCount;

    @Column(name = "error_message")
    private String errorMessage;

    // CDC routing fields
    @Column(length = 255)
    private String topic;

    @Column(length = 255)
    private String partitionKey;

    @Lob
    @JdbcTypeCode(SqlTypes.JSON)
    private String headers;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (status == null) {
            status = OutboxStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
    }
}
```

### 2.3 Create Outbox Repository

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/JpaOutboxEventRepository.java`

```java
package com.wpanther.pdfsigning.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface JpaOutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query("SELECT e FROM OutboxEventEntity e WHERE e.status = 'PENDING' ORDER BY e.createdAt ASC")
    List<OutboxEventEntity> findPendingEvents(@Param("limit") int limit);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.status = 'PUBLISHED', e.publishedAt = :publishedAt WHERE e.id = :id")
    int markAsPublished(@Param("id") UUID id, @Param("publishedAt") LocalDateTime publishedAt);

    @Modifying
    @Transactional
    @Query("UPDATE OutboxEventEntity e SET e.status = 'FAILED', e.errorMessage = :errorMessage, e.retryCount = :retryCount WHERE e.id = :id")
    int markAsFailed(@Param("id") UUID id, @Param("errorMessage") String errorMessage, @Param("retryCount") int retryCount);

    @Modifying
    @Transactional
    @Query("DELETE FROM OutboxEventEntity e WHERE e.status = 'PUBLISHED' AND e.publishedAt < :cutoffDate")
    int deletePublishedBefore(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

### 2.4 Create OutboxEventRepository Adapter

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/persistence/OutboxEventRepositoryAdapter.java`

```java
package com.wpanther.pdfsigning.infrastructure.persistence;

import com.wpanther.saga.domain.outbox.OutboxEvent;
import com.wpanther.saga.domain.outbox.OutboxEventRepository;
import com.wpanther.saga.domain.outbox.OutboxStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class OutboxEventRepositoryAdapter implements OutboxEventRepository {

    private final JpaOutboxEventRepository jpaRepository;

    @Override
    public OutboxEvent save(OutboxEvent outboxEvent) {
        OutboxEventEntity entity = toEntity(outboxEvent);
        OutboxEventEntity saved = jpaRepository.save(entity);
        return fromEntity(saved);
    }

    @Override
    public Optional<OutboxEvent> findById(UUID id) {
        return jpaRepository.findById(id)
            .map(this::fromEntity);
    }

    @Override
    public List<OutboxEvent> findPendingEvents(int limit) {
        return jpaRepository.findPendingEvents(limit).stream()
            .map(this::fromEntity)
            .collect(Collectors.toList());
    }

    @Override
    public boolean markAsPublished(UUID id, LocalDateTime publishedAt) {
        return jpaRepository.markAsPublished(id, publishedAt) > 0;
    }

    @Override
    public boolean markAsFailed(UUID id, String errorMessage, int retryCount) {
        return jpaRepository.markAsFailed(id, errorMessage, retryCount) > 0;
    }

    @Override
    public int deletePublishedBefore(LocalDateTime cutoffDate) {
        return jpaRepository.deletePublishedBefore(cutoffDate);
    }

    private OutboxEventEntity toEntity(OutboxEvent outbox) {
        return OutboxEventEntity.builder()
            .id(outbox.getId())
            .aggregateType(outbox.getAggregateType())
            .aggregateId(outbox.getAggregateId())
            .eventType(outbox.getEventType())
            .payload(outbox.getPayload())
            .status(outbox.getStatus())
            .retryCount(outbox.getRetryCount())
            .errorMessage(outbox.getErrorMessage())
            .topic(outbox.getTopic())
            .partitionKey(outbox.getPartitionKey())
            .headers(outbox.getHeaders())
            .createdAt(outbox.getCreatedAt())
            .publishedAt(outbox.getPublishedAt())
            .build();
    }

    private OutboxEvent fromEntity(OutboxEventEntity entity) {
        return OutboxEvent.builder()
            .id(entity.getId())
            .aggregateType(entity.getAggregateType())
            .aggregateId(entity.getAggregateId())
            .eventType(entity.getEventType())
            .payload(entity.getPayload())
            .status(entity.getStatus())
            .retryCount(entity.getRetryCount())
            .errorMessage(entity.getErrorMessage())
            .topic(entity.getTopic())
            .partitionKey(entity.getPartitionKey())
            .headers(entity.getHeaders())
            .createdAt(entity.getCreatedAt())
            .publishedAt(entity.getPublishedAt())
            .build();
    }
}
```

### 2.5 Create Database Migration

**File**: `src/main/resources/db/migration/V3__create_outbox_events_table.sql`

```sql
-- Create outbox_events table for transactional outbox pattern
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(20) NOT NULL,
    retry_count INTEGER DEFAULT 0,
    error_message TEXT,
    topic VARCHAR(255),
    partition_key VARCHAR(255),
    headers TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

-- Create indexes for outbox queries
CREATE INDEX idx_outbox_status ON outbox_events(status);
CREATE INDEX idx_outbox_created_at ON outbox_events(created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events(aggregate_id, aggregate_type);

-- Add comments
COMMENT ON TABLE outbox_events IS 'Transactional outbox for reliable event publishing via Debezium CDC';
COMMENT ON COLUMN outbox_events.id IS 'Unique event identifier';
COMMENT ON COLUMN outbox_events.aggregate_type IS 'Aggregate type (e.g., SignedPdfDocument)';
COMMENT ON COLUMN outbox_events.aggregate_id IS 'Aggregate ID (e.g., document ID)';
COMMENT ON COLUMN outbox_events.event_type IS 'Event type (e.g., PdfSigningReply)';
COMMENT ON COLUMN outbox_events.payload IS 'Event payload (JSON)';
COMMENT ON COLUMN outbox_events.status IS 'PENDING, PUBLISHED, FAILED';
COMMENT ON COLUMN outbox_events.topic IS 'Target Kafka topic for CDC routing';
COMMENT ON COLUMN outbox_events.partition_key IS 'Kafka partition key';
COMMENT ON COLUMN outbox_events.headers IS 'Message headers (JSON)';
```

---

## Phase 3: Create Event Publishers

**Dual-Publishing Pattern**: The service publishes TWO types of events:
1. **Saga replies** → `saga.reply.pdf-signing` (for orchestrator coordination)
2. **Notification events** → `notification.events` (for notification-service observer)

### 3.1 Create Saga Reply Publisher

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/SagaReplyPublisher.java`

```java
package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSigningReplyEvent;
import com.wpanther.saga.domain.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes saga replies to the orchestrator via transactional outbox.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SagaReplyPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    /**
     * Publish SUCCESS reply when PDF signing completes successfully.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishSuccess(
            String sagaId,
            String sagaStep,
            String correlationId,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            java.time.Instant signatureTimestamp) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.success(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "SUCCESS");

        outboxService.saveWithRouting(
            reply,
            "SignedPdfDocument",
            sagaId,
            "saga.reply.pdf-signing",
            sagaId,
            toJson(headers)
        );

        log.info("Published SUCCESS reply for sagaId={}, correlationId={}", sagaId, correlationId);
    }

    /**
     * Publish FAILURE reply when PDF signing fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishFailure(String sagaId, String sagaStep, String correlationId, String errorMessage) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.failure(
            sagaId, sagaStep, correlationId, errorMessage
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "FAILURE");

        outboxService.saveWithRouting(
            reply,
            "SignedPdfDocument",
            sagaId,
            "saga.reply.pdf-signing",
            sagaId,
            toJson(headers)
        );

        log.warn("Published FAILURE reply for sagaId={}, correlationId={}, error={}",
            sagaId, correlationId, errorMessage);
    }

    /**
     * Publish COMPENSATED reply when compensation completes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishCompensated(String sagaId, String sagaStep, String correlationId) {

        PdfSigningReplyEvent reply = PdfSigningReplyEvent.compensated(
            sagaId, sagaStep, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("sagaId", sagaId);
        headers.put("correlationId", correlationId);
        headers.put("status", "COMPENSATED");

        outboxService.saveWithRouting(
            reply,
            "SignedPdfDocument",
            sagaId,
            "saga.reply.pdf-signing",
            sagaId,
            toJson(headers)
        );

        log.info("Published COMPENSATED reply for sagaId={}, correlationId={}", sagaId, correlationId);
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize headers to JSON", e);
            return "{}";
        }
    }
}
```

### 3.2 Create Notification Event Publisher

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/NotificationEventPublisher.java`

```java
package com.wpanther.pdfsigning.infrastructure.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wpanther.pdfsigning.domain.event.PdfSignedNotificationEvent;
import com.wpanther.saga.domain.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

/**
 * Publishes notification events for the notification-service observer.
 * Notification-service is NOT part of saga - it's a reactive observer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventPublisher {

    private final OutboxService outboxService;
    private final ObjectMapper objectMapper;

    @Value("${app.kafka.topics.notification-events:notification.events}")
    private String notificationEventsTopic;

    /**
     * Publish notification event when PDF is signed successfully.
     * This is separate from saga reply - orchestrator doesn't consume this.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSignedNotification(
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            java.time.Instant signatureTimestamp,
            String correlationId) {

        PdfSignedNotificationEvent notification = PdfSignedNotificationEvent.create(
            invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigned");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        // Use invoiceId as partition key for all events of the same invoice
        outboxService.saveWithRouting(
            notification,
            "SignedPdfDocument",
            signedDocumentId,
            notificationEventsTopic,
            invoiceId,  // Partition by invoiceId for ordering
            toJson(headers)
        );

        log.info("Published PdfSigned notification for invoiceId={}, invoiceNumber={}, documentType={}",
            invoiceId, invoiceNumber, documentType);
    }

    /**
     * Publish notification event when PDF signing fails.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void publishPdfSigningFailureNotification(
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        Map<String, String> headers = new HashMap<>();
        headers.put("eventType", "PdfSigningFailed");
        headers.put("documentType", documentType);
        headers.put("correlationId", correlationId);
        headers.put("invoiceId", invoiceId);

        // Create a simple failure notification map
        Map<String, Object> failureNotification = new HashMap<>();
        failureNotification.put("eventType", "PdfSigningFailed");
        failureNotification.put("invoiceId", invoiceId);
        failureNotification.put("invoiceNumber", invoiceNumber);
        failureNotification.put("documentType", documentType);
        failureNotification.put("errorMessage", errorMessage);
        failureNotification.put("correlationId", correlationId);
        failureNotification.put("occurredAt", java.time.Instant.now().toString());

        try {
            outboxService.saveWithRouting(
                failureNotification,
                "SignedPdfDocument",
                invoiceId,
                notificationEventsTopic,
                invoiceId,
                toJson(headers)
            );

            log.warn("Published PdfSigningFailed notification for invoiceId={}, error={}",
                invoiceId, errorMessage);
        } catch (Exception e) {
            log.error("Failed to publish failure notification for invoiceId={}", invoiceId, e);
        }
    }

    private String toJson(Map<String, String> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize headers to JSON", e);
            return "{}";
        }
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize object to JSON", e);
            return "{}";
        }
    }
}
```

### 3.3 Combined Event Publisher (Optional Convenience Wrapper)

**File**: `src/main/java/com/wpanther/pdfsigning/infrastructure/messaging/PdfSigningEventPublisher.java`

```java
package com.wpanther.pdfsigning.infrastructure.messaging;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Convenience wrapper that publishes BOTH saga reply AND notification event.
 * Ensures both events are published in the same transaction.
 */
@Component
@RequiredArgsConstructor
public class PdfSigningEventPublisher {

    private final SagaReplyPublisher sagaReplyPublisher;
    private final NotificationEventPublisher notificationEventPublisher;

    /**
     * Publish SUCCESS: saga reply to orchestrator + notification to notification-service.
     * Both events are published in the same transaction via outbox pattern.
     */
    public void publishSuccess(
            String sagaId,
            String sagaStep,
            String correlationId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String transactionId,
            String certificate,
            String signatureLevel,
            Instant signatureTimestamp) {

        // 1. Publish saga reply (for orchestrator)
        sagaReplyPublisher.publishSuccess(
            sagaId, sagaStep, correlationId,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            transactionId, certificate, signatureLevel, signatureTimestamp
        );

        // 2. Publish notification event (for notification-service observer)
        notificationEventPublisher.publishPdfSignedNotification(
            invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );
    }

    /**
     * Publish FAILURE: saga reply to orchestrator + notification to notification-service.
     */
    public void publishFailure(
            String sagaId,
            String sagaStep,
            String correlationId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage) {

        // 1. Publish saga reply (for orchestrator)
        sagaReplyPublisher.publishFailure(
            sagaId, sagaStep, correlationId, errorMessage
        );

        // 2. Publish notification event (for notification-service observer)
        notificationEventPublisher.publishPdfSigningFailureNotification(
            invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );
    }
}
```

---

## Phase 4: Create Saga Command Handler

**File**: `src/main/java/com/wpanther/pdfsigning/application/service/SagaCommandHandler.java`

```java
package com.wpanther.pdfsigning.application.service;

import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import com.wpanther.pdfsigning.domain.model.SignedPdfDocument;
import com.wpanther.pdfsigning.domain.repository.SignedPdfDocumentRepository;
import com.wpanther.pdfsigning.domain.service.PdfSigningService;
import com.wpanther.pdfsigning.infrastructure.messaging.PdfSigningEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Handles saga commands from the orchestrator.
 * Publishes BOTH saga reply (to orchestrator) AND notification event (to notification-service).
 * Follows xml-signing-service pattern.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SagaCommandHandler {

    private final SignedPdfDocumentRepository documentRepository;
    private final PdfSigningService signingService;
    private final PdfSigningEventPublisher eventPublisher;  // Combined publisher

    /**
     * Handles ProcessPdfSigningCommand from saga orchestrator.
     * Signs the PDF and sends SUCCESS/FAILURE reply + notification.
     */
    @Transactional
    public void handleProcessCommand(ProcessPdfSigningCommand command) {
        log.info("Processing PDF signing command: sagaId={}, documentId={}, documentType={}",
            command.getSagaId(), command.getDocumentId(), command.getDocumentType());

        // 1. Check idempotency - use documentId (from command)
        var existing = documentRepository.findByInvoiceId(command.getDocumentId());

        // 2. Check if already completed (idempotent)
        if (existing.isPresent() && existing.get().isCompleted()) {
            log.info("PDF already signed for documentId={}, sending SUCCESS reply", command.getDocumentId());
            sendSuccessReply(command, existing.get());
            return;
        }

        // 3. Check retry limits
        int maxRetries = 3; // TODO: configure
        if (existing.isPresent() && existing.get().getRetryCount() >= maxRetries) {
            log.warn("Max retries exceeded for documentId={}, sending FAILURE reply", command.getDocumentId());
            eventPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                "Maximum retry attempts exceeded for PDF signing"
            );
            return;
        }

        // 4. Create or retrieve aggregate
        SignedPdfDocument document = existing.orElseGet(() ->
            SignedPdfDocument.create(
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getPdfUrl(),
                command.getPdfSize(),
                command.getCorrelationId(),
                command.getDocumentType()
            )
        );

        // 5. Start signing (state transition)
        document.startSigning();
        documentRepository.save(document);

        try {
            // 6. Execute signing
            PdfSigningService.SignedPdfResult result = signingService.signPdf(
                command.getPdfUrl(),
                document.getId().toString()
            );

            // 7. Mark completed (state transition)
            document.markCompleted(
                result.signedPdfPath(),
                result.signedPdfUrl(),
                result.signedPdfSize(),
                result.transactionId(),
                result.certificate(),
                result.signatureLevel(),
                result.signatureTimestamp().toLocalDateTime() // Convert Instant to LocalDateTime
            );
            documentRepository.save(document);

            // 8. Send SUCCESS reply AND notification event (dual publishing)
            eventPublisher.publishSuccess(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),           // invoiceId
                command.getInvoiceNumber(),
                command.getDocumentType(),
                document.getId().toString(),
                document.getSignedPdfUrl(),
                document.getSignedPdfSize(),
                document.getTransactionId(),
                document.getCertificate(),
                document.getSignatureLevel(),
                result.signatureTimestamp()
            );

            log.info("PDF signing completed successfully for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId());

        } catch (Exception e) {
            // 9. Handle failure
            log.error("PDF signing failed for documentId={}, sagaId={}",
                command.getDocumentId(), command.getSagaId(), e);

            document.markFailed(e.getMessage());
            document.incrementRetryCount();
            documentRepository.save(document);

            // Send FAILURE reply AND notification event
            eventPublisher.publishFailure(
                command.getSagaId(),
                command.getSagaStep(),
                command.getCorrelationId(),
                command.getDocumentId(),
                command.getInvoiceNumber(),
                command.getDocumentType(),
                e.getMessage()
            );
        }
    }

    /**
     * Handles CompensatePdfSigningCommand from saga orchestrator.
     * Deletes the signed document and sends COMPENSATED reply.
     */
    @Transactional
    public void handleCompensation(CompensatePdfSigningCommand command) {
        log.info("Compensating PDF signing: sagaId={}, documentId={}",
            command.getSagaId(), command.getDocumentId());

        // 1. Find and delete the signed document
        var existing = documentRepository.findByInvoiceId(command.getDocumentId());

        if (existing.isPresent()) {
            SignedPdfDocument document = existing.get();

            // Delete signed PDF file from filesystem
            try {
                java.nio.file.Files.deleteIfExists(
                    java.nio.file.Paths.get(document.getSignedPdfPath())
                );
                log.info("Deleted signed PDF file: {}", document.getSignedPdfPath());
            } catch (Exception e) {
                log.warn("Failed to delete signed PDF file: {}", document.getSignedPdfPath(), e);
            }

            // Delete from database
            documentRepository.deleteById(document.getId());
            log.info("Deleted SignedPdfDocument {} for compensation", document.getId());
        } else {
            log.info("No signed document found for documentId={}, compensation already done or never existed",
                command.getDocumentId());
        }

        // 2. Send COMPENSATED reply (idempotent)
        // Note: No notification event for compensation - orchestrator handles that
        eventPublisher.publishCompensated(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId()
        );
    }

    private void sendSuccessReply(ProcessPdfSigningCommand command, SignedPdfDocument document) {
        // Publish both saga reply and notification event
        eventPublisher.publishSuccess(
            command.getSagaId(),
            command.getSagaStep(),
            command.getCorrelationId(),
            command.getDocumentId(),
            command.getInvoiceNumber(),
            command.getDocumentType(),
            document.getId().toString(),
            document.getSignedPdfUrl(),
            document.getSignedPdfSize(),
            document.getTransactionId(),
            document.getCertificate(),
            document.getSignatureLevel(),
            document.getSignatureTimestamp().toInstant()
        );
    }
}
```

---

## Phase 5: Update Camel Routes for Saga Topics
            document.getSignatureLevel(),
            document.getSignatureTimestamp().toInstant() // Convert LocalDateTime to Instant
        );
    }
}
```

---

## Phase 5: Update Camel Routes for Saga Topics

**Update**: `src/main/java/com/wpanther/pdfsigning/infrastructure/config/PdfSigningRouteConfig.java`

```java
package com.wpanther.pdfsigning.infrastructure.config;

import com.wpanther.pdfsigning.application.service.SagaCommandHandler;
import com.wpanther.pdfsigning.domain.event.CompensatePdfSigningCommand;
import com.wpanther.pdfsigning.domain.event.ProcessPdfSigningCommand;
import lombok.extern.slf4j.Slf4j;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Apache Camel routes for PDF signing with saga orchestration.
 *
 * Consumes from:
 * - saga.command.pdf-signing (ProcessPdfSigningCommand from orchestrator)
 * - saga.compensation.pdf-signing (CompensatePdfSigningCommand from orchestrator)
 *
 * Produces to:
 * - saga.reply.pdf-signing (PdfSigningReplyEvent via outbox + Debezium CDC)
 */
@Component
@Slf4j
public class PdfSigningRouteConfig extends RouteBuilder {

    private final SagaCommandHandler sagaCommandHandler;

    @Value("${app.kafka.bootstrap-servers}")
    private String kafkaBrokers;

    @Value("${app.kafka.topics.saga-command:saga.command.pdf-signing}")
    private String sagaCommandTopic;

    @Value("${app.kafka.topics.saga-compensation:saga.compensation.pdf-signing}")
    private String sagaCompensationTopic;

    @Value("${app.kafka.topics.dlq:pdf.signing.dlq}")
    private String dlqTopic;

    public PdfSigningRouteConfig(SagaCommandHandler sagaCommandHandler) {
        this.sagaCommandHandler = sagaCommandHandler;
    }

    @Override
    public void configure() {

        // Global error handler - Dead Letter Channel
        errorHandler(deadLetterChannel("kafka:" + dlqTopic + "?brokers=" + kafkaBrokers)
            .maximumRedeliveries(3)
            .redeliveryDelay(1000)
            .useExponentialBackOff()
            .backOffMultiplier(2)
            .maximumRedeliveryDelay(10000)
            .logExhausted(true)
            .logStackTrace(true));

        // ============================================================
        // SAGA COMMAND CONSUMER: ProcessPdfSigningCommand
        // ============================================================
        from("kafka:" + sagaCommandTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=pdf-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=10"
                + "&consumersCount=3")
            .routeId("saga-command-consumer")
            .log("Received ProcessPdfSigningCommand: sagaId=${header[kafka.KEY]}, partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            .unmarshal().json(JsonLibrary.Jackson, ProcessPdfSigningCommand.class)

            .process(exchange -> {
                ProcessPdfSigningCommand command = exchange.getIn().getBody(ProcessPdfSigningCommand.class);
                log.info("Processing PDF signing: sagaId={}, documentId={}, documentType={}",
                    command.getSagaId(), command.getDocumentId(), command.getDocumentType());

                sagaCommandHandler.handleProcessCommand(command);
            })
            .log("Successfully processed PDF signing for sagaId: ${body.sagaId}");

        // ============================================================
        // SAGA COMPENSATION CONSUMER: CompensatePdfSigningCommand
        // ============================================================
        from("kafka:" + sagaCompensationTopic
                + "?brokers=" + kafkaBrokers
                + "&groupId=pdf-signing-service"
                + "&autoOffsetReset=earliest"
                + "&autoCommitEnable=false"
                + "&breakOnFirstError=true"
                + "&maxPollRecords=10"
                + "&consumersCount=3")
            .routeId("saga-compensation-consumer")
            .log("Received CompensatePdfSigningCommand: sagaId=${header[kafka.KEY]}, partition=${header[kafka.PARTITION]}, offset=${header[kafka.OFFSET]}")

            .unmarshal().json(JsonLibrary.Jackson, CompensatePdfSigningCommand.class)

            .process(exchange -> {
                CompensatePdfSigningCommand command = exchange.getIn().getBody(CompensatePdfSigningCommand.class);
                log.info("Compensating PDF signing: sagaId={}, documentId={}",
                    command.getSagaId(), command.getDocumentId());

                sagaCommandHandler.handleCompensation(command);
            })
            .log("Successfully compensated PDF signing for sagaId: ${body.sagaId}");
    }
}
```

---

## Phase 6: Update Configuration

**Add to**: `src/main/resources/application.yml`

```yaml
app:
  kafka:
    bootstrap-servers: ${KAFKA_BROKERS:localhost:9092}
    topics:
      # Saga topics (new)
      saga-command: saga.command.pdf-signing
      saga-compensation: saga.compensation.pdf-signing
      # Notification events (observer pattern)
      notification-events: notification.events
      # Legacy topics (keep for backward compatibility during migration)
      pdf-generated: pdf.generated
      pdf-signing-requested: pdf.signing.requested
      pdf-signed: pdf.signed
      pdf-storage-requested: pdf-storage-requested
      dlq: pdf.signing.dlq

  # Outbox configuration (saga-commons)
saga:
  outbox:
    cleanup:
      enabled: false
      cron-expression: "0 0 2 * * ?"
      retention-hours: 24
    publisher:
      batch-size: 100
      poll-interval-millis: 1000
      max-retries: 3
```

**Key Topics After Migration**:

| Topic | Direction | Purpose | Event Type |
|-------|-----------|---------|------------|
| `saga.command.pdf-signing` | Consume | Command from orchestrator | ProcessPdfSigningCommand |
| `saga.compensation.pdf-signing` | Consume | Compensation from orchestrator | CompensatePdfSigningCommand |
| `saga.reply.pdf-signing` | Produce (via outbox) | Reply to orchestrator | PdfSigningReplyEvent |
| `notification.events` | Produce (via outbox) | Notification to observer | PdfSignedNotificationEvent |

---

## Phase 7: Migration Strategy (Rolling Deployment)

### Step 1: Deploy New Code Alongside Old
1. Add new saga command/reply topics
2. Deploy new code that supports BOTH patterns:
   - Legacy: `pdf.generated` → `pdf.signed`
   - Saga: `saga.command.pdf-signing` → `saga.reply.pdf-signing`

### Step 2: Migrate Upstream Services
1. Update pdf-generation-services to publish to saga topics (via orchestrator)
2. Keep legacy publishing for rollback safety

### Step 3: Migrate Downstream Services
1. Update notification-service and document-storage-service to consume from saga.reply.pdf-signing
2. Or keep them consuming legacy topics - orchestrator handles notification

### Step 4: Deprecate Legacy Topics
1. Stop consuming from `pdf.generated`
2. Stop producing to `pdf.signed`, `pdf-storage-requested`
3. Remove legacy event classes

---

## Phase 8: Testing Checklist

- [ ] Unit tests for `SagaCommandHandler`
- [ ] Unit tests for `SagaReplyPublisher`
- [ ] Integration tests for saga command consumption
- [ ] Integration tests for saga reply publishing via outbox
- [ ] Integration tests for compensation flow
- [ ] CDC tests with Debezium for outbox event routing
- [ ] Idempotency tests (duplicate commands)
- [ ] Retry limit tests
- [ ] Dead letter queue tests
- [ ] End-to-end saga flow tests

---

## Phase 9: Debezium CDC Configuration

**Connector Config** (for Kafka Connect):

```json
{
  "name": "pdf-signing-outbox-connector",
  "config": {
    "connector.class": "io.debezium.connector.postgresql.PostgresConnector",
    "database.hostname": "localhost",
    "database.port": "5432",
    "database.user": "postgres",
    "database.password": "postgres",
    "database.dbname": "pdfsigning_db",
    "topic.prefix": "pdf-signing",
    "plugin.name": "pgoutput",
    "table.include.list": "public.outbox_events",
    "transforms": "outbox",
    "transforms.outbox.type": "io.debezium.transforms.outbox.EventRouter",
    "transforms.outbox.table.field.event.type": "event_type",
    "transforms.outbox.table.field.payload": "payload",
    "transforms.outbox.table.field.additionalFields": "aggregateType,aggregateId",
    "transforms.outbox.route.topic.replacement": "${topic}",
    "transforms.outbox.route.key.field": "partition_key",
    "transforms.outbox.route.by.field": "topic"
  }
}
```

---

## Summary of Changes

| Category | Files to Add | Files to Modify |
|----------|--------------|-----------------|
| **Domain Events** | ProcessPdfSigningCommand.java<br>CompensatePdfSigningCommand.java<br>PdfSigningReplyEvent.java<br>PdfSignedNotificationEvent.java | - |
| **Infrastructure** | OutboxEventEntity.java<br>JpaOutboxEventRepository.java<br>OutboxEventRepositoryAdapter.java<br>SagaReplyPublisher.java<br>NotificationEventPublisher.java<br>PdfSigningEventPublisher.java<br>SagaCommandHandler.java | - |
| **Routes** | - | PdfSigningRouteConfig.java |
| **Database** | V3__create_outbox_events_table.sql | - |
| **Config** | - | application.yml |
| **Tests** | SagaCommandHandlerTest.java<br>SagaReplyPublisherTest.java<br>NotificationEventPublisherTest.java<br>SagaRouteConfigTest.java | - |
| **Docs** | SAGA_MIGRATION_PLAN.md | CLAUDE.md, README.md |

---

## Dependencies

Verify in `pom.xml`:

```xml
<!-- saga-commons for SagaCommand, SagaReply, OutboxService -->
<dependency>
    <groupId>com.wpanther</groupId>
    <artifactId>saga-commons</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## Timeline Estimate

| Phase | Estimated Effort |
|-------|-----------------|
| Phase 1: Add Saga Event Classes | 2 hours |
| Phase 2: Add Outbox Pattern Infrastructure | 4 hours |
| Phase 3: Create Saga Reply Publisher | 2 hours |
| Phase 4: Create Saga Command Handler | 4 hours |
| Phase 5: Update Camel Routes | 2 hours |
| Phase 6: Update Configuration | 1 hour |
| Phase 7: Migration Strategy | Planning |
| Phase 8: Testing | 8 hours |
| Phase 9: Debezium CDC Configuration | 2 hours |
| **Total** | **~25 hours** |
