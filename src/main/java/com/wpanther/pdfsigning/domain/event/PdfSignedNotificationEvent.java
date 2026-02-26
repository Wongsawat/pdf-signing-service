package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification event when PDF is signed successfully.
 * Published to: notification.events (via outbox pattern)
 *
 * This is a notification event for the notification-service observer.
 * It is separate from the saga reply - the orchestrator does NOT consume this.
 * Only notification-service consumes this event to send email/webhook notifications.
 * <p>
 * Extends TraceEvent as it represents an observational event for audit/notification purposes.
 */
@Getter
public class PdfSignedNotificationEvent extends TraceEvent {

    private static final String TRACE_TYPE = "PdfSigned";
    private static final String SOURCE = "pdf-signing-service";

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

    /**
     * Factory method for creating new notification events.
     */
    public static PdfSignedNotificationEvent create(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {

        return new PdfSignedNotificationEvent(
            sagaId, invoiceId, invoiceNumber, documentType,
            signedDocumentId, signedPdfUrl, signedPdfSize,
            signatureLevel, signatureTimestamp, correlationId
        );
    }

    /**
     * Constructor for creating new events.
     */
    private PdfSignedNotificationEvent(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String signedDocumentId,
            String signedPdfUrl,
            Long signedPdfSize,
            String signatureLevel,
            Instant signatureTimestamp,
            String correlationId) {

        super(sagaId, SOURCE, TRACE_TYPE, null);
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

    /**
     * Constructor for deserialization from Kafka.
     * Used by outbox event consumers or notification-service.
     */
    @JsonCreator
    public PdfSignedNotificationEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("sagaId") String sagaId,
        @JsonProperty("source") String source,
        @JsonProperty("traceType") String traceType,
        @JsonProperty("context") String context,
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
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
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
