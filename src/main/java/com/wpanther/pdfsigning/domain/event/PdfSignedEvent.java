package com.wpanther.pdfsigning.domain.event;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.wpanther.saga.domain.model.IntegrationEvent;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * Event published by pdf-signing-service when a PDF is digitally signed.
 * Consumed by document-storage-service and notification-service.
 *
 * Topic: pdf.signed
 */
@Getter
public class PdfSignedEvent extends IntegrationEvent {

    private static final String EVENT_TYPE = "pdf.signed";

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

    @JsonProperty("transactionId")
    private final String transactionId;

    @JsonProperty("certificate")
    private final String certificate;

    @JsonProperty("signatureLevel")
    private final String signatureLevel;

    @JsonProperty("signatureTimestamp")
    private final Instant signatureTimestamp;

    @JsonProperty("correlationId")
    private final String correlationId;

    // Constructor for creating new events
    public PdfSignedEvent(String invoiceId, String invoiceNumber, String documentType,
                          String signedDocumentId, String signedPdfUrl, Long signedPdfSize,
                          String transactionId, String certificate, String signatureLevel,
                          Instant signatureTimestamp, String correlationId) {
        super();
        this.invoiceId = invoiceId;
        this.invoiceNumber = invoiceNumber;
        this.documentType = documentType;
        this.signedDocumentId = signedDocumentId;
        this.signedPdfUrl = signedPdfUrl;
        this.signedPdfSize = signedPdfSize;
        this.transactionId = transactionId;
        this.certificate = certificate;
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
    public PdfSignedEvent(
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
        @JsonProperty("transactionId") String transactionId,
        @JsonProperty("certificate") String certificate,
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
        this.transactionId = transactionId;
        this.certificate = certificate;
        this.signatureLevel = signatureLevel;
        this.signatureTimestamp = signatureTimestamp;
        this.correlationId = correlationId;
    }
}
