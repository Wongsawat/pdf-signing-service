package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Notification event when PDF signing fails.
 * Published to: notification.events (via outbox pattern)
 *
 * This is a notification event for the notification-service observer.
 */
@Getter
public class PdfSigningFailedNotificationEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "PdfSigningFailed";

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
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        return new PdfSigningFailedNotificationEvent(
            invoiceId, invoiceNumber, documentType,
            errorMessage, correlationId
        );
    }

    /**
     * Constructor for creating new events.
     */
    private PdfSigningFailedNotificationEvent(
            String invoiceId,
            String invoiceNumber,
            String documentType,
            String errorMessage,
            String correlationId) {

        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
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
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("errorMessage") String errorMessage,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.errorMessage = errorMessage;
        this.correlationId = correlationId;
    }
}
