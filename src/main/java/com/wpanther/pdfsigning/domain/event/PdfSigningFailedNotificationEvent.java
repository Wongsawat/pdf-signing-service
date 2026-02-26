package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.TraceEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification event when PDF signing fails.
 * Published to: notification.events (via outbox pattern)
 *
 * This is a notification event for the notification-service observer.
 * <p>
 * Extends TraceEvent as it represents an observational event for audit/notification purposes.
 */
@Getter
public class PdfSigningFailedNotificationEvent extends TraceEvent {

    private static final String TRACE_TYPE = "PdfSigningFailed";
    private static final String SOURCE = "pdf-signing-service";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("errorMessage")
    private final String errorMessage;

    @JsonProperty("correlationId")
    private final String correlationId;

    /**
     * Factory method for creating new notification events.
     */
    public static PdfSigningFailedNotificationEvent create(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        return new PdfSigningFailedNotificationEvent(
            sagaId, invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );
    }

    /**
     * Constructor for creating new events.
     */
    private PdfSigningFailedNotificationEvent(
            String sagaId,
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        super(sagaId, SOURCE, TRACE_TYPE, null);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
    }

    /**
     * Constructor for deserialization from Kafka.
     */
    @JsonCreator
    public PdfSigningFailedNotificationEvent(
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
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version, sagaId, source, traceType, context);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
    }
}
