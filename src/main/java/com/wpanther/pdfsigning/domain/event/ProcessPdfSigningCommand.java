package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Saga command from orchestrator to sign a PDF document.
 * Consumed from: saga.command.pdf-signing
 *
 * Contains all information needed to download and sign the PDF.
 * Extends IntegrationEvent (not SagaCommand) - saga fields are included as regular fields.
 */
@Getter
public class ProcessPdfSigningCommand extends IntegrationEvent {

    private static final long serialVersionUID = 1L;

    @JsonProperty("sagaId")
    private final String sagaId;

    @JsonProperty("sagaStep")
    private final String sagaStep;

    @JsonProperty("correlationId")
    private final String correlationId;

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

    /**
     * Constructor for deserialization from Kafka.
     * Used by Apache Camel JSON unmarshalling.
     */
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
        super(eventId, occurredAt, eventType, version);
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.pdfUrl = pdfUrl;
        this.pdfSize = pdfSize;
        this.xmlEmbedded = xmlEmbedded;
    }

    /**
     * Convenience constructor for testing.
     */
    public ProcessPdfSigningCommand(String sagaId, String sagaStep, String correlationId,
                                    String documentId, String invoiceNumber, String documentType,
                                    String pdfUrl, Long pdfSize, Boolean xmlEmbedded) {
        super();
        this.sagaId = sagaId;
        this.sagaStep = sagaStep;
        this.correlationId = correlationId;
        this.documentId = documentId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.pdfUrl = pdfUrl;
        this.pdfSize = pdfSize;
        this.xmlEmbedded = xmlEmbedded;
    }
}
