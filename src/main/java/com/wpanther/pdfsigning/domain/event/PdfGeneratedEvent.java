package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published by pdf-generation-service when a PDF is generated.
 * Consumed by pdf-signing-service to trigger PDF signing.
 *
 * Topic: pdf.generated
 */
@Getter
public class PdfGeneratedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "pdf.generated";

    @JsonProperty("invoiceId")
    private final String invoiceId;

    @JsonProperty("invoiceNumber")
    private final String invoiceNumber;

    @JsonProperty("documentType")
    private final String documentType;

    @JsonProperty("documentId")
    private final String documentId;

    @JsonProperty("documentUrl")
    private final String documentUrl;

    @JsonProperty("fileSize")
    private final Long fileSize;

    @JsonProperty("xmlEmbedded")
    private final Boolean xmlEmbedded;

    @JsonProperty("correlationId")
    private final String correlationId;

    // Constructor for creating new events
    public PdfGeneratedEvent(String invoiceId, String invoiceNumber, String documentType,
                             String documentId, String documentUrl, Long fileSize,
                             Boolean xmlEmbedded, String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.correlationId = correlationId;
    }

    @Override
    public String getEventType() {
        return EVENT_TYPE;
    }

    // Constructor for deserialization
    @JsonCreator
    public PdfGeneratedEvent(
        @JsonProperty("eventId") UUID eventId,
        @JsonProperty("occurredAt") Instant occurredAt,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("version") int version,
        @JsonProperty("invoiceId") String invoiceId,
        @JsonProperty("invoiceNumber") String invoiceNumber,
        @JsonProperty("documentType") String documentType,
        @JsonProperty("documentId") String documentId,
        @JsonProperty("documentUrl") String documentUrl,
        @JsonProperty("fileSize") Long fileSize,
        @JsonProperty("xmlEmbedded") Boolean xmlEmbedded,
        @JsonProperty("correlationId") String correlationId
    ) {
        super(eventId, occurredAt, eventType, version);
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.documentId = documentId;
        this.documentUrl = documentUrl;
        this.fileSize = fileSize;
        this.xmlEmbedded = xmlEmbedded;
        this.correlationId = correlationId;
    }
}
