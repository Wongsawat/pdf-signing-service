package com.wpanther.pdfsigning.domain.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Event published by pdf-signing-service when a PDF is digitally signed.
 * Consumed by document-storage-service and notification-service.
 *
 * Topic: pdf.signed
 */
@Data
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PdfSignedEvent extends IntegrationEvent {

    /**
     * Invoice identifier (UUID)
     */
    private String invoiceId;

    /**
     * Human-readable invoice number
     */
    private String invoiceNumber;

    /**
     * Document type (INVOICE, TAX_INVOICE, etc.)
     */
    private String documentType;

    /**
     * Signed document identifier
     */
    private String signedDocumentId;

    /**
     * URL to access the signed PDF
     */
    private String signedPdfUrl;

    /**
     * File size of the signed PDF in bytes
     */
    private Long signedPdfSize;

    /**
     * CSC API transaction ID
     */
    private String transactionId;

    /**
     * PEM-encoded signing certificate
     */
    private String certificate;

    /**
     * Signature level (e.g., PAdES-BASELINE-T)
     */
    private String signatureLevel;

    /**
     * Timestamp from the signing service
     */
    private LocalDateTime signatureTimestamp;
}
